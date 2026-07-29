package io.github.arglax.wuwalab.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * ---------------------------------------------------------------------------
 * App Shop - catalog, ownership and purchase validation
 * ---------------------------------------------------------------------------
 *
 * The shop spends ARGSTRITES only (WuWaLab's own in-app currency from the
 * Daily Sign-In card) - never the game's real Astrites. Those two pools are
 * deliberately kept apart so nothing you buy here can ever eat into the
 * convene budget the Pull Planner is working with.
 *
 * ### Adding your own art
 * Every item below points at a *drawable name*, not a compiled resource id.
 * Drop a PNG/WebP into `res/drawable/` with the matching filename and it
 * appears in the shop automatically - no code change, no rebuild of this file.
 * If the drawable isn't there yet, the tile falls back to a neutral
 * placeholder instead of crashing, so you can ship the shop before the art.
 *
 *   Profile pictures  -> res/drawable/resonator_<name>.png (one per Resonator,
 *                        e.g. resonator_jinhsi.png, resonator_rover_havoc.png)
 *   Widget backgrounds-> res/drawable/shop_widget_bg_01.png ... _06.png
 */
enum class ShopCategory(val label: String, val priceArgstrites: Int) {
    PROFILE_PICTURE("Profile Pictures", 20),
    WIDGET_BACKGROUND("Widget Backgrounds", 30),
    TITLE("Titles", 0),
    // Individually priced (30-100 Argstrites, see AvatarBorderStyle) rather
    // than one flat category price - the same pattern Titles already use.
    AVATAR_BORDER("Avatar Borders", 0)
}

/** Cosmetic rarity tier for titles - drives the badge color (and, for LEGENDARY, a pulsing glow). */
enum class TitleRarity(val label: String, val color: Color) {
    COMMON("Common", Color(0xFFB0B0B0)),      // gray
    UNCOMMON("Uncommon", Color(0xFF52E39A)),  // green
    RARE("Rare", Color(0xFFB07BFF)),          // purple
    EPIC("Epic", Color(0xFFFFD24C)),          // gold
    LEGENDARY("Legendary", Color(0xFFFF3B3B)),// red
    
    MYTHIC("Mythic", Color(0xFFFFFFFF))       // fallback white (animated in UI)
}

/**
 * Cosmetic rarity tier for [ShopCategory.AVATAR_BORDER] items - drives
 * whether the border is a flat static color (RARE/EPIC style) or the
 * rotating rainbow sweep gradient (LEGENDARY_ANIMATED, see
 * `Modifier.animatedSweepBorder` in VisualEffects.kt).
 */
enum class AvatarBorderStyle(val label: String, val price: Int, val colors: List<Color>) {
    COMMON("Common", 30, listOf(Color(0xFFB0B0B0), Color(0xFFB0B0B0))),
    RARE("Rare", 50, listOf(Color(0xFFB07BFF), Color(0xFF6C4FCE))),
    EPIC("Epic", 75, listOf(Color(0xFFFFD24C), Color(0xFFFF9E3D))),
    LEGENDARY_ANIMATED("Legendary", 100, listOf(Color(0xFFFF3B3B), Color(0xFFFFD24C), Color(0xFF52E39A), Color(0xFF4FA6FF), Color(0xFFB07BFF)))
}

data class ShopItem(
    val id: String,
    val name: String,
    val category: ShopCategory,
    val drawableName: String = "",
    val description: String = "",
    // Overrides category.priceArgstrites - used by Titles and Avatar
    // Borders, which each have their own individual price instead of one
    // flat per-category price.
    val priceOverride: Int? = null,
    // True for items only obtainable through a Redeem code - hidden from
    // the shop's Buy grid, but still equippable/ownable once owned.
    val redeemOnly: Boolean = false,
    // Titles only - null for every other category.
    val rarity: TitleRarity? = null,
    // Avatar Borders only - null for every other category.
    val borderStyle: AvatarBorderStyle? = null,
    // Titles only - the permanent, cumulative +% added to the Argstrite
    // Bonus for every title OWNED (equipped or not - see
    // ShopRepository.recomputeTitleBonus). 0 for titles that are purely
    // cosmetic/flavor with no stated bonus.
    val titleBonusPercent: Float = 0f,
    // Titles only - a cumulative MULTIPLIER (not %) stacked on top of every
    // owned title's bonusPercent sum. 1f = no effect. Reserved for
    // over-the-top titles like "???" (x10).
    val titleBonusMultiplier: Float = 1f
) {
    val price: Int get() = priceOverride ?: borderStyle?.price ?: category.priceArgstrites
}

