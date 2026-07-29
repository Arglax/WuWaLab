package io.github.arglax.wuwalab.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.arglax.wuwalab.R
import io.github.arglax.wuwalab.data.FreeAvatarId
import io.github.arglax.wuwalab.data.WuwaProfile
import io.github.arglax.wuwalab.ui.theme.AmberGlow
import io.github.arglax.wuwalab.ui.theme.EmeraldGlow
import io.github.arglax.wuwalab.ui.theme.GlassSurface
import io.github.arglax.wuwalab.ui.theme.TextMuted
import io.github.arglax.wuwalab.ui.theme.TextPrimary
import io.github.arglax.wuwalab.ui.theme.TextSecondary
import io.github.arglax.wuwalab.ui.theme.VioletGlow

/** Resolves the currently-selected free avatar to its bundled drawable resource. */
fun avatarDrawableRes(avatar: FreeAvatarId): Int = when (avatar) {
    FreeAvatarId.DEFAULT -> R.drawable.ic_default_avatar
    FreeAvatarId.ROVER -> R.drawable.ic_avatar_rover
    FreeAvatarId.BEACON -> R.drawable.ic_avatar_beacon
}

/**
 * The personalization header pinned to the very top of the app.
 *
 * Sizing note: every dimension here is the previous design pass divided by
 * 1.2 (x0.83) - the header was eating too much vertical space. Original ->
 * now: avatar 60->50dp, IGN 21->17sp, stat text 18->15sp, astrite icons
 * 27->22dp, UL icon 24->20dp, padding 18/13->15/11dp, gaps scaled to match.
 *
 * The old static hairline border is replaced with a fixed (non-spinning)
 * sweep-gradient ring (see [staticSweepBorder]) so the header reads as the
 * app's one "premium" element without growing a single dp. This used to spin
 * continuously via [animatedSweepBorder], but a header that never stops
 * rotating turned out to be distracting rather than "premium" - it's now
 * frozen at a fixed angle instead.
 */
/**
 * A small colored pill showing an equipped title, styled by its [TitleRarity]:
 * gray/green/purple/gold, and for Legendary a slow pulsing red glow.
 */
/**
 * The rainbow ramp every Mythic ("???") flourish animates through, so the
 * title badge, the Shop tile preview and the Profile popup all read as the
 * same one-of-a-kind tier rather than a flat white outline.
 */
val MythicRainbow: List<Color> = listOf(
    Color(0xFFFF3B3B),
    Color(0xFFFFA23B),
    Color(0xFFFFE83B),
    Color(0xFF52E39A),
    Color(0xFF3BC9FF),
    Color(0xFF7A5CFF),
    Color(0xFFFF5CE1),
    Color(0xFFFF3B3B)
)

@Composable
fun TitleBadge(text: String, rarity: io.github.arglax.wuwalab.data.TitleRarity?, fontSize: androidx.compose.ui.unit.TextUnit = 12.sp) {
    val baseColor = rarity?.color ?: AmberGlow
    val isLegendary = rarity == io.github.arglax.wuwalab.data.TitleRarity.LEGENDARY
    val isMythic = rarity == io.github.arglax.wuwalab.data.TitleRarity.MYTHIC

    // MYTHIC's palette entry is a plain white, which rendered "???" as a dead
    // white hairline - the rarest thing in the app looking like the cheapest.
    // Mythic now gets the same rotating rainbow sweep the Legendary avatar
    // border uses, plus a slow hue cycle and a breathing fill.
    if (isMythic) {
        val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "mythicTitle")
        val phase = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(2600, easing = androidx.compose.animation.core.LinearEasing),
                repeatMode = androidx.compose.animation.core.RepeatMode.Restart
            ),
            label = "mythicTitleHue"
        )
        val glow = transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(1100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "mythicTitleGlow"
        )
        Box(
            modifier = Modifier
                .animatedSweepBorder(colors = MythicRainbow, cornerRadius = 6.dp, strokeWidth = 1.5.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.05f + 0.07f * glow.value))
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text(
                text,
                color = Color.hsv((phase.value * 360f) % 360f, 0.5f, 1f),
                fontWeight = FontWeight.Bold,
                fontSize = fontSize,
                maxLines = 1
            )
        }
        return
    }

    val pulse = if (isLegendary) {
        val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "titlePulse")
        transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "titlePulseAlpha"
        )
    } else null

    val glowAlpha = pulse?.value ?: 1f
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(baseColor.copy(alpha = 0.18f + 0.12f * glowAlpha))
            .then(
                if (isLegendary) Modifier.border(1.dp, baseColor.copy(alpha = glowAlpha), RoundedCornerShape(6.dp))
                else Modifier
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = baseColor.copy(alpha = 0.7f + 0.3f * glowAlpha), fontWeight = FontWeight.Bold, fontSize = fontSize, maxLines = 1)
    }
}

