package io.github.arglax.wuwalab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.sp
import io.github.arglax.wuwalab.data.Achievement
import io.github.arglax.wuwalab.data.AchievementUiState
import io.github.arglax.wuwalab.data.TitleRarity
import io.github.arglax.wuwalab.ui.theme.AmberGlow
import io.github.arglax.wuwalab.ui.theme.CoralGlow
import io.github.arglax.wuwalab.ui.theme.EmeraldGlow
import io.github.arglax.wuwalab.ui.theme.TextMuted
import io.github.arglax.wuwalab.ui.theme.TextPrimary
import io.github.arglax.wuwalab.ui.theme.TextSecondary
import io.github.arglax.wuwalab.ui.theme.VioletGlow

/**
 * One badge glyph + accent color per achievement id - drawn as vector icons
 * inside a colored circle rather than shipped PNGs, so every achievement
 * (including any added later to [io.github.arglax.wuwalab.data.ACHIEVEMENT_CATALOG])
 * gets a real, distinct badge with zero extra art assets to produce or ship.
 */
private data class AchievementBadge(val icon: ImageVector, val accent: Color)

private val BADGES: Map<String, AchievementBadge> = mapOf(
    "consistent_farmer" to AchievementBadge(Icons.Filled.CalendarMonth, AmberGlow),
    "pull_historian" to AchievementBadge(Icons.Filled.History, VioletGlow),
    "detailed_puller" to AchievementBadge(Icons.Filled.EditNote, VioletGlow),
    "task_finisher" to AchievementBadge(Icons.Filled.TaskAlt, EmeraldGlow),
    "thorough_planner" to AchievementBadge(Icons.Filled.FactCheck, EmeraldGlow),
    "matrix_custodian" to AchievementBadge(Icons.Filled.GridView, CoralGlow),
    "supporter_title_bonus" to AchievementBadge(Icons.Filled.Favorite, TitleRarity.LEGENDARY.color)
)
private val DEFAULT_BADGE = AchievementBadge(Icons.Filled.TaskAlt, TextMuted)

/**
 * Full-window Achievements list - a "new window" per achievement rather than
 * the compact LazyRow preview still shown inline in [ProfileStatsDialog].
 * Each row is a proper task card: badge, title, the exact requirement,
 * a live progress bar (not just a fraction of text), and what it pays out.
 */
@Composable
fun AchievementsDialog(
    achievements: List<AchievementUiState>,
    supporterUnlocked: Boolean,
    onDismiss: () -> Unit
) {
    val unlockedCount = achievements.count { it.unlocked } + if (supporterUnlocked) 1 else 0
    val totalCount = achievements.size + if (supporterUnlocked) 1 else 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Achievements", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text("$unlockedCount / $totalCount unlocked", color = TextSecondary, fontSize = 12.sp)
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 480.dp)
            ) {
                if (supporterUnlocked) {
                    item {
                        AchievementTaskCard(
                            id = "supporter_title_bonus",
                            title = "WuWaLab Supporter",
                            description = "Redeem the YOUREALLYDIDSUPPORTMEBROOMFG code.",
                            rewardText = "+200 Argstrites \u00b7 +20% Bonus",
                            progress = 1,
                            goal = 1,
                            unlocked = true
                        )
                    }
                }
                items(achievements) { state ->
                    AchievementTaskCard(
                        id = state.achievement.id,
                        title = state.achievement.title,
                        description = state.achievement.description,
                        rewardText = "+${state.achievement.rewardArgstrites} Argstrites \u00b7 +${formatBonusPercent(state.achievement.bonusPercent)}% Bonus",
                        progress = state.progress,
                        goal = state.achievement.goal,
                        unlocked = state.unlocked
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun AchievementTaskCard(
    id: String,
    title: String,
    description: String,
    rewardText: String,
    progress: Int,
    goal: Int,
    unlocked: Boolean
) {
    val badge = BADGES[id] ?: DEFAULT_BADGE
    val accent = if (unlocked) badge.accent else TextMuted
    val clampedProgress = progress.coerceIn(0, goal)
    val fraction = if (goal > 0) (clampedProgress.toFloat() / goal.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = if (unlocked) 0.12f else 0.05f))
            .border(1.dp, accent.copy(alpha = if (unlocked) 0.5f else 0.2f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            // Badge icon - a filled colored circle when unlocked, a dim
            // locked padlock silhouette when not, so it reads at a glance
            // which ones are still tasks-to-do vs. already earned.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = if (unlocked) 0.9f else 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (unlocked) badge.icon else Icons.Filled.Lock,
                    contentDescription = null,
                    tint = if (unlocked) Color.Black.copy(alpha = 0.75f) else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(2.dp))
                Text(description, color = TextSecondary, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (unlocked) {
            Text(rewardText, color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        } else {
            LinearProgressIndicator(
                progress = fraction,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                color = accent,
                trackColor = accent.copy(alpha = 0.15f)
            )
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("$clampedProgress / $goal", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text("Reward: $rewardText", color = TextMuted, fontSize = 11.sp)
            }
        }
    }
}
