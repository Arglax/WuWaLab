package io.github.arglax.wuwalab.ui.matrix

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.arglax.wuwalab.data.EisenhowerQuadrant
import io.github.arglax.wuwalab.data.TodoItem
import io.github.arglax.wuwalab.data.TodoRepository
import io.github.arglax.wuwalab.ui.components.HelpButton
import io.github.arglax.wuwalab.ui.theme.AmberGlow
import io.github.arglax.wuwalab.ui.theme.CoralGlow
import io.github.arglax.wuwalab.ui.theme.CyanGlow
import io.github.arglax.wuwalab.ui.theme.EmeraldGlow
import io.github.arglax.wuwalab.ui.theme.GlassBorderSoft
import io.github.arglax.wuwalab.ui.theme.GlassSurface
import io.github.arglax.wuwalab.ui.theme.TextMuted
import io.github.arglax.wuwalab.ui.theme.TextPrimary
import io.github.arglax.wuwalab.ui.theme.TextSecondary
import io.github.arglax.wuwalab.ui.theme.VioletGlow
import kotlinx.coroutines.launch

private fun quadrantAccent(q: EisenhowerQuadrant): Color = when (q) {
    EisenhowerQuadrant.DO_FIRST -> CoralGlow
    EisenhowerQuadrant.SCHEDULE -> CyanGlow
    EisenhowerQuadrant.DELEGATE -> AmberGlow
    EisenhowerQuadrant.ELIMINATE -> TextMuted
    EisenhowerQuadrant.UNASSIGNED -> VioletGlow
}

/**
 * Eisenhower Matrix over the SAME task list the To-Do planner uses
 * ([TodoRepository.itemsFlow]), so both pages always agree.
 *
 * Interaction model: simple tap-to-assign. Tap any task chip to select it
 * (it lifts with an accent border), then tap a quadrant to drop it there.
 * Tapping the selected chip again deselects. Tasks not yet placed wait in
 * the "Unassigned" tray under the grid.
 */
