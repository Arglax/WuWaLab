package io.github.arglax.wuwalab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.arglax.wuwalab.data.ShopItem
import io.github.arglax.wuwalab.data.TitleRarity
import io.github.arglax.wuwalab.ui.theme.EmeraldGlow
import io.github.arglax.wuwalab.ui.theme.TextMuted
import io.github.arglax.wuwalab.ui.theme.TextPrimary
import io.github.arglax.wuwalab.ui.theme.TextSecondary

/**
 * The full Titles window - the detailed counterpart to the Profile summary's
 * one-line "Titles  11 owned" link.
 *
 * Every title the player OWNS is listed, not just the equipped one: owning it
 * is what feeds the Argstrite Bonus (see ShopRepository.recomputeTitleBonus),
 * equipping is purely cosmetic. Each row states its rarity and the exact bonus
 * it contributes, with a multiplier rendered as its own x-term rather than
 * folded into the percent - a x10 is not a +10%.
 */
@Composable
fun TitlesDialog(
    ownedTitles: List<ShopItem>,
    equippedTitleId: String?,
    onDismiss: () -> Unit
) {
    val sorted = ownedTitles.sortedWith(
        compareByDescending<ShopItem> { it.rarity?.ordinal ?: -1 }.thenBy { it.name }
    )
    val totalPercent = ownedTitles.sumOf { it.titleBonusPercent.toDouble() }.toFloat()
    val totalMultiplier = io.github.arglax.wuwalab.data.BonusMath.combineMultipliers(ownedTitles.map { it.titleBonusMultiplier })

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Titles", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        ownedTitles.size.toString() + " owned",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        formatBonusText(totalPercent, totalMultiplier) + " from titles",
                        color = EmeraldGlow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = TextMuted.copy(alpha = 0.15f))
                Spacer(Modifier.height(10.dp))

                if (sorted.isEmpty()) {
                    Text(
                        "No titles yet. Buy one in the Shop or redeem a code.",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
                    ) {
                        items(sorted, key = { it.id }) { item ->
                            TitleRow(item = item, equipped = item.id == equippedTitleId)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun TitleRow(item: ShopItem, equipped: Boolean) {
    val isMythic = item.rarity == TitleRarity.MYTHIC
    val accent = item.rarity?.color ?: EmeraldGlow
    val bonusText = buildString {
        if (item.titleBonusPercent > 0f) append("+" + formatBonusPercent(item.titleBonusPercent) + "%")
        if (item.titleBonusMultiplier > 1f) {
            if (isNotEmpty()) append("  ")
            append("\u00d7" + formatBonusPercent(item.titleBonusMultiplier))
        }
    }

    // Mythic gets the animated rainbow ring instead of the flat white its
    // palette entry would otherwise paint.
    val frame = if (isMythic) {
        Modifier.animatedSweepBorder(colors = MythicRainbow, cornerRadius = 14.dp, strokeWidth = 1.5.dp)
    } else {
        Modifier.border(1.dp, accent.copy(alpha = if (equipped) 0.6f else 0.28f), RoundedCornerShape(14.dp))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(frame)
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = if (isMythic) 0.05f else if (equipped) 0.15f else 0.07f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (equipped) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Equipped", tint = accent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
            }
            TitleBadge(text = item.name, rarity = item.rarity, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            if (bonusText.isNotEmpty()) {
                Text(bonusText, color = EmeraldGlow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.07f))
                    .padding(horizontal = 7.dp, vertical = 1.dp)
            ) {
                Text(item.rarity?.label ?: "Title", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            if (equipped) {
                Spacer(Modifier.width(6.dp))
                Text("Equipped", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(item.description, color = TextPrimary.copy(alpha = 0.85f), fontSize = 11.sp)
    }
}
