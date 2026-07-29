package io.github.arglax.wuwalab.ui.matrixwidget

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.arglax.wuwalab.data.MatrixWidgetPrefs
import io.github.arglax.wuwalab.data.MatrixWidgetSettings
import io.github.arglax.wuwalab.data.MatrixWidgetTapTarget
import io.github.arglax.wuwalab.ui.components.GlassCard
import io.github.arglax.wuwalab.ui.components.HelpButton
import io.github.arglax.wuwalab.ui.theme.CyanGlow
import io.github.arglax.wuwalab.ui.theme.EmeraldGlow
import io.github.arglax.wuwalab.ui.theme.GlassBorderSoft
import io.github.arglax.wuwalab.ui.theme.TextMuted
import io.github.arglax.wuwalab.ui.theme.TextPrimary
import io.github.arglax.wuwalab.ui.theme.TextSecondary
import io.github.arglax.wuwalab.ui.theme.VioletGlow
import io.github.arglax.wuwalab.util.rememberTapFeedback
import io.github.arglax.wuwalab.widget.MatrixWidget
import kotlinx.coroutines.launch

/**
 * Settings for the home-screen Eisenhower Matrix widget.
 *
 * This lives under Planning (between To-Do and Pull Planner) rather than with
 * the other widget tooling, because everything it controls is Matrix content -
 * how many tasks a quadrant shows, whether finished ones stay visible, and
 * where a tap lands. Every change writes through immediately and pushes a
 * redraw, so the widget on the home screen matches what's on screen here.
 */
@Composable
fun MatrixWidgetSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tapFeedback = rememberTapFeedback()
    val prefs = remember { MatrixWidgetPrefs(context) }

    var settings by remember { mutableStateOf(MatrixWidgetSettings()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settings = prefs.getOnce()
        loaded = true
    }

    fun apply(next: MatrixWidgetSettings) {
        settings = next
        scope.launch {
            prefs.save(next)
            MatrixWidget.updateAll(context)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Matrix Widget",
                    fontWeight = FontWeight.Bold,
                    fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                HelpButton(
                    title = "Matrix Widget Help",
                    body = "These settings control the Eisenhower Matrix widget on your home " +
                        "screen - not the Matrix page inside the app.\n\n" +
                        "Tasks per quadrant caps how many titles each of the four boxes lists " +
                        "before it collapses into a \"+N more\" line; a smaller number reads better " +
                        "on a small widget.\n\n" +
                        "Tap action decides where a tap lands. Leave it on Ask me and the widget " +
                        "shows a chooser with Open Matrix and Open To-Do instead of jumping " +
                        "somewhere you didn't pick."
                )
            }
        }

        if (!loaded) {
            item { Text("Loading your widget settings...", color = TextMuted) }
            return@LazyColumn
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), accent = CyanGlow) {
                Text("Layout", fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(10.dp))

                ToggleRow(
                    label = "Show the header",
                    description = "The title line above the four quadrants.",
                    checked = settings.showHeader,
                    onCheckedChange = { tapFeedback(); apply(settings.copy(showHeader = it)) }
                )

                if (settings.showHeader) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = settings.headerTitle,
                        onValueChange = { apply(settings.copy(headerTitle = it.take(28))) },
                        label = { Text("Header text") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text("Tasks per quadrant", color = TextSecondary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..6).forEach { count ->
                        ChoicePill(
                            label = count.toString(),
                            selected = settings.itemsPerQuadrant == count,
                            onClick = { tapFeedback(); apply(settings.copy(itemsPerQuadrant = count)) }
                        )
                    }
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), accent = EmeraldGlow) {
                Text("Content", fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(10.dp))
                ToggleRow(
                    label = "Include completed tasks",
                    description = "Off by default - the widget is a view of what's still open.",
                    checked = settings.showCompleted,
                    onCheckedChange = { tapFeedback(); apply(settings.copy(showCompleted = it)) }
                )
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), accent = VioletGlow) {
                Text("Tap action", fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "What happens when you tap the widget on your home screen.",
                    color = TextMuted,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                )
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MatrixWidgetTapTarget.entries.forEach { target ->
                        ChoiceRow(
                            label = target.label,
                            description = target.description,
                            selected = settings.tapTarget == target,
                            onClick = { tapFeedback(); apply(settings.copy(tapTarget = target)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelLarge.fontSize)
            Text(description, color = TextMuted, fontSize = MaterialTheme.typography.labelSmall.fontSize)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = EmeraldGlow)
        )
    }
}

@Composable
private fun ChoicePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) CyanGlow.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.05f))
            .border(1.dp, if (selected) CyanGlow else GlassBorderSoft, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (selected) TextPrimary else TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = MaterialTheme.typography.labelMedium.fontSize
        )
    }
}

@Composable
private fun ChoiceRow(label: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) VioletGlow.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f))
            .border(1.dp, if (selected) VioletGlow.copy(alpha = 0.7f) else GlassBorderSoft, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(if (selected) VioletGlow else Color.White.copy(alpha = 0.18f))
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelLarge.fontSize)
            Text(description, color = TextMuted, fontSize = MaterialTheme.typography.labelSmall.fontSize)
        }
    }
}