@Composable
fun EisenhowerMatrixScreen(
    todoRepo: TodoRepository,
    achievementsRepo: io.github.arglax.wuwalab.data.AchievementsRepository? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val items by todoRepo.itemsFlow.collectAsState(initial = emptyList())
    var selectedTaskId by remember { mutableStateOf<String?>(null) }

    val byQuadrant = remember(items) { items.groupBy { it.quadrant } }

    fun assign(quadrant: EisenhowerQuadrant) {
        val id = selectedTaskId ?: return
        scope.launch {
            todoRepo.setQuadrant(id, quadrant)
            // Only a real placement onto the matrix counts as "organizing" it -
            // sending something back to the unassigned tray doesn't.
            if (quadrant != EisenhowerQuadrant.UNASSIGNED) achievementsRepo?.recordMatrixAssignment()
        }
        selectedTaskId = null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Eisenhower Matrix",
                fontWeight = FontWeight.Bold,
                fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            HelpButton(
                title = "Eisenhower Matrix Help",
                body = "This grid shares the same task list as the To-Do planner - tasks you " +
                    "add there show up here too, and vice versa.\n\n" +
                    "1. Tap a task chip to pick it up (it highlights).\n" +
                    "2. Tap a quadrant to drop it there: Do First, Schedule, Delegate, or " +
                    "Eliminate.\n" +
                    "3. Tap the \"Unassigned\" tray to send a picked-up task back to unsorted.\n" +
                    "4. Tap a selected chip again to deselect it without moving it."
            )
        }
        Text(
            if (selectedTaskId == null) "Tap a task to pick it up, then tap a quadrant to place it."
            else "Now tap the quadrant this task belongs in.",
            color = if (selectedTaskId == null) TextSecondary else AmberGlow,
            fontSize = MaterialTheme.typography.labelMedium.fontSize
        )

        // Second, explicit way to get the Matrix widget onto the home
        // screen - besides the usual OS long-press widget picker (which not
        // everyone thinks to try), this fires the system's "pin widget"
        // prompt directly from inside the app.
        MatrixWidgetPinRow()

        // --- The 2x2 grid ---
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuadrantCell(
                quadrant = EisenhowerQuadrant.DO_FIRST,
                items = byQuadrant[EisenhowerQuadrant.DO_FIRST].orEmpty(),
                selectedTaskId = selectedTaskId,
                pickingActive = selectedTaskId != null,
                onTapQuadrant = { assign(EisenhowerQuadrant.DO_FIRST) },
                onTapTask = { selectedTaskId = if (selectedTaskId == it) null else it },
                modifier = Modifier.weight(1f)
            )
            QuadrantCell(
                quadrant = EisenhowerQuadrant.SCHEDULE,
                items = byQuadrant[EisenhowerQuadrant.SCHEDULE].orEmpty(),
                selectedTaskId = selectedTaskId,
                pickingActive = selectedTaskId != null,
                onTapQuadrant = { assign(EisenhowerQuadrant.SCHEDULE) },
                onTapTask = { selectedTaskId = if (selectedTaskId == it) null else it },
                modifier = Modifier.weight(1f)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuadrantCell(
                quadrant = EisenhowerQuadrant.DELEGATE,
                items = byQuadrant[EisenhowerQuadrant.DELEGATE].orEmpty(),
                selectedTaskId = selectedTaskId,
                pickingActive = selectedTaskId != null,
                onTapQuadrant = { assign(EisenhowerQuadrant.DELEGATE) },
                onTapTask = { selectedTaskId = if (selectedTaskId == it) null else it },
                modifier = Modifier.weight(1f)
            )
            QuadrantCell(
                quadrant = EisenhowerQuadrant.ELIMINATE,
                items = byQuadrant[EisenhowerQuadrant.ELIMINATE].orEmpty(),
                selectedTaskId = selectedTaskId,
                pickingActive = selectedTaskId != null,
                onTapQuadrant = { assign(EisenhowerQuadrant.ELIMINATE) },
                onTapTask = { selectedTaskId = if (selectedTaskId == it) null else it },
                modifier = Modifier.weight(1f)
            )
        }

        // --- Unassigned tray ---
        val unassigned = byQuadrant[EisenhowerQuadrant.UNASSIGNED].orEmpty()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(GlassSurface)
                .border(1.dp, GlassBorderSoft, RoundedCornerShape(18.dp))
                .clickable(enabled = selectedTaskId != null) { assign(EisenhowerQuadrant.UNASSIGNED) }
                .padding(14.dp)
                .animateContentSize()
        ) {
            Text("Unassigned", fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(
                "Tasks from the To-Do planner land here until you place them.",
                color = TextMuted,
                fontSize = MaterialTheme.typography.labelSmall.fontSize
            )
            Spacer(Modifier.height(8.dp))
            if (unassigned.isEmpty()) {
                Text("Nothing waiting - nice.", color = TextSecondary, fontSize = MaterialTheme.typography.labelMedium.fontSize)
            } else {
                unassigned.forEach { item ->
                    TaskChip(
                        item = item,
                        selected = item.id == selectedTaskId,
                        accent = quadrantAccent(EisenhowerQuadrant.UNASSIGNED),
                        onTap = { selectedTaskId = if (selectedTaskId == item.id) null else item.id }
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun QuadrantCell(
    quadrant: EisenhowerQuadrant,
    items: List<TodoItem>,
    selectedTaskId: String?,
    pickingActive: Boolean,
    onTapQuadrant: () -> Unit,
    onTapTask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = quadrantAccent(quadrant)
    Column(
        modifier = modifier
            .heightIn(min = 150.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(GlassSurface)
            .border(
                width = if (pickingActive) 2.dp else 1.dp,
                color = if (pickingActive) accent.copy(alpha = 0.8f) else accent.copy(alpha = 0.35f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onTapQuadrant() }
            .padding(12.dp)
            .animateContentSize()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(6.dp))
            Text(quadrant.title, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = MaterialTheme.typography.labelLarge.fontSize)
        }
        Text(quadrant.subtitle, color = TextMuted, fontSize = MaterialTheme.typography.labelSmall.fontSize)
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) {
            Text("Empty", color = TextMuted, fontSize = MaterialTheme.typography.labelSmall.fontSize)
        } else {
            items.forEach { item ->
                TaskChip(
                    item = item,
                    selected = item.id == selectedTaskId,
                    accent = accent,
                    onTap = { onTapTask(item.id) }
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun TaskChip(
    item: TodoItem,
    selected: Boolean,
    accent: Color,
    onTap: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.06f))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) accent else GlassBorderSoft,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onTap() }
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(
            item.title,
            color = if (item.done) TextMuted else TextPrimary,
            textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
            fontSize = MaterialTheme.typography.labelMedium.fontSize,
            maxLines = 2
        )
    }
}
/**
 * Small dismissible row offering to pin the read-only Matrix home-screen
 * widget directly, via [android.appwidget.AppWidgetManager.requestPinAppWidget].
 * This is IN ADDITION to the widget already being discoverable the normal
 * way (long-press home screen -> Widgets -> WuWaLab -> "Eisenhower Matrix") -
 * some users never find that picker, so this gives a second, explicit path
 * without removing the first.
 */
@Composable
private fun MatrixWidgetPinRow() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val supported = remember {
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            android.appwidget.AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported
    }
    if (!supported) return // e.g. some launchers/OEM skins don't support the pin API - the long-press picker still works fine.

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(VioletGlow.copy(alpha = 0.10f))
            .border(1.dp, VioletGlow.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            "Want this Matrix on your home screen?",
            color = TextSecondary,
            fontSize = MaterialTheme.typography.labelMedium.fontSize,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Add Widget",
            color = VioletGlow,
            fontWeight = FontWeight.Bold,
            fontSize = MaterialTheme.typography.labelMedium.fontSize,
            modifier = Modifier.clickable {
                val provider = android.content.ComponentName(context, io.github.arglax.wuwalab.widget.MatrixWidgetReceiver::class.java)
                runCatching {
                    android.appwidget.AppWidgetManager.getInstance(context)
                        .requestPinAppWidget(provider, null, null)
                }
            }
        )
    }
}