/** "12" for a whole number, "12.5" for a fraction - never a trailing ".0". */
internal fun formatBonusPercent(percent: Float): String =
    if (percent == percent.toInt().toFloat()) percent.toInt().toString() else "%.1f".format(percent)

/**
 * Full bonus text combining the summed flat % with the separate xN
 * multiplier when one is active - e.g. "+30%" normally, but "+30% ×10" once
 * a multiplier title (like "???") is owned. A x10 is NOT the same as a
 * +10% (it multiplies the already-bonused amount, not adds to the percent
 * pool), so it must always render as its own distinct "×N" term rather than
 * being folded into the percent figure.
 */
internal fun formatBonusText(percent: Float, multiplier: Float): String {
    val base = "+${formatBonusPercent(percent)}%"
    return if (multiplier > 1f) "$base \u00d7${formatBonusPercent(multiplier)}" else base
}

@Composable
fun ProfileHeader(
    profile: WuwaProfile,
    lifetimeAstrites: Int,
    radiantAstrites: Int,
    onClick: () -> Unit,
    onEditClick: () -> Unit = onClick,
    // Opens the App Shop. Sits immediately to the RIGHT of the edit pencil.
    onShopClick: () -> Unit = {},
    // A shop-bought portrait, when one is equipped. 0 means "use the free avatar".
    shopAvatarRes: Int = 0,
    // The equipped title's display name (from Redeem or the Shop), null when none is equipped.
    equippedTitle: String? = null,
    equippedTitleRarity: io.github.arglax.wuwalab.data.TitleRarity? = null,
    // The equipped Avatar Border item's style (static color or the rotating
    // rainbow sweep), null when none is equipped.
    equippedAvatarBorder: io.github.arglax.wuwalab.data.AvatarBorderStyle? = null,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tapFeedback = io.github.arglax.wuwalab.util.rememberTapFeedback()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .staticSweepBorder(
                colors = listOf(
                    VioletGlow.copy(alpha = 0.85f),
                    VioletGlow.copy(alpha = 0.15f),
                    AmberGlow.copy(alpha = 0.7f),
                    VioletGlow.copy(alpha = 0.15f)
                ),
                cornerRadius = 15.dp,
                strokeWidth = 1.5.dp
            )
            .clip(RoundedCornerShape(15.dp))
            .background(GlassSurface)
            // Tapping the header itself now opens the Profile Stats popup
            // (see ProfileStatsDialog below) - editing has moved to the
            // dedicated pencil icon at the end of this row.
            .clickable(onClick = { tapFeedback(); onClick() })
            .padding(horizontal = 15.dp, vertical = 11.dp)
    ) {
        // Custom Profile Studio photo takes priority over a shop-bought
        // portrait, which in turn takes priority over the bundled free
        // avatars - see ProfileStudioRepository.applyCurrentFraming.
        val customAvatarBitmap = if (profile.avatarUnlocked && profile.customAvatarPath != null) {
            // Profile Studio always writes to the same fixed filename, so the
            // path string alone never changes when a new photo is applied -
            // the file's lastModified() has to be part of the cache key or
            // this keeps showing whatever picture was first ever uploaded.
            remember(profile.customAvatarPath, profile.customAvatarPath?.let { java.io.File(it).lastModified() }) {
                runCatching { android.graphics.BitmapFactory.decodeFile(profile.customAvatarPath) }.getOrNull()
            }
        } else null

        val avatarModifier = Modifier
            .size(if (isLandscape) 56.dp else 50.dp)
            .rarityAvatarBorder(equippedAvatarBorder, cornerRadius = 12.dp)

        if (customAvatarBitmap != null) {
            Image(
                bitmap = customAvatarBitmap.asImageBitmap(),
                contentDescription = "Profile picture",
                contentScale = ContentScale.Crop,
                modifier = avatarModifier
            )
        } else {
            Image(
                painter = painterResource(if (shopAvatarRes != 0) shopAvatarRes else avatarDrawableRes(profile.selectedAvatar)),
                contentDescription = "Profile picture",
                modifier = avatarModifier
            )
        }
        Spacer(Modifier.width(12.dp))

        if (isLandscape) {
            // Landscape: everything stays in one horizontal line - there's
            // enough width that a long name and the stats never collide.
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    profile.ign,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 19.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (!equippedTitle.isNullOrBlank()) {
                    TitleBadge(equippedTitle, equippedTitleRarity, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.width(20.dp))
            ProfileStatChip(iconRes = R.drawable.ic_ul, contentDescription = "Union Level", text = "UL${profile.unionLevel}", textColor = TextSecondary)
            Spacer(Modifier.width(24.dp))
            ProfileStatChip(iconRes = R.drawable.ic_astrite, contentDescription = "Astrites", label = "Astrites", text = lifetimeAstrites.toString(), textColor = AmberGlow)
            Spacer(Modifier.width(24.dp))
            ProfileStatChip(iconRes = R.drawable.ic_radiant_astrite, contentDescription = "Argstrites", label = "Argstrites", text = io.github.arglax.wuwalab.util.formatArgstrites(radiantAstrites), textColor = VioletGlow)
        } else {
            // Portrait: the name gets its own full-width row so a long IGN
            // simply truncates in place instead of squeezing into the stats
            // row and pushing them out of alignment - the stats always sit
            // on their own row underneath, in a fixed spot every time.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    profile.ign,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!equippedTitle.isNullOrBlank()) {
                    TitleBadge(equippedTitle, equippedTitleRarity, fontSize = 11.sp)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileStatChip(iconRes = R.drawable.ic_ul, contentDescription = "Union Level", text = "UL${profile.unionLevel}", textColor = TextSecondary)
                    Spacer(Modifier.width(13.dp))
                    ProfileStatChip(iconRes = R.drawable.ic_astrite, contentDescription = "Astrites", text = lifetimeAstrites.toString(), textColor = AmberGlow)
                    Spacer(Modifier.width(13.dp))
                    ProfileStatChip(iconRes = R.drawable.ic_radiant_astrite, contentDescription = "Argstrites", text = io.github.arglax.wuwalab.util.formatArgstrites(radiantAstrites), textColor = VioletGlow)
                }
            }
        }

        Spacer(Modifier.width(10.dp))
        // Edit on the left, Shop on the right - same size, same spacing, so
        // the pair reads as one control cluster in portrait and landscape.
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderIconButton(
                icon = Icons.Filled.Edit,
                contentDescription = "Edit Profile",
                tint = TextPrimary,
                onClick = { tapFeedback(); onEditClick() }
            )
            Spacer(Modifier.width(8.dp))
            HeaderIconButton(
                icon = Icons.Filled.ShoppingCart,
                contentDescription = "Open App Shop",
                tint = AmberGlow,
                onClick = { tapFeedback(); onShopClick() }
            )
        }
    }
}

