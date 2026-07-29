package io.github.arglax.wuwalab.widget

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.arglax.wuwalab.MainActivity
import io.github.arglax.wuwalab.ui.theme.AmberGlow
import io.github.arglax.wuwalab.ui.theme.CoralGlow
import io.github.arglax.wuwalab.ui.theme.CyanGlow
import io.github.arglax.wuwalab.ui.theme.EmeraldGlow
import io.github.arglax.wuwalab.ui.theme.TextMuted
import io.github.arglax.wuwalab.ui.theme.TextPrimary
import io.github.arglax.wuwalab.ui.theme.TextSecondary
import io.github.arglax.wuwalab.ui.theme.VioletGlow
import io.github.arglax.wuwalab.ui.theme.WuWaLabTheme

/**
 * Intent extra MainActivity reads to open straight onto a specific page.
 * The value is a [io.github.arglax.wuwalab.MainActivity] page key - see
 * the RootPage enum names.
 */
const val EXTRA_NAV_PAGE = "wuwalab_nav_page"

const val NAV_PAGE_DASHBOARD = "DASHBOARD"
const val NAV_PAGE_PLANNER = "PLANNER"
const val NAV_PAGE_TODO = "TODO_LIST"
const val NAV_PAGE_MATRIX = "MATRIX"

/**
 * Which set of destinations the chooser offers. A widget should only ever
 * offer places it actually maps to, so the Matrix widget gets its own short
 * list rather than the full app menu.
 */
const val EXTRA_CHOOSER_MODE = "wuwalab_chooser_mode"
const val CHOOSER_MODE_MAIN = "main"
const val CHOOSER_MODE_MATRIX = "matrix"

/**
 * The little chooser that appears when a home-screen widget is tapped.
 *
 * A widget can't host a menu of its own, so the tap opens this transparent,
 * dialog-only activity instead. Nothing is launched until you pick something -
 * the tap itself never opens the app. Pick a destination and it hands off to
 * the app (or to your phone's clock app for the alarm option) and closes
 * itself; tapping outside just dismisses, exactly like a popup should.
 */
class WidgetActionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent?.getStringExtra(EXTRA_CHOOSER_MODE) ?: CHOOSER_MODE_MAIN
        setContent {
            WuWaLabTheme {
                WidgetActionDialog(
                    mode = mode,
                    onOpenPage = { page -> openApp(page); finish() },
                    onSetAlarm = { openAlarms(); finish() },
                    onDismiss = { finish() }
                )
            }
        }
    }

    private fun openApp(page: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_NAV_PAGE, page)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    /**
     * Hands off to whatever clock app the phone uses. Falls back from "create a
     * new alarm" to "show my alarms" if the first isn't handled, and says so
     * plainly if neither is - rather than failing silently on a tap.
     */
    private fun openAlarms() {
        val create = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(create)
            return
        } catch (_: ActivityNotFoundException) {
            // fall through
        }
        val show = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(show)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No clock app found on this device.", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        fun intent(context: Context, mode: String = CHOOSER_MODE_MAIN): Intent =
            Intent(context, WidgetActionActivity::class.java).apply {
                putExtra(EXTRA_CHOOSER_MODE, mode)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
    }
}

@Composable
private fun WidgetActionDialog(
    mode: String,
    onOpenPage: (String) -> Unit,
    onSetAlarm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isMatrix = mode == CHOOSER_MODE_MATRIX
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isMatrix) "Eisenhower Matrix" else "WuWaLab", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Where would you like to go?",
                    color = TextSecondary,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize
                )
                Spacer(Modifier.height(14.dp))
                if (isMatrix) {
                    // The Matrix widget only maps to two places, so those are
                    // the only two things it offers.
                    ActionRow(
                        icon = Icons.Filled.GridView,
                        tint = CoralGlow,
                        title = "Open Matrix",
                        subtitle = "Your four quadrants, full size",
                        onClick = { onOpenPage(NAV_PAGE_MATRIX) }
                    )
                    ActionRow(
                        icon = Icons.Filled.CheckCircle,
                        tint = EmeraldGlow,
                        title = "Open To-Do",
                        subtitle = "The same tasks as a plain list",
                        onClick = { onOpenPage(NAV_PAGE_TODO) }
                    )
                } else {
                    ActionRow(
                        icon = Icons.Filled.Dashboard,
                        tint = CyanGlow,
                        title = "Open Dashboard",
                        subtitle = "Waveplates, crystals and daily sign-in",
                        onClick = { onOpenPage(NAV_PAGE_DASHBOARD) }
                    )
                    ActionRow(
                        icon = Icons.Filled.Casino,
                        tint = AmberGlow,
                        title = "Open Pull Planner",
                        subtitle = "Convene odds and your Astrite budget",
                        onClick = { onOpenPage(NAV_PAGE_PLANNER) }
                    )
                    ActionRow(
                        icon = Icons.Filled.GridView,
                        tint = CoralGlow,
                        title = "Open Matrix",
                        subtitle = "Your four Eisenhower quadrants",
                        onClick = { onOpenPage(NAV_PAGE_MATRIX) }
                    )
                    ActionRow(
                        icon = Icons.Filled.CheckCircle,
                        tint = EmeraldGlow,
                        title = "Open To-Do",
                        subtitle = "Your tasks and reminders",
                        onClick = { onOpenPage(NAV_PAGE_TODO) }
                    )
                    ActionRow(
                        icon = Icons.Filled.Alarm,
                        tint = VioletGlow,
                        title = "Set an alarm",
                        subtitle = "Opens your phone's clock app",
                        onClick = onSetAlarm
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodyMedium.fontSize)
            Text(subtitle, color = TextMuted, fontSize = MaterialTheme.typography.labelSmall.fontSize)
        }
    }
}
