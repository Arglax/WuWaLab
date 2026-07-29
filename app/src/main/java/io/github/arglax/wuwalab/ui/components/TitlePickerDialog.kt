package io.github.arglax.wuwalab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.arglax.wuwalab.data.ShopItem
import io.github.arglax.wuwalab.data.TitleRarity
import io.github.arglax.wuwalab.ui.theme.EmeraldGlow
import io.github.arglax.wuwalab.ui.theme.GlassBorderSoft
import io.github.arglax.wuwalab.ui.theme.TextMuted
import io.github.arglax.wuwalab.ui.theme.TextPrimary
import io.github.arglax.wuwalab.ui.theme.TextSecondary
import io.github.arglax.wuwalab.ui.theme.VioletGlow
import androidx.compose.foundation.layout.width
/**
 * A dedicated, full window for choosing an equipped title - replaces the old
 * horizontal-scrolling chip row (which hid the title's own description and
 * bonus behind a tiny pill you had to already know the meaning of). Every
 * title is a full-width, readable row here: name, rarity, description, and
 * exactly what it's contributing to the Bonus.
 *
 * IMPORTANT UX note baked into the copy below: a title's bonus comes from
 * simply OWNING it (see [ShopItem.titleBonusPercent] kdoc / ShopRepository's
 * recomputeTitleBonus) - equipping one only changes which badge shows next
 * to your name. This dialog makes that explicit so nobody thinks switching
 * titles costs them their existing bonus.
 */
@Composable
fun TitlePickerDialog(
    ownedTitles: List<ShopItem>,
    equippedTitleId: String?,
    onSelectTitle: (ShopItem?) -> Unit,
    onDismiss: () -> Unit
) {
    val totalPercent = ownedTitles.sumOf { it.titleBonusPercent.toDouble() }.toFloat()
    val totalMultiplier = io.github.arglax.wuwalab.data.BonusMath.combineMultipliers(ownedTitles.map { it.titleBonusMultiplier })

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a Title", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                // Cumulative total up front, plus the "ownership, not
                // equip" clarification - the single biggest source of
                // confusion the old chip row never explained.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(EmeraldGlow.copy(alpha = 0.12f))
                        .border(1.dp, EmeraldGlow.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        "Owning ${ownedTitles.size} title(s) currently adds ${formatBonusText(totalPercent, totalMultiplier)} to your Bonus.",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Every title you own contributes its bonus whether it's equipped or not. Equipping one only changes which badge shows next to your name.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(14.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 420.dp)
                ) {
                    item {
                        TitlePickerRow(
                            name = "None",
                            description = "Show no title next to your name. Doesn't affect your Bonus - that still comes from every title you own.",
                            rarity = null,
                            bonusText = null,
                            selected = equippedTitleId == null,
                            onClick = { onSelectTitle(null) }
                        )
                    }
                    items(ownedTitles) { item ->
                        TitlePickerRow(
                            name = item.name,
                            description = item.description,
                            rarity = item.rarity,
                            bonusText = if (item.titleBonusPercent > 0f || item.titleBonusMultiplier > 1f) {
                                formatBonusText(item.titleBonusPercent, item.titleBonusMultiplier)
                            } else null,
                            selected = equippedTitleId == item.id,
                            onClick = { onSelectTitle(item) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun TitlePickerRow(
    name: String,
    description: String,
    rarity: TitleRarity?,
    bonusText: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = rarity?.color ?: TextMuted
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.14f) else GlassBorderSoft.copy(alpha = 0.06f))
            .border(1.dp, if (selected) accent.copy(alpha = 0.7f) else GlassBorderSoft, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Icon(
            if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = if (selected) "Equipped" else null,
            tint = if (selected) accent else TextMuted,
            modifier = Modifier.size(20.dp).padding(top = 2.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (rarity != null) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(accent.copy(alpha = 0.18f))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(rarity.label, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(description, color = TextSecondary, fontSize = 12.sp)
            if (bonusText != null) {
                Spacer(Modifier.height(4.dp))
                Text("Grants $bonusText", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