object ShopCatalog {

    val items: List<ShopItem> = listOf(
        // --- Profile pictures - 20 Argstrites each -------------------------
        // One Resonator portrait per playable character/Rover variant.
        ShopItem("pfp_lingyang", "Lingyang Portrait", ShopCategory.PROFILE_PICTURE, "resonator_lingyang", "A Resonator portrait of Lingyang for your profile header."),
        ShopItem("pfp_zhezhi", "Zhezhi Portrait", ShopCategory.PROFILE_PICTURE, "resonator_zhezhi", "A Resonator portrait of Zhezhi for your profile header."),
        ShopItem("pfp_carlotta", "Carlotta Portrait", ShopCategory.PROFILE_PICTURE, "resonator_carlotta", "A Resonator portrait of Carlotta for your profile header."),
        ShopItem("pfp_hiyuki", "Hiyuki Portrait", ShopCategory.PROFILE_PICTURE, "resonator_hiyuki", "A Resonator portrait of Hiyuki for your profile header."),
        ShopItem("pfp_lucilla", "Lucilla Portrait", ShopCategory.PROFILE_PICTURE, "resonator_lucilla", "A Resonator portrait of Lucilla for your profile header."),
        ShopItem("pfp_suisui", "Suisui Portrait", ShopCategory.PROFILE_PICTURE, "resonator_suisui", "A Resonator portrait of Suisui for your profile header."),
        ShopItem("pfp_encore", "Encore Portrait", ShopCategory.PROFILE_PICTURE, "resonator_encore", "A Resonator portrait of Encore for your profile header."),
        ShopItem("pfp_changli", "Changli Portrait", ShopCategory.PROFILE_PICTURE, "resonator_changli", "A Resonator portrait of Changli for your profile header."),
        ShopItem("pfp_brant", "Brant Portrait", ShopCategory.PROFILE_PICTURE, "resonator_brant", "A Resonator portrait of Brant for your profile header."),
        ShopItem("pfp_lupa", "Lupa Portrait", ShopCategory.PROFILE_PICTURE, "resonator_lupa", "A Resonator portrait of Lupa for your profile header."),
        ShopItem("pfp_galbrena", "Galbrena Portrait", ShopCategory.PROFILE_PICTURE, "resonator_galbrena", "A Resonator portrait of Galbrena for your profile header."),
        ShopItem("pfp_momye", "Momye Portrait", ShopCategory.PROFILE_PICTURE, "resonator_momye", "A Resonator portrait of Momye for your profile header."),
        ShopItem("pfp_aemeath", "Aemeath Portrait", ShopCategory.PROFILE_PICTURE, "resonator_aemeath", "A Resonator portrait of Aemeath for your profile header."),
        ShopItem("pfp_denia", "Denia Portrait", ShopCategory.PROFILE_PICTURE, "resonator_denia", "A Resonator portrait of Denia for your profile header."),
        ShopItem("pfp_calcharo", "Calcharo Portrait", ShopCategory.PROFILE_PICTURE, "resonator_calcharo", "A Resonator portrait of Calcharo for your profile header."),
        ShopItem("pfp_yinlin", "Yinlin Portrait", ShopCategory.PROFILE_PICTURE, "resonator_yinlin", "A Resonator portrait of Yinlin for your profile header."),
        ShopItem("pfp_jinhsi", "Jinhsi Portrait", ShopCategory.PROFILE_PICTURE, "resonator_jinhsi", "A Resonator portrait of Jinhsi for your profile header."),
        ShopItem("pfp_xiangli_yao", "Xiangli Yao Portrait", ShopCategory.PROFILE_PICTURE, "resonator_xiangli_yao", "A Resonator portrait of Xiangli Yao for your profile header."),
        ShopItem("pfp_augusta", "Augusta Portrait", ShopCategory.PROFILE_PICTURE, "resonator_augusta", "A Resonator portrait of Augusta for your profile header."),
        ShopItem("pfp_rebecca", "Rebecca Portrait", ShopCategory.PROFILE_PICTURE, "resonator_rebecca", "A Resonator portrait of Rebecca for your profile header."),
        ShopItem("pfp_rover_electro", "Rover Electro Portrait", ShopCategory.PROFILE_PICTURE, "resonator_rover_electro", "A Resonator portrait of Rover Electro for your profile header."),
        ShopItem("pfp_jiyan", "Jiyan Portrait", ShopCategory.PROFILE_PICTURE, "resonator_jiyan", "A Resonator portrait of Jiyan for your profile header."),
        ShopItem("pfp_jianxin", "Jianxin Portrait", ShopCategory.PROFILE_PICTURE, "resonator_jianxin", "A Resonator portrait of Jianxin for your profile header."),
        ShopItem("pfp_rover_aero", "Rover Aero Portrait", ShopCategory.PROFILE_PICTURE, "resonator_rover_aero", "A Resonator portrait of Rover Aero for your profile header."),
        ShopItem("pfp_ciaccona", "Ciaccona Portrait", ShopCategory.PROFILE_PICTURE, "resonator_ciaccona", "A Resonator portrait of Ciaccona for your profile header."),
        ShopItem("pfp_cartethyia", "Cartethyia Portrait", ShopCategory.PROFILE_PICTURE, "resonator_cartethyia", "A Resonator portrait of Cartethyia for your profile header."),
        ShopItem("pfp_iuno", "Iuno Portrait", ShopCategory.PROFILE_PICTURE, "resonator_iuno", "A Resonator portrait of Iuno for your profile header."),
        ShopItem("pfp_qiuyuan", "Qiuyuan Portrait", ShopCategory.PROFILE_PICTURE, "resonator_qiuyuan", "A Resonator portrait of Qiuyuan for your profile header."),
        ShopItem("pfp_sigrika", "Sigrika Portrait", ShopCategory.PROFILE_PICTURE, "resonator_sigrika", "A Resonator portrait of Sigrika for your profile header."),
        ShopItem("pfp_rover_spectro", "Rover Spectro Portrait", ShopCategory.PROFILE_PICTURE, "resonator_rover_spectro", "A Resonator portrait of Rover Spectro for your profile header."),
        ShopItem("pfp_verina", "Verina Portrait", ShopCategory.PROFILE_PICTURE, "resonator_verina", "A Resonator portrait of Verina for your profile header."),
        ShopItem("pfp_shorekeeper", "Shorekeeper Portrait", ShopCategory.PROFILE_PICTURE, "resonator_shorekeeper", "A Resonator portrait of Shorekeeper for your profile header."),
        ShopItem("pfp_phoebe", "Phoebe Portrait", ShopCategory.PROFILE_PICTURE, "resonator_phoebe", "A Resonator portrait of Phoebe for your profile header."),
        ShopItem("pfp_zani", "Zani Portrait", ShopCategory.PROFILE_PICTURE, "resonator_zani", "A Resonator portrait of Zani for your profile header."),
        ShopItem("pfp_chisa", "Chisa Portrait", ShopCategory.PROFILE_PICTURE, "resonator_chisa", "A Resonator portrait of Chisa for your profile header."),
        ShopItem("pfp_lynae", "Lynae Portrait", ShopCategory.PROFILE_PICTURE, "resonator_lynae", "A Resonator portrait of Lynae for your profile header."),
        ShopItem("pfp_luuk_herssen", "Luuk Herssen Portrait", ShopCategory.PROFILE_PICTURE, "resonator_luuk_herssen", "A Resonator portrait of Luuk Herssen for your profile header."),
        ShopItem("pfp_lucy", "Lucy Portrait", ShopCategory.PROFILE_PICTURE, "resonator_lucy", "A Resonator portrait of Lucy for your profile header."),
        ShopItem("pfp_camellya", "Camellya Portrait", ShopCategory.PROFILE_PICTURE, "resonator_camellya", "A Resonator portrait of Camellya for your profile header."),
        ShopItem("pfp_rover_havoc", "Rover Havoc Portrait", ShopCategory.PROFILE_PICTURE, "resonator_rover_havoc", "A Resonator portrait of Rover Havoc for your profile header."),
        ShopItem("pfp_roccia", "Roccia Portrait", ShopCategory.PROFILE_PICTURE, "resonator_roccia", "A Resonator portrait of Roccia for your profile header."),
        ShopItem("pfp_cantarella", "Cantarella Portrait", ShopCategory.PROFILE_PICTURE, "resonator_cantarella", "A Resonator portrait of Cantarella for your profile header."),
        ShopItem("pfp_phrolova", "Phrolova Portrait", ShopCategory.PROFILE_PICTURE, "resonator_phrolova", "A Resonator portrait of Phrolova for your profile header."),
        ShopItem("pfp_sanhua", "Sanhua Portrait", ShopCategory.PROFILE_PICTURE, "resonator_sanhua", "A Resonator portrait of Sanhua for your profile header."),
        ShopItem("pfp_baizhi", "Baizhi Portrait", ShopCategory.PROFILE_PICTURE, "resonator_baizhi", "A Resonator portrait of Baizhi for your profile header."),
        ShopItem("pfp_youhu", "Youhu Portrait", ShopCategory.PROFILE_PICTURE, "resonator_youhu", "A Resonator portrait of Youhu for your profile header."),
        ShopItem("pfp_chixia", "Chixia Portrait", ShopCategory.PROFILE_PICTURE, "resonator_chixia", "A Resonator portrait of Chixia for your profile header."),
        ShopItem("pfp_mortefi", "Mortefi Portrait", ShopCategory.PROFILE_PICTURE, "resonator_mortefi", "A Resonator portrait of Mortefi for your profile header."),
        ShopItem("pfp_yuanwu", "Yuanwu Portrait", ShopCategory.PROFILE_PICTURE, "resonator_yuanwu", "A Resonator portrait of Yuanwu for your profile header."),
        ShopItem("pfp_buling", "Buling Portrait", ShopCategory.PROFILE_PICTURE, "resonator_buling", "A Resonator portrait of Buling for your profile header."),
        ShopItem("pfp_yangyang", "Yangyang Portrait", ShopCategory.PROFILE_PICTURE, "resonator_yangyang", "A Resonator portrait of Yangyang for your profile header."),
        ShopItem("pfp_aalto", "Aalto Portrait", ShopCategory.PROFILE_PICTURE, "resonator_aalto", "A Resonator portrait of Aalto for your profile header."),
        ShopItem("pfp_lumi", "Lumi Portrait", ShopCategory.PROFILE_PICTURE, "resonator_lumi", "A Resonator portrait of Lumi for your profile header."),
        ShopItem("pfp_taoqi", "Taoqi Portrait", ShopCategory.PROFILE_PICTURE, "resonator_taoqi", "A Resonator portrait of Taoqi for your profile header."),
        ShopItem("pfp_danjin", "Danjin Portrait", ShopCategory.PROFILE_PICTURE, "resonator_danjin", "A Resonator portrait of Danjin for your profile header."),

        // --- Widget backgrounds - 30 Argstrites each ----------------------
        ShopItem("wbg_01", "Widget Skin I", ShopCategory.WIDGET_BACKGROUND, "shop_widget_bg_01", "Artwork behind your home-screen widget."),
        ShopItem("wbg_02", "Widget Skin II", ShopCategory.WIDGET_BACKGROUND, "shop_widget_bg_02", "A second widget backdrop."),
        ShopItem("wbg_03", "Widget Skin III", ShopCategory.WIDGET_BACKGROUND, "shop_widget_bg_03", "A third widget backdrop."),
        ShopItem("wbg_04", "Widget Skin IV", ShopCategory.WIDGET_BACKGROUND, "shop_widget_bg_04", "A fourth widget backdrop."),
        ShopItem("wbg_05", "Widget Skin V", ShopCategory.WIDGET_BACKGROUND, "shop_widget_bg_05", "A fifth widget backdrop."),
        ShopItem("wbg_06", "Widget Skin VI", ShopCategory.WIDGET_BACKGROUND, "shop_widget_bg_06", "A sixth widget backdrop."),

        // --- Titles - individually priced, shown next to your name --------
        // Every title's stated bonus is PERMANENT and CUMULATIVE the moment
        // it's owned (equipped or not) - see ShopRepository.recomputeTitleBonus,
        // which sums titleBonusPercent across every owned title and folds
        // that into the same Argstrite Bonus % achievements feed.
        ShopItem("title_argstrite_miner", "Argstrite Miner", ShopCategory.TITLE, priceOverride = 150, description = "Proof you've been grinding for Argstrites. +2% Bonus", rarity = TitleRarity.UNCOMMON, titleBonusPercent = 2f),
        ShopItem("title_rich_af", "Rich AF", ShopCategory.TITLE, priceOverride = 999, description = "For those who never check the price tag. +15% Bonus", rarity = TitleRarity.EPIC, titleBonusPercent = 15f),
        ShopItem("title_scrap_collector", "Scrap Collector", ShopCategory.TITLE, priceOverride = 0, description = "Free - everyone starts somewhere.", rarity = TitleRarity.COMMON),
        ShopItem("title_argstrite_investor", "Argstrite Investor", ShopCategory.TITLE, priceOverride = 500, description = "You invested now here's +5% bonus", rarity = TitleRarity.RARE, titleBonusPercent = 5f),
        ShopItem("title_yaoi_lover", "YAOI Lover", ShopCategory.TITLE, priceOverride = 999, description = "Okay u love it then here's 10% bonus", rarity = TitleRarity.EPIC, titleBonusPercent = 10f),
        ShopItem("title_yuri_lover", "YURI Lover", ShopCategory.TITLE, priceOverride = 999, description = "Okay u love it then here's 10% bonus", rarity = TitleRarity.EPIC, titleBonusPercent = 10f),
        ShopItem("title_wuwalab_hater", "WuWaLab Hater", ShopCategory.TITLE, priceOverride = 1, description = "Fine. Get on with it. +1% Bonus", rarity = TitleRarity.COMMON, titleBonusPercent = 1f),
        ShopItem("title_free_lingyang", "Free Lingyang", ShopCategory.TITLE, priceOverride = 0, description = "Why don't you get it? It's free. +5% bonus", rarity = TitleRarity.COMMON, titleBonusPercent = 5f),
        ShopItem("title_tycoon", "Tycoon", ShopCategory.TITLE, priceOverride = 4999, description = "You are so rich you're getting richer. +30% Bonus", rarity = TitleRarity.LEGENDARY, titleBonusPercent = 30f),
        ShopItem("title_grand_tycoon", "Grand Tycoon", ShopCategory.TITLE, priceOverride = 9999, description = "I guess even the rich have their limits? +50% Bonus", rarity = TitleRarity.LEGENDARY, titleBonusPercent = 50f),
        ShopItem("title_mystery", "???", ShopCategory.TITLE, priceOverride = 99999, description = "Time to talk to the developer. All earnings x10 on top of bonuses.", rarity = TitleRarity.MYTHIC, titleBonusMultiplier = 10f),

        // --- Redeem-only titles - never shown in the Buy grid, only granted
        // by their matching code on the Redeem screen ------------------------
        ShopItem("title_tester", "Tester", ShopCategory.TITLE, priceOverride = 0, redeemOnly = true, description = "Unlocked via a Redeem code. Well test your way with this.", rarity = TitleRarity.RARE),
        // Supporter's +20% is granted through AchievementsRepository's
        // hard-coded SUPPORTER_ACHIEVEMENT (not titleBonusPercent here), to
        // avoid double-counting the same +20% from two sources.
        ShopItem("title_supporter", "WuWaLab Supporter", ShopCategory.TITLE, priceOverride = 0, redeemOnly = true, description = "Unlocked via a Redeem code. You supported an indie dev app and this is what you got?! +20% Bonus", rarity = TitleRarity.LEGENDARY),
        // The ultimate redeem-only flex: one code grants +100,000 Argstrites
        // flat AND a permanent +500% Bonus the instant it's owned.
        ShopItem("title_argl4x_best", "Argl4xTh3Best", ShopCategory.TITLE, priceOverride = 0, redeemOnly = true, description = "Unlocked via a Redeem code. The best title money can't buy. +100,000 Argstrites and +500% Bonus.", rarity = TitleRarity.MYTHIC, titleBonusPercent = 500f),

        // --- Avatar Borders - individually priced 30-100 Argstrites by rarity, wrap your equipped profile picture ---
        ShopItem("border_slate", "Slate Ring", ShopCategory.AVATAR_BORDER, description = "A clean, understated gray ring around your portrait.", borderStyle = AvatarBorderStyle.COMMON),
        ShopItem("border_verdant", "Verdant Ring", ShopCategory.AVATAR_BORDER, description = "A common mossy-green ring.", borderStyle = AvatarBorderStyle.COMMON),
        ShopItem("border_amethyst", "Amethyst Halo", ShopCategory.AVATAR_BORDER, description = "A rare violet gradient halo.", borderStyle = AvatarBorderStyle.RARE),
        ShopItem("border_azure", "Azure Halo", ShopCategory.AVATAR_BORDER, description = "A rare deep-blue gradient halo.", borderStyle = AvatarBorderStyle.RARE),
        ShopItem("border_gilded", "Gilded Crest", ShopCategory.AVATAR_BORDER, description = "An epic gold-to-ember gradient crest.", borderStyle = AvatarBorderStyle.EPIC),
        ShopItem("border_prism", "Prism Aurora", ShopCategory.AVATAR_BORDER, description = "A legendary rotating rainbow sweep - the flashiest ring in the shop.", borderStyle = AvatarBorderStyle.LEGENDARY_ANIMATED),
        ShopItem("border_starlight", "Starlight Aurora", ShopCategory.AVATAR_BORDER, description = "A legendary rotating rainbow sweep with a cooler palette.", borderStyle = AvatarBorderStyle.LEGENDARY_ANIMATED)
    )

