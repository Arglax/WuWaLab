package io.github.arglax.wuwalab.ui.planners

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.arglax.wuwalab.ui.components.GlassCard
import io.github.arglax.wuwalab.ui.theme.AmberGlow
import io.github.arglax.wuwalab.ui.theme.TextMuted
import io.github.arglax.wuwalab.ui.theme.TextPrimary
import io.github.arglax.wuwalab.ui.theme.TextSecondary
import io.github.arglax.wuwalab.ui.theme.VioletGlow

/**
 * The "Planners" tab - currently a scaffold with two empty, independently
 * collapsible/expandable sections: Pull Planner and To Do Planner. Neither
 * has real content wired up yet; this is intentionally just the shell so
 * the tab exists and has somewhere obvious to grow into next session.
 */
@Composable
fun PlannersScreen(modifier: Modifier = Modifier) {
    var pullExpanded by remember { mutableStateOf(true) }
    var todoExpanded by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Planners",
            fontWeight = FontWeight.Bold,
            fontSize = MaterialTheme.typography.headlineSmall.fontSize,
            color = TextPrimary
        )

        PlannerSection(
            title = "Pull Planner",
            accent = AmberGlow,
            expanded = pullExpanded,
            onToggle = { pullExpanded = !pullExpanded }
        )

        PlannerSection(
            title = "To Do Planner",
            accent = VioletGlow,
            expanded = todoExpanded,
            onToggle = { todoExpanded = !todoExpanded }
        )
    }
}

@Composable
private fun PlannerSection(
    title: String,
    accent: androidx.compose.ui.graphics.Color,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "plannerChevron")

    GlassCard(modifier = Modifier.fillMaxWidth().animateContentSize(), accent = accent) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
        ) {
            Text(title, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = TextSecondary,
                modifier = Modifier.rotate(chevronRotation)
            )
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Nothing planned yet.",
                color = TextMuted,
                fontSize = MaterialTheme.typography.labelSmall.fontSize
            )
        }
    }
}