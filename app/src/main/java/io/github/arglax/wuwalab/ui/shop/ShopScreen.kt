package io.github.arglax.wuwalab.ui.shop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.arglax.wuwalab.R
import io.github.arglax.wuwalab.data.PurchaseResult
import io.github.arglax.wuwalab.data.ShopCatalog
import io.github.arglax.wuwalab.data.ShopCategory
import io.github.arglax.wuwalab.data.ShopItem
import io.github.arglax.wuwalab.data.ShopRepository
import io.github.arglax.wuwalab.data.ShopSort
import io.github.arglax.wuwalab.data.WuwaRepository
import io.github.arglax.wuwalab.ui.components.GlassCard
import io.github.arglax.wuwalab.ui.components.HelpButton
import io.github.arglax.wuwalab.ui.theme.AmberGlow
import io.github.arglax.wuwalab.ui.theme.CoralGlow
import io.github.arglax.wuwalab.ui.theme.EmeraldGlow
import io.github.arglax.wuwalab.ui.theme.GlassBorderSoft
import io.github.arglax.wuwalab.ui.theme.GlassSurface
import io.github.arglax.wuwalab.ui.theme.TextMuted
import io.github.arglax.wuwalab.ui.theme.TextPrimary
import io.github.arglax.wuwalab.ui.theme.TextSecondary
import io.github.arglax.wuwalab.ui.theme.VioletGlow
import io.github.arglax.wuwalab.util.rememberConfirmFeedback
import io.github.arglax.wuwalab.util.rememberTapFeedback
import io.github.arglax.wuwalab.widget.WuwaWidget
import kotlinx.coroutines.launch

/**
 * The App Shop.
 *
 * Spends ARGSTRITES only - the app's own currency from the Daily Sign-In card.
 * Your real Astrite convene budget is never touched here, and the Buy button
 * is disabled the moment an item costs more than you hold, with the shortfall
 * spelled out underneath it. A second balance check runs inside the purchase
 * itself, so a negative Argstrite balance is not reachable from any path.
 */