    fun byId(id: String?): ShopItem? = items.firstOrNull { it.id == id }

    /** Resolves a drawable name to a resource id, or 0 when the art hasn't been added yet. */
    fun drawableRes(context: Context, drawableName: String): Int =
        runCatching {
            context.resources.getIdentifier(drawableName, "drawable", context.packageName)
        }.getOrDefault(0)
}

/** How the shop grid is ordered. */
enum class ShopSort(val label: String) {
    PRICE_LOW_HIGH("Price: Low to High"),
    PRICE_HIGH_LOW("Price: High to Low"),
    NAME("Name (A-Z)"),
    OWNED_LAST("Unowned First")
}

/** Persisted shop state: what you own and what you currently have equipped. */
data class ShopState(
    val owned: Set<String> = emptySet(),
    val equippedAvatarId: String? = null,
    val equippedWidgetBgId: String? = null,
    val equippedTitleId: String? = null,
    val equippedAvatarBorderId: String? = null
)

/**
 * A plain JSON file store (same approach as [OverlayPrefs]) rather than
 * DataStore, for one specific reason: the home-screen widget needs to read the
 * equipped background *synchronously*, outside a coroutine. Keeping an
 * in-memory [StateFlow] alongside the file gives Compose its reactivity too,
 * so the shop grid, the profile header and the widget all see the same value.
 */
