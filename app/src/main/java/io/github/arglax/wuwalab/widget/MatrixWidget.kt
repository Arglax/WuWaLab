package io.github.arglax.wuwalab.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.arglax.wuwalab.data.EisenhowerQuadrant
import io.github.arglax.wuwalab.data.MatrixWidgetPrefs
import io.github.arglax.wuwalab.data.MatrixWidgetSettings
import io.github.arglax.wuwalab.data.MatrixWidgetTapTarget
import io.github.arglax.wuwalab.data.TodoItem
import io.github.arglax.wuwalab.data.TodoRepository
import kotlinx.coroutines.flow.first
import androidx.glance.unit.ColorProvider

private const val TAG = "MatrixWidget"

private fun fixedColor(color: Color) = ColorProvider(day = color, night = color)

private val JadeBackground = fixedColor(Color(0xFF10241C))
private val CardSurface = fixedColor(Color(0x1AFFFFFF))
private val WhiteText = fixedColor(Color.White)
private val MutedText = fixedColor(Color(0xFF9FD8B8))

// One accent per quadrant, matching EisenhowerQuadrant's DO_FIRST/SCHEDULE/
// DELEGATE/ELIMINATE order used throughout the in-app matrix UI.
private val DoFirstAccent = fixedColor(Color(0xFFE05C5C))
private val ScheduleAccent = fixedColor(Color(0xFFD4AF37))
private val DelegateAccent = fixedColor(Color(0xFF6FB7E0))
private val EliminateAccent = fixedColor(Color(0xFF8A8F98))

private val HEADER_HEIGHT = 22.dp
private val GRID_GAP = 6.dp
private val OUTER_PADDING = 8.dp

/** One quadrant's worth of data, snapshotted per [provideGlance]/refresh. */
private data class QuadrantData(
    val quadrant: EisenhowerQuadrant,
    val accent: ColorProvider,
    val items: List<TodoItem>
)

/**
 * Second, separate home-screen widget from [WuwaWidget] - a read-only view
 * of the user's current Eisenhower Matrix (the same quadrant data owned by
 * [TodoRepository] and edited in the in-app To-Do screen). Tapping opens the
 * app straight onto the To-Do/Matrix page rather than a full chooser, since
 * there's really only one sensible destination for this widget.
 */
class MatrixWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val todoRepo = TodoRepository(context)
        val settings = MatrixWidgetPrefs(context).settingsFlow.first()
        val all = todoRepo.itemsFlow.first()
        val items = if (settings.showCompleted) all else all.filterNot { it.done }

        val quadrants = listOf(
            QuadrantData(EisenhowerQuadrant.DO_FIRST, DoFirstAccent, items.filter { it.quadrant == EisenhowerQuadrant.DO_FIRST }),
            QuadrantData(EisenhowerQuadrant.SCHEDULE, ScheduleAccent, items.filter { it.quadrant == EisenhowerQuadrant.SCHEDULE }),
            QuadrantData(EisenhowerQuadrant.DELEGATE, DelegateAccent, items.filter { it.quadrant == EisenhowerQuadrant.DELEGATE }),
            QuadrantData(EisenhowerQuadrant.ELIMINATE, EliminateAccent, items.filter { it.quadrant == EisenhowerQuadrant.ELIMINATE })
        )

        provideContent {
            MatrixWidgetContent(quadrants, settings)
        }
    }

    companion object {
        /** Same pattern as [WuwaWidget.updateAll] - see its kdoc for why. */
        suspend fun updateAll(context: Context) {
            val appContext = context.applicationContext
            try {
                MatrixWidget().updateAll(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "Widget updateAll failed", e)
            }
        }
    }
}