@Composable
fun ShopScreen(
    shopRepo: ShopRepository,
    wuwaRepo: WuwaRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tapFeedback = rememberTapFeedback()
    val confirmFeedback = rememberConfirmFeedback()

    val argstrites by wuwaRepo.radiantAstriteFlow.collectAsState(initial = 0)
    val shopState by shopRepo.stateFlow.collectAsState()

    var sort by remember { mutableStateOf(ShopSort.PRICE_LOW_HIGH) }
    var categoryFilter by remember { mutableStateOf<ShopCategory?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    // Buying is final, so every purchase goes through an explicit confirmation.
    var pendingPurchase by remember { mutableStateOf<ShopItem?>(null) }
    // Set when someone equips an item whose artwork hasn't shipped yet.
    var placeholderNotice by remember { mutableStateOf<ShopItem?>(null) }

    LaunchedEffect(Unit) { shopRepo.refresh() }

    pendingPurchase?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingPurchase = null },
            title = { Text("Buy " + item.name + "?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "This costs " + io.github.arglax.wuwalab.util.formatArgstrites(item.price) + " Argstrites out of your " + io.github.arglax.wuwalab.util.formatArgstrites(argstrites) + ".",
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Purchases are final - we do not offer refunds. Make sure this is the one you want.",
                        color = CoralGlow,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingPurchase = null
                    scope.launch {
                        when (val result = shopRepo.purchase(item)) {
                            is PurchaseResult.Success -> {
                                confirmFeedback()
                                messageIsError = false
                                message = "Purchased " + item.name + ". " + io.github.arglax.wuwalab.util.formatArgstrites(result.remainingArgstrites) + " Argstrites left."
                                WuwaWidget.updateAll(context)
                            }
                            is PurchaseResult.NotEnoughArgstrites -> {
                                messageIsError = true
                                message = "You need " + io.github.arglax.wuwalab.util.formatArgstrites(result.needed) + " Argstrites but only have " + io.github.arglax.wuwalab.util.formatArgstrites(result.balance) + ". Claim your Daily Sign-In to top up."
                            }
                            is PurchaseResult.AlreadyOwned -> {
                                messageIsError = true
                                message = "You already own " + item.name + "."
                            }
                            PurchaseResult.Unavailable -> {
                                messageIsError = true
                                message = "That item is not available right now."
                            }
                        }
                    }
                }) { Text("Buy for " + io.github.arglax.wuwalab.util.formatArgstrites(item.price)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingPurchase = null }) { Text("Cancel") }
            }
        )
    }

    placeholderNotice?.let { item ->
        AlertDialog(
            onDismissRequest = { placeholderNotice = null },
            title = { Text("Portrait not applied", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Portrait not applied, contact Arglax.\n\n" +
                        item.name + " doesn't have its artwork yet, so equipping it would leave your " +
                        "profile blank. You still own it - it will apply itself once the art ships.",
                    color = TextPrimary
                )
            },
            confirmButton = {
                TextButton(onClick = { placeholderNotice = null }) { Text("Got it") }
            }
        )
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val columns = if (isLandscape) 4 else 2

    val visibleItems = remember(sort, categoryFilter, shopState) {
        ShopCatalog.items
            .filterNot { it.redeemOnly }
            .filter { categoryFilter == null || it.category == categoryFilter }
            .sortedWith(
                when (sort) {
                    ShopSort.PRICE_LOW_HIGH -> compareBy<ShopItem> { it.price }.thenBy { it.name }
                    ShopSort.PRICE_HIGH_LOW -> compareByDescending<ShopItem> { it.price }.thenBy { it.name }
                    ShopSort.NAME -> compareBy { it.name }
                    ShopSort.OWNED_LAST -> compareBy<ShopItem> { shopState.owned.contains(it.id) }
                        .thenBy { it.price }
                        .thenBy { it.name }
                }
            )
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(columns) }) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "App Shop",
                        fontWeight = FontWeight.Bold,
                        fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    HelpButton(
                        title = "App Shop Help",
                        body = "Everything here is bought with Argstrites - WuWaLab's own currency, " +
                            "not the game's Astrites. You earn Argstrites by tapping Daily Sign-In on the Dashboard " +
                            "(+10 a day), by claiming the Argstrites banked from using real features around the app, " +
                            "and an in-app way to farm more is on the way.\n\n" +
                            "Profile pictures cost ${ShopCategory.PROFILE_PICTURE.priceArgstrites} Argstrites and appear in your profile header. " +
                            "Widget backgrounds cost ${ShopCategory.WIDGET_BACKGROUND.priceArgstrites} and change the artwork behind your home-screen widget. " +
                            "Titles are individually priced and, once owned, permanently stack their stated Argstrite Bonus % - equipping is just cosmetic, owning it is what counts. " +
                            "Avatar Borders (30-100 Argstrites by rarity) wrap your equipped profile picture in a colored ring, up to a rotating rainbow sweep for the rarest ones.\n\n" +
                            "You can only buy something you can afford, and buying it once is enough - " +
                            "owned items switch to an Equip button."
                    )
                }
                Spacer(Modifier.height(12.dp))

                GlassCard(modifier = Modifier.fillMaxWidth(), accent = VioletGlow) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_radiant_astrite),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Your Argstrites", color = TextSecondary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
                            Text(
                                io.github.arglax.wuwalab.util.formatArgstrites(argstrites),
                                color = VioletGlow,
                                fontWeight = FontWeight.Bold,
                                fontSize = MaterialTheme.typography.headlineSmall.fontSize
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Owned", color = TextSecondary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
                            Text(
                                shopState.owned.size.toString() + " / " + ShopCatalog.items.size,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Argstrites are separate from Astrites - nothing you buy here touches your convene budget.",
                        color = TextMuted,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Type and Sort used to be two horizontally-scrolling strips
                // of chips, which meant the option you wanted was often parked
                // off-screen. Both are now a labelled field that opens a
                // proper menu, so every option is one tap away and the current
                // selection is always readable without scrolling.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    PickerField(
                        label = "Type",
                        value = categoryFilter?.label ?: "All types",
                        options = listOf("All types") + ShopCategory.entries.map { it.label },
                        selectedIndex = categoryFilter?.let { ShopCategory.entries.indexOf(it) + 1 } ?: 0,
                        onSelect = { index ->
                            categoryFilter = if (index == 0) null else ShopCategory.entries[index - 1]
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PickerField(
                        label = "Sort",
                        value = sort.label,
                        options = ShopSort.entries.map { it.label },
                        selectedIndex = ShopSort.entries.indexOf(sort),
                        onSelect = { index -> sort = ShopSort.entries[index] },
                        modifier = Modifier.weight(1f)
                    )
                }

                message?.let { text ->
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background((if (messageIsError) CoralGlow else EmeraldGlow).copy(alpha = 0.16f))
                            .border(1.dp, (if (messageIsError) CoralGlow else EmeraldGlow).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text,
                            color = if (messageIsError) CoralGlow else EmeraldGlow,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
            }
        }

        items(visibleItems, key = { it.id }) { item ->
            val owned = shopState.owned.contains(item.id)
            val equipped = when (item.category) {
                ShopCategory.PROFILE_PICTURE -> shopState.equippedAvatarId == item.id
                ShopCategory.WIDGET_BACKGROUND -> shopState.equippedWidgetBgId == item.id
                ShopCategory.TITLE -> shopState.equippedTitleId == item.id
                ShopCategory.AVATAR_BORDER -> shopState.equippedAvatarBorderId == item.id
            }
            ShopItemTile(
                item = item,
                owned = owned,
                equipped = equipped,
                balance = argstrites,
                onBuy = {
                    tapFeedback()
                    pendingPurchase = item
                },
                onEquip = {
                    tapFeedback()
                    // Equipping a tile with no artwork would blank the header,
                    // which just reads as a broken app - say so instead. Titles
                    // have no artwork by design (they're just text), so this
                    // check only applies to portraits/widget backgrounds.
                    if (item.category != ShopCategory.TITLE && item.category != ShopCategory.AVATAR_BORDER && ShopCatalog.drawableRes(context, item.drawableName) == 0) {
                        placeholderNotice = item
                    } else {
                        shopRepo.equip(item)
                        messageIsError = false
                        message = item.name + " equipped."
                        scope.launch { WuwaWidget.updateAll(context) }
                    }
                },
                onUnequip = {
                    tapFeedback()
                    shopRepo.unequip(item.category)
                    messageIsError = false
                    message = "Reverted to the default " + item.category.label.lowercase().removeSuffix("s") + "."
                    scope.launch { WuwaWidget.updateAll(context) }
                }
            )
        }
    }
}