object ShopStore {

    private val _state = MutableStateFlow(ShopState())
    val state: StateFlow<ShopState> = _state.asStateFlow()

    private var loaded = false

    private fun file(context: Context) = File(context.filesDir, "shop_state.json")

    @Synchronized
    fun ensureLoaded(context: Context): ShopState {
        if (!loaded) {
            _state.value = readFile(context)
            loaded = true
        }
        return _state.value
    }

    /** Synchronous read that always hits the file - used by the widget process. */
    fun readFile(context: Context): ShopState = try {
        val f = file(context)
        if (!f.exists()) ShopState() else {
            val json = JSONObject(f.readText())
            val arr = json.optJSONArray("owned") ?: JSONArray()
            ShopState(
                owned = (0 until arr.length()).map { arr.getString(it) }.toSet(),
                equippedAvatarId = json.optString("equippedAvatarId", "").ifBlank { null },
                equippedWidgetBgId = json.optString("equippedWidgetBgId", "").ifBlank { null },
                equippedTitleId = json.optString("equippedTitleId", "").ifBlank { null },
                equippedAvatarBorderId = json.optString("equippedAvatarBorderId", "").ifBlank { null }
            )
        }
    } catch (_: Exception) {
        ShopState()
    }

    @Synchronized
    fun update(context: Context, transform: (ShopState) -> ShopState) {
        val next = transform(ensureLoaded(context))
        _state.value = next
        loaded = true
        runCatching {
            val json = JSONObject().apply {
                put("owned", JSONArray().also { arr -> next.owned.forEach { arr.put(it) } })
                put("equippedAvatarId", next.equippedAvatarId ?: "")
                put("equippedWidgetBgId", next.equippedWidgetBgId ?: "")
                put("equippedTitleId", next.equippedTitleId ?: "")
                put("equippedAvatarBorderId", next.equippedAvatarBorderId ?: "")
            }
            file(context).writeText(json.toString())
        }
    }
}

