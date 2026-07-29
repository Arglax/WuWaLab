package io.github.arglax.wuwalab.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.arglax.wuwalab.data.FreeAvatarId
import io.github.arglax.wuwalab.data.WuwaProfile
import io.github.arglax.wuwalab.ui.theme.CoralGlow
import io.github.arglax.wuwalab.ui.theme.GlassBorderSoft
import io.github.arglax.wuwalab.ui.theme.GlassSurface
import io.github.arglax.wuwalab.ui.theme.TextMuted
import io.github.arglax.wuwalab.ui.theme.TextPrimary
import io.github.arglax.wuwalab.ui.theme.VioletGlow

@Composable
fun EditProfileDialog(
    initial: WuwaProfile,
    onDismiss: () -> Unit,
    onSave: (ign: String, unionLevel: Int) -> Unit,
    onSelectAvatar: (FreeAvatarId) -> Unit,
    // Swaps in an already-owned photo from the user's permanent custom
    // avatar collection - always free, since it was already paid for once.
    onSelectCustomAvatar: (renderedPath: String) -> Unit = {},
    onOverrideTodayAstrite: (Int) -> Unit,
    // Portraits actually bought in the App Shop - shown as extra, equippable
    // tiles right alongside the free bundled avatars, not hidden behind a
    // separate screen.
    ownedShopAvatars: List<io.github.arglax.wuwalab.data.ShopItem> = emptyList(),
    equippedShopAvatarId: String? = null,
    onSelectShopAvatar: (io.github.arglax.wuwalab.data.ShopItem) -> Unit = {},
    // Titles owned via the Shop OR a Redeem code - tap one to equip it, or
    // "None" to go back to showing no title at all.
    ownedTitles: List<io.github.arglax.wuwalab.data.ShopItem> = emptyList(),
    equippedTitleId: String? = null,
    onSelectTitle: (io.github.arglax.wuwalab.data.ShopItem?) -> Unit = {}
) {
    var ign by remember { mutableStateOf(initial.ign) }
    var unionLevelText by remember { mutableStateOf(initial.unionLevel.toString()) }
    var selectedAvatar by remember { mutableStateOf(initial.selectedAvatar) }
    var overrideValue by remember { mutableStateOf("") }
    // Collapsed by default - "Edit Initial Astrite" is an authoritative
    // override most players never need, so it's tucked behind an explicit
    // "Advanced" disclosure rather than sitting open in the main flow.
    var advancedExpanded by remember { mutableStateOf(false) }
    var showTitlePicker by remember { mutableStateOf(false) }

    // Which picture is actually showing right now, so the right tile gets the
    // "selected" ring - the custom photo (if active) always wins, then a
    // shop portrait, then the free avatar - same priority ProfileHeader uses.
    val customPhotoActive = initial.avatarUnlocked && initial.customAvatarPath != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile", fontWeight = FontWeight.Bold) },
        text = {
            // Scrollable, fixed-height content: expanding Advanced used to
            // resize the dialog window itself, which the system animates
            // separately from the content and made the whole thing lurch.
            // Now the dialog stays put and the content just scrolls.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // --- Avatar picker: free bundled avatars + any portraits
                // actually owned from the App Shop + the custom Profile
                // Studio photo (if one exists) - tap any tile to make it
                // the active picture everywhere in the app. ---
                Text("Profile Picture", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    // Every custom photo ever applied stays permanently in the
                    // collection and is swappable here - not just the one
                    // currently active.
                    initial.customAvatarCollection.forEach { entry ->
                        val bitmap = remember(entry.renderedPath) {
                            runCatching { android.graphics.BitmapFactory.decodeFile(entry.renderedPath) }.getOrNull()
                        }
                        if (bitmap != null) {
                            CustomAvatarOption(
                                bitmap = bitmap,
                                selected = customPhotoActive && initial.customAvatarPath == entry.renderedPath,
                                onClick = { onSelectCustomAvatar(entry.renderedPath) }
                            )
                        }
                    }
                    AvatarOption(
                        drawableRes = avatarDrawableRes(FreeAvatarId.DEFAULT),
                        selected = !customPhotoActive && equippedShopAvatarId == null && selectedAvatar == FreeAvatarId.DEFAULT,
                        onClick = { selectedAvatar = FreeAvatarId.DEFAULT; onSelectAvatar(FreeAvatarId.DEFAULT) }
                    )
                    AvatarOption(
                        drawableRes = avatarDrawableRes(FreeAvatarId.ROVER),
                        selected = !customPhotoActive && equippedShopAvatarId == null && selectedAvatar == FreeAvatarId.ROVER,
                        onClick = { selectedAvatar = FreeAvatarId.ROVER; onSelectAvatar(FreeAvatarId.ROVER) }
                    )
                    AvatarOption(
                        drawableRes = avatarDrawableRes(FreeAvatarId.BEACON),
                        selected = !customPhotoActive && equippedShopAvatarId == null && selectedAvatar == FreeAvatarId.BEACON,
                        onClick = { selectedAvatar = FreeAvatarId.BEACON; onSelectAvatar(FreeAvatarId.BEACON) }
                    )
                    ownedShopAvatars.forEach { item ->
                        val res = io.github.arglax.wuwalab.data.ShopCatalog.drawableRes(
                            androidx.compose.ui.platform.LocalContext.current, item.drawableName
                        )
                        if (res != 0) {
                            AvatarOption(
                                drawableRes = res,
                                selected = !customPhotoActive && equippedShopAvatarId == item.id,
                                onClick = { onSelectShopAvatar(item) }
                            )
                        }
                    }
                }
                Text(
                    "Want a different custom photo? Head to Extras \u2192 Profile Studio to upload and frame one.",
                    color = TextMuted,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    modifier = Modifier.padding(top = 8.dp)
                )

                if (ownedTitles.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = TextMuted.copy(alpha = 0.15f))
                    Spacer(Modifier.height(14.dp))
                    Text("Title", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Pick which title shows next to your name - or none at all.",
                        color = TextMuted,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                    )
                    Spacer(Modifier.height(8.dp))
                    // Opens a dedicated picker window instead of the old
                    // horizontal-scrolling chip strip - each title gets
                    // room to show its full description and bonus there.
                    val equippedTitle = ownedTitles.firstOrNull { it.id == equippedTitleId }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, GlassBorderSoft, RoundedCornerShape(12.dp))
                            .clickable { showTitlePicker = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Currently equipped", color = TextMuted, fontSize = MaterialTheme.typography.labelSmall.fontSize)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                equippedTitle?.name ?: "None",
                                color = equippedTitle?.rarity?.color ?: TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize
                            )
                        }
                        Icon(Icons.Filled.ExpandMore, contentDescription = "Change title", tint = TextMuted, modifier = Modifier.rotate(-90f))
                    }
                    if (showTitlePicker) {
                        io.github.arglax.wuwalab.ui.components.TitlePickerDialog(
                            ownedTitles = ownedTitles,
                            equippedTitleId = equippedTitleId,
                            onSelectTitle = { onSelectTitle(it); showTitlePicker = false },
                            onDismiss = { showTitlePicker = false }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = ign,
                    onValueChange = { ign = it.take(24) },
                    label = { Text("In-Game Name (IGN)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = unionLevelText,
                    onValueChange = { unionLevelText = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("Union Level (1-80)") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = TextMuted.copy(alpha = 0.15f))
                Spacer(Modifier.height(14.dp))

                // --- Advanced (collapsed by default): "Edit Initial Astrite"
                // moved here from the Lunite Pass dialog since it's a
                // profile-level correction, not specific to the Lunite Pass.
                // It's an authoritative REPLACE, never additive - this must
                // never be summed with astriteRepo entries, or the lifetime/
                // dashboard totals become wrong - which is exactly the kind
                // of footgun that belongs behind an explicit "Advanced" tap
                // rather than sitting open where anyone could bump it. ---
                val chevronRotation by animateFloatAsState(
                    targetValue = if (advancedExpanded) 180f else 0f,
                    animationSpec = tween(durationMillis = 120),
                    label = "advancedChevron"
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { advancedExpanded = !advancedExpanded }
                ) {
                    Text("Advanced", fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Filled.ExpandMore,
                        contentDescription = if (advancedExpanded) "Collapse" else "Expand",
                        tint = TextMuted,
                        modifier = Modifier.rotate(chevronRotation)
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    if (advancedExpanded) {
                        Spacer(Modifier.height(10.dp))
                        Text("Edit Initial Astrite", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = CoralGlow, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Authoritative setting - this REPLACES today's logged Astrite total outright, it does not add to it. Only use this to correct or seed your starting count for the day.",
                                color = CoralGlow,
                                fontSize = MaterialTheme.typography.labelSmall.fontSize
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = overrideValue,
                            onValueChange = { overrideValue = it.filter { c -> c.isDigit() }.take(6) },
                            label = { Text("Enter Astrites") },
                            leadingIcon = {
                                Image(painter = painterResource(io.github.arglax.wuwalab.R.drawable.ic_astrite), contentDescription = null, modifier = Modifier.size(20.dp))
                            },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val amount = overrideValue.toIntOrNull()
                                if (amount != null) {
                                    onOverrideTodayAstrite(amount)
                                    overrideValue = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VioletGlow),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Set/Override Astrites") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val level = unionLevelText.toIntOrNull() ?: initial.unionLevel
                onSave(ign, level)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AvatarOption(drawableRes: Int, selected: Boolean, onClick: () -> Unit) {
    Image(
        painter = painterResource(drawableRes),
        contentDescription = null,
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) VioletGlow else GlassBorderSoft,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
    )
}

/** One tile from the user's permanent custom-avatar collection - tap to swap it in as the active picture (always free, it's already owned). */
@Composable
private fun CustomAvatarOption(bitmap: android.graphics.Bitmap, selected: Boolean, onClick: () -> Unit) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Custom photo",
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) VioletGlow else GlassBorderSoft,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
    )
}