/**
 * A labelled field that opens a menu of [options] - the replacement for the
 * old sliding chip strips in the Shop header.
 */
@Composable
private fun PickerField(
    label: String,
    value: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    val tapFeedback = rememberTapFeedback()
    Column(modifier = modifier) {
        Text(label, color = TextMuted, fontSize = MaterialTheme.typography.labelSmall.fontSize)
        Spacer(Modifier.height(4.dp))
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, if (open) VioletGlow else GlassBorderSoft, RoundedCornerShape(12.dp))
                    .clickable { tapFeedback(); open = true }
                    .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)
            ) {
                Text(
                    value,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Change " + label, tint = TextSecondary)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                option,
                                color = if (index == selectedIndex) VioletGlow else TextPrimary,
                                fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            tapFeedback()
                            onSelect(index)
                            open = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
@Suppress("unused")
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) VioletGlow.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (selected) VioletGlow else GlassBorderSoft, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            color = if (selected) TextPrimary else TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
            maxLines = 1
        )
    }
}

@Composable
private fun ShopItemTile(
    item: ShopItem,
    owned: Boolean,
    equipped: Boolean,
    balance: Int,
    onBuy: () -> Unit,
    onEquip: () -> Unit,
    onUnequip: () -> Unit
) {
    val context = LocalContext.current
    val artRes = remember(item.drawableName) { ShopCatalog.drawableRes(context, item.drawableName) }
    val affordable = balance >= item.price

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        accent = if (equipped) EmeraldGlow else if (owned) VioletGlow else AmberGlow,
        contentPadding = PaddingValues(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (item.category == ShopCategory.WIDGET_BACKGROUND) 16f / 9f else 1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            if (item.category == ShopCategory.TITLE) {
                // Titles are just text worn next to your name - no artwork to
                // preview, so the tile shows the title itself, styled the same
                // way it'll actually look once equipped (rarity color + glow).
                io.github.arglax.wuwalab.ui.components.TitleBadge(
                    text = item.name,
                    rarity = item.rarity,
                    fontSize = MaterialTheme.typography.titleSmall.fontSize
                )
            } else if (item.category == ShopCategory.AVATAR_BORDER) {
                // Avatar Borders wrap a portrait rather than being artwork of
                // their own, so the tile previews the ring itself around a
                // neutral swatch - exactly the RarityBorderBox treatment it'll
                // get once equipped around the real profile picture.
                io.github.arglax.wuwalab.ui.components.RarityBorderBox(
                    style = item.borderStyle,
                    cornerRadius = 12.dp,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                    )
                }
            } else if (artRes != 0) {
                Image(
                    painter = painterResource(artRes),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // The art hasn't been dropped into res/drawable yet - show a
                // readable placeholder instead of failing to build or crashing.
                Text(
                    "Art coming soon",
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    modifier = Modifier.padding(8.dp)
                )
            }
            if (equipped) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(EmeraldGlow.copy(alpha = 0.9f))
                        .padding(3.dp)
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Equipped", tint = Color.Black, modifier = Modifier.size(14.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            item.name,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = MaterialTheme.typography.labelLarge.fontSize,
            maxLines = 1
        )
        Text(
            item.category.label,
            color = TextMuted,
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
            maxLines = 1
        )
        if (item.category == ShopCategory.TITLE && (item.titleBonusPercent > 0f || item.titleBonusMultiplier > 1f)) {
            // Multiplier titles (e.g. "???"'s x10) must never be shown as if
            // they were just a bigger percent - the x-term is rendered
            // separately so it's clear it multiplies the WHOLE bonused
            // amount, not adds to the percent pool.
            val bonusText = buildString {
                if (item.titleBonusPercent > 0f) append("+${formatShopBonusPercent(item.titleBonusPercent)}%")
                if (item.titleBonusMultiplier > 1f) {
                    if (isNotEmpty()) append(" ")
                    append("\u00d7${formatShopBonusPercent(item.titleBonusMultiplier)}")
                }
            }
            Text(
                bonusText,
                color = EmeraldGlow,
                fontWeight = FontWeight.Bold,
                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_radiant_astrite),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                io.github.arglax.wuwalab.util.formatArgstrites(item.price),
                color = if (owned) TextMuted else VioletGlow,
                fontWeight = FontWeight.Bold,
                fontSize = MaterialTheme.typography.labelLarge.fontSize
            )
        }

        Spacer(Modifier.height(8.dp))

        when {
            owned && equipped -> Button(
                onClick = onUnequip,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = TextPrimary),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Unequip", fontSize = MaterialTheme.typography.labelMedium.fontSize) }

            owned -> Button(
                onClick = onEquip,
                colors = ButtonDefaults.buttonColors(containerColor = VioletGlow, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Equip", fontSize = MaterialTheme.typography.labelMedium.fontSize) }

            else -> Button(
                onClick = onBuy,
                enabled = affordable,
                colors = ButtonDefaults.buttonColors(containerColor = AmberGlow, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (affordable) "Buy" else "Need " + io.github.arglax.wuwalab.util.formatArgstrites(item.price - balance), fontSize = MaterialTheme.typography.labelMedium.fontSize) }
        }

        if (!owned && !affordable) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Short by " + io.github.arglax.wuwalab.util.formatArgstrites(item.price - balance) + " Argstrites.",
                color = CoralGlow,
                fontSize = MaterialTheme.typography.labelSmall.fontSize
            )
        }
    }
}
/** "12" for a whole number, "12.5" for a fraction - never a trailing ".0". Mirrors ProfileHeader's identically-behaved private formatter, kept separate since Shop and Profile are independent UI modules. */
private fun formatShopBonusPercent(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString() else "%.1f".format(value)