/** The outcome of tapping "Buy". Every failure mode is a value, never an exception. */
sealed class PurchaseResult {
    data class Success(val item: ShopItem, val remainingArgstrites: Int) : PurchaseResult()
    data class NotEnoughArgstrites(val needed: Int, val balance: Int) : PurchaseResult()
    data class AlreadyOwned(val item: ShopItem) : PurchaseResult()
    data object Unavailable : PurchaseResult()
}

class ShopRepository(
    private val context: Context,
    private val wuwaRepo: WuwaRepository
) {

    val stateFlow: StateFlow<ShopState> = ShopStore.state

    fun refresh() {
        ShopStore.ensureLoaded(context)
    }

    fun isOwned(itemId: String): Boolean = ShopStore.ensureLoaded(context).owned.contains(itemId)

    /**
     * Buys [item] with Argstrites.
     *
     * Two independent guards stand between the player and a negative balance:
     * the UI disables the Buy button when they can't afford it, and
     * [WuwaRepository.trySpendRadiantAstrite] re-checks the balance inside the
     * same atomic write that deducts it. If the second check fails, nothing is
     * deducted and nothing is unlocked.
     */
    suspend fun purchase(item: ShopItem): PurchaseResult {
        ShopStore.ensureLoaded(context)
        if (isOwned(item.id)) return PurchaseResult.AlreadyOwned(item)

        val balanceBefore = wuwaRepo.getRadiantAstriteOnce()
        if (item.price > balanceBefore) {
            return PurchaseResult.NotEnoughArgstrites(item.price, balanceBefore)
        }
        // A price of 0 (e.g. the free "Scrap Collector" title) has nothing to
        // deduct - trySpendRadiantAstrite(0) always returns false by design
        // (it rejects non-positive amounts), so free items must skip the
        // spend call entirely rather than being treated as a failed purchase.
        if (item.price > 0 && !wuwaRepo.trySpendRadiantAstrite(item.price)) {
            // Lost a race with another deduction - balance moved underneath us.
            return PurchaseResult.NotEnoughArgstrites(item.price, wuwaRepo.getRadiantAstriteOnce())
        }

        ShopStore.update(context) { current ->
            current.copy(
                owned = current.owned + item.id,
                // First purchase in a category equips itself, so a new buy is
                // never invisible - the player sees it applied immediately.
                equippedAvatarId = if (item.category == ShopCategory.PROFILE_PICTURE && current.equippedAvatarId == null) item.id else current.equippedAvatarId,
                equippedWidgetBgId = if (item.category == ShopCategory.WIDGET_BACKGROUND && current.equippedWidgetBgId == null) item.id else current.equippedWidgetBgId,
                equippedTitleId = if (item.category == ShopCategory.TITLE && current.equippedTitleId == null) item.id else current.equippedTitleId,
                equippedAvatarBorderId = if (item.category == ShopCategory.AVATAR_BORDER && current.equippedAvatarBorderId == null) item.id else current.equippedAvatarBorderId
            )
        }
        if (item.category == ShopCategory.TITLE) recomputeTitleBonus()
        return PurchaseResult.Success(item, wuwaRepo.getRadiantAstriteOnce())
    }

    /**
     * Sums [ShopItem.titleBonusPercent] across every OWNED title (equipped
     * or not - owning it is what counts, exactly like Achievements) and
     * multiplies the total by every owned title's [ShopItem.titleBonusMultiplier]
     * (e.g. the "???" title's x10), then pushes the result into
     * [WuwaRepository] so every future Argstrite earning reflects it
     * immediately - not just the Shop/Profile UI.
     */
    suspend fun recomputeTitleBonus() {
        val owned = ShopStore.ensureLoaded(context).owned
        val ownedTitles = ShopCatalog.items.filter { it.category == ShopCategory.TITLE && owned.contains(it.id) }
        val percent = ownedTitles.sumOf { it.titleBonusPercent.toDouble() }.toFloat()
        // ADDITIVE, never compounding - a x2 title next to a x5 title is x7,
        // not x10. See BonusMath.combineMultipliers.
        val multiplier = BonusMath.combineMultipliers(ownedTitles.map { it.titleBonusMultiplier })
        wuwaRepo.setTitleBonus(percent, multiplier)
    }

    /** Equips an owned item. Silently no-ops for anything not owned. */
    fun equip(item: ShopItem) {
        if (!isOwned(item.id)) return
        ShopStore.update(context) { current ->
            when (item.category) {
                ShopCategory.PROFILE_PICTURE -> current.copy(equippedAvatarId = item.id)
                ShopCategory.WIDGET_BACKGROUND -> current.copy(equippedWidgetBgId = item.id)
                ShopCategory.TITLE -> current.copy(equippedTitleId = item.id)
                ShopCategory.AVATAR_BORDER -> current.copy(equippedAvatarBorderId = item.id)
            }
        }
    }

    /** Unequips a category, falling back to the bundled free art (or no title). */
    fun unequip(category: ShopCategory) {
        ShopStore.update(context) { current ->
            when (category) {
                ShopCategory.PROFILE_PICTURE -> current.copy(equippedAvatarId = null)
                ShopCategory.WIDGET_BACKGROUND -> current.copy(equippedWidgetBgId = null)
                ShopCategory.TITLE -> current.copy(equippedTitleId = null)
                ShopCategory.AVATAR_BORDER -> current.copy(equippedAvatarBorderId = null)
            }
        }
    }

    /**
     * Grants ownership of [itemId] without spending anything - used by Redeem
     * codes. Auto-equips it if nothing is equipped yet in that category, same
     * as a normal purchase.
     */
    fun grantOwnership(itemId: String) {
        val item = ShopCatalog.byId(itemId) ?: return
        ShopStore.update(context) { current ->
            if (current.owned.contains(itemId)) return@update current
            current.copy(
                owned = current.owned + itemId,
                equippedAvatarId = if (item.category == ShopCategory.PROFILE_PICTURE && current.equippedAvatarId == null) itemId else current.equippedAvatarId,
                equippedWidgetBgId = if (item.category == ShopCategory.WIDGET_BACKGROUND && current.equippedWidgetBgId == null) itemId else current.equippedWidgetBgId,
                equippedTitleId = if (item.category == ShopCategory.TITLE && current.equippedTitleId == null) itemId else current.equippedTitleId,
                equippedAvatarBorderId = if (item.category == ShopCategory.AVATAR_BORDER && current.equippedAvatarBorderId == null) itemId else current.equippedAvatarBorderId
            )
        }
    }

    /** Same as [grantOwnership] but also recomputes the cumulative Title Bonus - use this from suspend call sites (e.g. Redeem) instead of the sync version. */
    suspend fun grantOwnershipSuspend(itemId: String) {
        grantOwnership(itemId)
        if (ShopCatalog.byId(itemId)?.category == ShopCategory.TITLE) recomputeTitleBonus()
    }
}