@Composable
private fun MatrixWidgetContent(quadrants: List<QuadrantData>, settings: MatrixWidgetSettings) {
    val context = LocalContext.current
    // A widget tap must never dump you somewhere you didn't pick. Unless the
    // user has explicitly pinned a destination in Matrix Widget settings, this
    // opens the chooser - and that chooser only ever offers Open Matrix and
    // Open To-Do, since those are the only two places this widget maps to.
    val tapAction = when (settings.tapTarget) {
        MatrixWidgetTapTarget.MATRIX -> actionStartActivity(
            Intent(context, io.github.arglax.wuwalab.MainActivity::class.java).apply {
                putExtra(EXTRA_NAV_PAGE, NAV_PAGE_MATRIX)
            }
        )
        MatrixWidgetTapTarget.TODO -> actionStartActivity(
            Intent(context, io.github.arglax.wuwalab.MainActivity::class.java).apply {
                putExtra(EXTRA_NAV_PAGE, NAV_PAGE_TODO)
            }
        )
        MatrixWidgetTapTarget.ASK -> actionStartActivity(
            Intent(context, WidgetActionActivity::class.java).apply {
                putExtra(EXTRA_CHOOSER_MODE, CHOOSER_MODE_MATRIX)
            }
        )
    }

    // No weight-based flex sizing here - explicit dp math off LocalSize
    // instead (same pattern WuwaWidget.kt already uses for its landscape
    // scrim split), so this doesn't depend on any particular Glance
    // "weight modifier" API being present.
    val size = LocalSize.current
    val contentWidth = size.width - OUTER_PADDING * 2
    val contentHeight = size.height - OUTER_PADDING * 2
    val headerBlock = if (settings.showHeader) HEADER_HEIGHT + GRID_GAP else 0.dp
    val gridHeight = (contentHeight - headerBlock).coerceAtLeast(0.dp)
    val colWidth = ((contentWidth - GRID_GAP) / 2).coerceAtLeast(0.dp)
    val cardHeight = ((gridHeight - GRID_GAP) / 2).coerceAtLeast(0.dp)

    Column(
        modifier = GlanceModifier
            .width(size.width)
            .height(size.height)
            .background(JadeBackground)
            .clickable(tapAction)
            .padding(OUTER_PADDING)
    ) {
        if (settings.showHeader) {
            Text(
                text = settings.headerTitle,
                style = TextStyle(color = WhiteText, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.height(HEADER_HEIGHT)
            )
            Spacer(modifier = GlanceModifier.height(GRID_GAP))
        }
        Row(modifier = GlanceModifier.width(contentWidth).height(gridHeight)) {
            Column(modifier = GlanceModifier.width(colWidth).height(gridHeight)) {
                QuadrantCard(quadrants[0], settings.itemsPerQuadrant, modifier = GlanceModifier.width(colWidth).height(cardHeight))
                Spacer(modifier = GlanceModifier.height(GRID_GAP))
                QuadrantCard(quadrants[2], settings.itemsPerQuadrant, modifier = GlanceModifier.width(colWidth).height(cardHeight))
            }
            Spacer(modifier = GlanceModifier.width(GRID_GAP))
            Column(modifier = GlanceModifier.width(colWidth).height(gridHeight)) {
                QuadrantCard(quadrants[1], settings.itemsPerQuadrant, modifier = GlanceModifier.width(colWidth).height(cardHeight))
                Spacer(modifier = GlanceModifier.height(GRID_GAP))
                QuadrantCard(quadrants[3], settings.itemsPerQuadrant, modifier = GlanceModifier.width(colWidth).height(cardHeight))
            }
        }
    }
}

@Composable
private fun QuadrantCard(data: QuadrantData, maxItems: Int, modifier: GlanceModifier) {
    Column(
        modifier = modifier
            .background(CardSurface)
            .padding(6.dp)
    ) {
        Row(verticalAlignment = Alignment.Vertical.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Box(modifier = GlanceModifier.width(6.dp).height(6.dp).background(data.accent)) {}
            Spacer(modifier = GlanceModifier.width(4.dp))
            // Title + count combined into one line (rather than pushing the
            // count to the far end with a weighted spacer) so this never
            // depends on a flex/weight modifier being available.
            Text(
                text = "${data.quadrant.title} \u00b7 ${data.items.size}",
                style = TextStyle(color = WhiteText, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }
        Spacer(modifier = GlanceModifier.height(3.dp))
        if (data.items.isEmpty()) {
            Text(
                text = "Nothing here",
                style = TextStyle(color = MutedText, fontSize = 9.sp)
            )
        } else {
            // Read-only preview of the first few titles - v1 keeps this
            // widget non-interactive beyond the tap-to-open-app action.
            data.items.take(maxItems).forEach { item ->
                Text(
                    text = "\u2022 ${item.title}",
                    style = TextStyle(color = MutedText, fontSize = 9.sp),
                    maxLines = 1
                )
            }
            if (data.items.size > maxItems) {
                Text(
                    text = "+${data.items.size - maxItems} more",
                    style = TextStyle(color = MutedText, fontSize = 9.sp)
                )
            }
        }
    }
}

class MatrixWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MatrixWidget()
}