/** One round 26dp action button in the profile header. */
@Composable
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(15.dp))
    }
}

/**
 * One icon+value stat chip, shared by both the landscape and portrait
 * header layouts. [label], when provided (landscape only - there's no room
 * for it in portrait), shows a text label before the value, e.g. "Astrites 1,204".
 * The Union Level chip instead folds its own "UL" prefix directly into
 * [text] (e.g. "UL45"), since that reads naturally in both layouts without
 * needing a separate label slot.
 */
@Composable
private fun ProfileStatChip(
    iconRes: Int,
    contentDescription: String,
    text: String,
    textColor: Color,
    label: String? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painter = painterResource(iconRes), contentDescription = contentDescription, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(4.dp))
        if (label != null) {
            Text(label, color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontWeight = FontWeight.Bold, color = textColor, fontSize = 15.sp)
    }
}

/**
 * Shown when the player taps the Profile header itself - a read-only
 * snapshot of everything the header + Dashboard know about them: avatar,
 * IGN, Union Level, and both currencies with (?) explainers, since Astrites
 * and Argstrites are easy to confuse at a glance.
 */
@Composable
fun ProfileStatsDialog(
    profile: WuwaProfile,
    lifetimeAstrites: Int,
    radiantAstrites: Int,
    shopUnlocked: Int = 0,
    shopTotal: Int = 0,
    customWidgetBackgrounds: Int = 0,
    // Same priority as the header itself: a Profile Studio custom photo beats
    // a shop-bought portrait, which beats the bundled free avatars - so the
    // summary never shows a stale picture after either one changes.
    shopAvatarRes: Int = 0,
    equippedTitle: String? = null,
    equippedTitleRarity: io.github.arglax.wuwalab.data.TitleRarity? = null,
    // The equipped Avatar Border item's style (static color or the rotating
    // rainbow sweep), null when none is equipped.
    equippedAvatarBorder: io.github.arglax.wuwalab.data.AvatarBorderStyle? = null,
    // The LIVE sum of every unlocked Achievement's +1%, every owned title's
    // stated bonus, plus the Supporter title's +20% - this is the real
    // multiplier WuwaRepository applies to every future Argstrite earning,
    // never just a cosmetic number.
    bonusPercent: Float = 0f,
    // The separate xN multiplier stacked on top of bonusPercent by
    // multiplier titles (e.g. "???"'s x10). 1f = no active multiplier.
    bonusMultiplier: Float = 1f,
    achievements: List<io.github.arglax.wuwalab.data.AchievementUiState> = emptyList(),
    supporterUnlocked: Boolean = false,
    // Every Title the player owns (bought or redeemed), regardless of which
    // one - if any - is currently equipped, so the profile summary can show
    // the FULL collection, not just whichever one is worn right now.
    ownedTitles: List<io.github.arglax.wuwalab.data.ShopItem> = emptyList(),
    equippedTitleId: String? = null,
    onDismiss: () -> Unit
) {
    var showAchievementsDialog by remember { mutableStateOf(false) }
    var showTitlesDialog by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profile", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val customAvatarBitmap = if (profile.avatarUnlocked && profile.customAvatarPath != null) {
                        remember(profile.customAvatarPath, profile.customAvatarPath?.let { java.io.File(it).lastModified() }) {
                            runCatching { android.graphics.BitmapFactory.decodeFile(profile.customAvatarPath) }.getOrNull()
                        }
                    } else null
                    val avatarModifier = Modifier
                        .size(56.dp)
                        .rarityAvatarBorder(equippedAvatarBorder, cornerRadius = 14.dp)
                    if (customAvatarBitmap != null) {
                        Image(
                            bitmap = customAvatarBitmap.asImageBitmap(),
                            contentDescription = "Profile picture",
                            contentScale = ContentScale.Crop,
                            modifier = avatarModifier
                        )
                    } else {
                        Image(
                            painter = painterResource(if (shopAvatarRes != 0) shopAvatarRes else avatarDrawableRes(profile.selectedAvatar)),
                            contentDescription = "Profile picture",
                            modifier = avatarModifier
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(profile.ign, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
                        if (!equippedTitle.isNullOrBlank()) {
                            Spacer(Modifier.height(2.dp))
                            TitleBadge(equippedTitle, equippedTitleRarity, fontSize = 12.sp)
                            Spacer(Modifier.height(2.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(painter = painterResource(R.drawable.ic_ul), contentDescription = "Union Level", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Union Level ${profile.unionLevel}", color = TextSecondary, fontSize = 14.sp)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = TextMuted.copy(alpha = 0.15f))
                Spacer(Modifier.height(14.dp))

                ProfileStatRow(
                    iconRes = R.drawable.ic_astrite,
                    label = "Astrites",
                    value = lifetimeAstrites.toString(),
                    valueColor = AmberGlow,
                    tooltipTitle = "Astrites",
                    tooltipBody = "Wuthering Waves' own convene currency. Every entry you log on the Astrite Tracker adds up into this lifetime total, and it's what the Pull Planner spends against."
                )
                Spacer(Modifier.height(10.dp))
                ProfileStatRow(
                    iconRes = R.drawable.ic_radiant_astrite,
                    label = "Argstrites",
                    value = io.github.arglax.wuwalab.util.formatArgstrites(radiantAstrites),
                    valueColor = VioletGlow,
                    tooltipTitle = "Argstrites",
                    tooltipBody = "WuWaLab's own in-app currency (not from the game itself). Earned by logging in daily via the Daily Sign-In card, and redeemable for extras like unlocking custom profile portraits."
                )
                if (bonusPercent > 0f || bonusMultiplier > 1f) {
                    Spacer(Modifier.height(10.dp))
                    ProfileStatRow(
                        iconRes = R.drawable.ic_radiant_astrite,
                        label = "Bonus",
                        value = formatBonusText(bonusPercent, bonusMultiplier),
                        valueColor = EmeraldGlow,
                        tooltipTitle = "Argstrite Bonus",
                        tooltipBody = if (bonusMultiplier > 1f) {
                            "A permanent boost on every Argstrite you earn from now on - Daily Sign-In, the Earn quiz, logging bonuses, all of it. The +% stacks +1% per unlocked Achievement plus each owned title's stated %, then the \u00d7${formatBonusPercent(bonusMultiplier)} from a multiplier title (like \"???\") is applied ON TOP of that - it's not folded into the percent, it multiplies the whole already-bonused amount again. If the exact result works out to a fraction, you always get rounded UP to the next whole Argstrite."
                        } else {
                            "A permanent multiplier on every Argstrite you earn from now on - Daily Sign-In, the Earn quiz, logging bonuses, all of it. Stacks +1% per unlocked Achievement, plus each owned title's stated %. If a reward's exact bonus works out to a fraction, you always get rounded UP to the next whole Argstrite."
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = TextMuted.copy(alpha = 0.15f))
                Spacer(Modifier.height(14.dp))

                Text("Collection", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                UnlockRow("Shop items unlocked", shopUnlocked.toString() + " / " + shopTotal)
                Spacer(Modifier.height(6.dp))
                UnlockRow(
                    "Still locked",
                    (shopTotal - shopUnlocked).coerceAtLeast(0).toString() + " item(s)"
                )
                Spacer(Modifier.height(6.dp))
                UnlockRow("Custom widget backgrounds", customWidgetBackgrounds.toString())

                if (achievements.isNotEmpty() || supporterUnlocked || ownedTitles.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = TextMuted.copy(alpha = 0.15f))
                    Spacer(Modifier.height(14.dp))
                    // Achievements and Titles each have their own full window
                    // now, with badges, descriptions and per-item progress -
                    // duplicating a cramped preview row here just made this
                    // summary long without ever being the better place to read
                    // either list. The summary states the count and gets out
                    // of the way.
                    if (achievements.isNotEmpty() || supporterUnlocked) {
                        val unlockedCount = achievements.count { it.unlocked } + (if (supporterUnlocked) 1 else 0)
                        val totalCount = achievements.size + (if (supporterUnlocked) 1 else 0)
                        SummaryLinkRow(
                            label = "Achievements",
                            value = unlockedCount.toString() + " / " + totalCount,
                            onClick = { showAchievementsDialog = true }
                        )
                    }
                    if (ownedTitles.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        SummaryLinkRow(
                            label = "Titles",
                            value = ownedTitles.size.toString() + " owned",
                            onClick = { showTitlesDialog = true }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
    if (showAchievementsDialog) {
        AchievementsDialog(
            achievements = achievements,
            supporterUnlocked = supporterUnlocked,
            onDismiss = { showAchievementsDialog = false }
        )
    }
    if (showTitlesDialog) {
        TitlesDialog(
            ownedTitles = ownedTitles,
            equippedTitleId = equippedTitleId,
            onDismiss = { showTitlesDialog = false }
        )
    }
}

/**
 * One tappable "Achievements  1 / 7  ->" style row in the Profile summary.
 * Deliberately just a label, a count and an affordance: the real detail lives
 * in the dedicated window this opens.
 */
@Composable
private fun SummaryLinkRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Text(label, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        Text("\u2192", color = VioletGlow, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun ProfileStatRow(
    iconRes: Int,
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    tooltipTitle: String,
    tooltipBody: String
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Image(painter = painterResource(iconRes), contentDescription = label, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.width(6.dp))
        TooltipIcon(title = tooltipTitle, body = tooltipBody)
    }
}

/** One compact card in the Achievements row - green/checked when unlocked, muted with a progress fraction when not. [description] spells out the actual requirement so nobody has to guess what a title like "Matrix Custodian" means. [legendary] styles the one-off Supporter card like a Legendary title. */
@Composable
@Suppress("unused")
private fun AchievementChip(title: String, description: String, progressText: String, unlocked: Boolean, legendary: Boolean = false) {
    val accent = when {
        legendary -> io.github.arglax.wuwalab.data.TitleRarity.LEGENDARY.color
        unlocked -> EmeraldGlow
        else -> TextMuted
    }
    Column(
        modifier = Modifier
            .width(170.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = if (unlocked) 0.14f else 0.06f))
            .border(1.dp, accent.copy(alpha = if (unlocked) 0.55f else 0.25f), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (unlocked) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                title,
                color = if (unlocked) TextPrimary else TextMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 2
            )
        }
        Spacer(Modifier.height(3.dp))
        // The actual requirement, spelled out - so "Matrix Custodian" doesn't
        // leave anyone guessing what it takes to unlock it.
        Text(description, color = TextSecondary, fontSize = 10.sp, maxLines = 3)
        Spacer(Modifier.height(4.dp))
        Text(progressText, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * One compact card in the Titles row - styled by the title's own
 * [TitleRarity] color rather than a fixed accent (unlike [AchievementChip]),
 * since a title's rarity IS its identity. Shows the flat % bonus AND, when
 * present, the title's own xN multiplier as a clearly separate "×N" term -
 * never folded into the percent - since e.g. the "???" title is a x10 on
 * top of everything else, not a +10%.
 */
@Composable
@Suppress("unused")
private fun TitleChip(item: io.github.arglax.wuwalab.data.ShopItem, equipped: Boolean) {
    val accent = item.rarity?.color ?: EmeraldGlow
    Column(
        modifier = Modifier
            .width(170.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = if (equipped) 0.16f else 0.08f))
            .border(1.dp, accent.copy(alpha = if (equipped) 0.6f else 0.3f), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (equipped) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Equipped", tint = accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                item.name,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 2
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(item.rarity?.label ?: "Title", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(item.description, color = TextSecondary, fontSize = 10.sp, maxLines = 3)
        if (item.titleBonusPercent > 0f || item.titleBonusMultiplier > 1f) {
            Spacer(Modifier.height(4.dp))
            Text(
                formatBonusText(item.titleBonusPercent, item.titleBonusMultiplier),
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** One "label ..... value" line in the Collection block of the profile overview. */
@Composable
private fun UnlockRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