/**
 * Resource lookups for whatever is currently equipped. Deliberately
 * synchronous and dependency-free so the Glance widget can call it straight
 * from its composable without plumbing a coroutine through.
 */
object ShopArt {

    /** Every item the player owns in [category] - bought OR redeemed - regardless of whether it's shown in the Buy grid. Used by pickers (Edit Profile's avatar/title lists) that need the FULL owned set, unlike the Shop's Buy grid which hides redeem-only items. */
    fun ownedItemsInCategory(context: Context, category: ShopCategory): List<ShopItem> {
        val owned = ShopStore.ensureLoaded(context).owned
        return ShopCatalog.items.filter { it.category == category && owned.contains(it.id) }
    }

    /** The equipped shop avatar's drawable, or 0 when none is equipped/available. */
    fun equippedAvatarRes(context: Context): Int {
        val id = ShopStore.ensureLoaded(context).equippedAvatarId ?: return 0
        val item = ShopCatalog.byId(id) ?: return 0
        return ShopCatalog.drawableRes(context, item.drawableName)
    }

    /** The full equipped title item (name + rarity), or null when none is equipped. */
    fun equippedTitleItem(context: Context): ShopItem? {
        val id = ShopStore.ensureLoaded(context).equippedTitleId ?: return null
        return ShopCatalog.byId(id)
    }

    /** The equipped title's display label, or null when none is equipped. */
    fun equippedTitleLabel(context: Context): String? = equippedTitleItem(context)?.name

    /** The equipped avatar border's full item (for its [AvatarBorderStyle]), or null when none is equipped. */
    fun equippedAvatarBorderItem(context: Context): ShopItem? {
        val id = ShopStore.ensureLoaded(context).equippedAvatarBorderId ?: return null
        return ShopCatalog.byId(id)
    }

    /**
     * The widget's background: the equipped shop skin when there is one and the
     * art actually exists, otherwise the bundled default (`widget_bg`).
     */
    fun widgetBackgroundRes(context: Context): Int {
        val fallback = ShopCatalog.drawableRes(context, "widget_bg")
        val id = ShopStore.readFile(context).equippedWidgetBgId ?: return fallback
        val item = ShopCatalog.byId(id) ?: return fallback
        val res = ShopCatalog.drawableRes(context, item.drawableName)
        return if (res != 0) res else fallback
    }
}
