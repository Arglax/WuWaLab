package io.github.arglax.wuwalab

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.github.arglax.wuwalab.data.*
import io.github.arglax.wuwalab.notification.LuniteReminderScheduler
import io.github.arglax.wuwalab.ui.astrite.AstriteScreen
import io.github.arglax.wuwalab.ui.components.AstriteBarChart
import io.github.arglax.wuwalab.ui.components.ClaimArgstritesButton
import io.github.arglax.wuwalab.ui.components.DailySignInCard
import io.github.arglax.wuwalab.ui.components.DailySignInClaimedDialog
import io.github.arglax.wuwalab.ui.components.EditProfileDialog
import io.github.arglax.wuwalab.ui.components.GlassCard
import io.github.arglax.wuwalab.ui.components.LunitePassCard
import io.github.arglax.wuwalab.ui.components.LunitePassDialog
import io.github.arglax.wuwalab.ui.components.NotifyMeDialog
import io.github.arglax.wuwalab.ui.components.ProfileHeader
import io.github.arglax.wuwalab.ui.components.ProfileStatsDialog
import io.github.arglax.wuwalab.ui.components.SettingsDialog
import io.github.arglax.wuwalab.ui.components.StatusPill
import io.github.arglax.wuwalab.ui.components.TooltipIcon
import io.github.arglax.wuwalab.ui.components.UpdateAvailableDialog
import io.github.arglax.wuwalab.ui.components.WelcomeDialog
import io.github.arglax.wuwalab.ui.components.pulsingGlow
import io.github.arglax.wuwalab.ui.components.AppEventBoostCard
import io.github.arglax.wuwalab.ui.matrix.EisenhowerMatrixScreen
import io.github.arglax.wuwalab.ui.matrixwidget.MatrixWidgetSettingsScreen
import io.github.arglax.wuwalab.ui.economy.AstriteTrackerHost
import io.github.arglax.wuwalab.ui.planner.GachaPlannerScreen
import io.github.arglax.wuwalab.ui.shop.ShopScreen
import io.github.arglax.wuwalab.ui.widgetstudio.WidgetStudioScreen
import io.github.arglax.wuwalab.widget.EXTRA_NAV_PAGE
import io.github.arglax.wuwalab.util.rememberConfirmFeedback
import io.github.arglax.wuwalab.util.rememberTapFeedback
import io.github.arglax.wuwalab.ui.theme.*
import io.github.arglax.wuwalab.ui.todo.TodoScreen
import io.github.arglax.wuwalab.update.UpdateCheckResult
import io.github.arglax.wuwalab.update.UpdateChecker
import io.github.arglax.wuwalab.widget.WuwaWidget
import io.github.arglax.wuwalab.work.RefreshWorker
import kotlinx.coroutines.flow.first
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    companion object {
        private const val NOTIF_ID_PENDING_ARGSTRITES = 9100
    }

    // App-level permission flow: POST_NOTIFICATIONS is a plain runtime
    // permission on API 33+, so ask through the standard launcher. (The
    // SYSTEM_ALERT_WINDOW special permission can't be requested this way -
    // OverlayScreen owns that flow, with a fallback button that routes the
    // user to the Android Settings page if they dismissed/denied it.)
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional feature - no hard gate */ }

    /**
     * A widget tap while the app is already running should still land on the
     * page the user picked, not wherever they left off.
     */
    /**
     * The player leaving the app is exactly the moment a "you have Argstrites
     * waiting" nudge is useful - once they're back inside the app the pending
     * counter/claim button on the header already does the job, so this only
     * needs to fire on the way out.
     */
    override fun onStop() {
        super.onStop()
        val repo = io.github.arglax.wuwalab.data.WuwaRepository(applicationContext)
        lifecycleScope.launch {
            val pending = repo.pendingArgstriteFlow.first()
            if (pending > 0) {
                io.github.arglax.wuwalab.notification.NotificationUtils.notify(
                    context = applicationContext,
                    channelId = io.github.arglax.wuwalab.notification.CHANNEL_ID_ALERTS,
                    id = NOTIF_ID_PENDING_ARGSTRITES,
                    title = "Argstrites waiting to be claimed",
                    text = "You have $pending unclaimed Argstrite" + (if (pending == 1) "" else "s") + " - tap to claim them.",
                    header = "WuWaLab · Argstrites"
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val request = PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "wuwa_refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )

        // Set by the widget's tap chooser; null on a normal launch.
        val navPageKey = intent?.getStringExtra(EXTRA_NAV_PAGE)

        setContent {
            WuWaLabTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackgroundGradient)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .background(HeroGlowGradient)
                    )
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent
                    ) { innerPadding ->
                        RootScreen(
                            context = applicationContext,
                            startPageKey = navPageKey,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Time-of-day greeting shown at the top of the Dashboard, based on the
 * device's LOCAL time (not server time):
 *   5:00 AM - 11:59 AM  -> "Good Morning, <name> !"
 *   12:00 PM - 5:59 PM  -> "Good Afternoon, <name>, "
 *   6:00 PM - 4:59 AM   -> "Good Evening, <name>, "
 * [epochMs] is passed in (rather than reading System.currentTimeMillis()
 * directly) so this stays reactive to the Dashboard's existing once-a-minute
 * tick instead of freezing at first composition.
 */
private fun greetingFor(playerName: String, epochMs: Long): String {
    val hour = java.time.Instant.ofEpochMilli(epochMs)
        .atZone(java.time.ZoneId.systemDefault())
        .hour
    val name = playerName.ifBlank { "Rover" }
    return when (hour) {
        in 5..11 -> "Good Morning, $name !"
        in 12..17 -> "Good Afternoon, $name,"
        else -> "Good Evening, $name,"
    }
}

// Sample data so the Events section never looks empty during development.
private fun sampleEvents(): List<WuwaEvent> {
    val now = System.currentTimeMillis()
    val day = TimeUnit.DAYS.toMillis(1)
    return listOf(
        WuwaEvent(
            id = "sample_upcoming",
            name = "Convene: Changli Rerun",
            startEpochMs = now + 2 * day,
            endEpochMs = now + 23 * day,
            bannerImage = "convene_changli_bg"
        ),
        WuwaEvent(
            id = "sample_live",
            name = "Tower of Adversity: Illusive Realm",
            startEpochMs = now - 5 * day,
            endEpochMs = now + 2 * day + TimeUnit.HOURS.toMillis(6),
            bannerImage = "tower_illusive_realm_bg"
        ),
        WuwaEvent(
            id = "sample_ended",
            name = "Voidrift: Season 3",
            startEpochMs = now - 20 * day,
            endEpochMs = now - 1 * day,
            bannerImage = "voidrift_season3_bg"
        )
    )
}

private enum class EventTab(val label: String, val accent: Color) {
    LIVE("Live", EmeraldGlow),
    ENDED("Ended", TextMuted)
}

/** Which direction the events list is sorted by remaining time. */
private enum class EventSort(val label: String) {
    SOONEST("Time Left ↑"),
    LATEST("Time Left ↓")
}

/** Border color by event category - independent of Live/Ended tab. */
private fun eventCategoryAccent(eventType: String?): Color = when (eventType?.lowercase()) {
    "leisure" -> EmeraldGlow // green
    "farming" -> CyanGlow // blue
    "combat" -> CoralGlow // red
    else -> AmberGlow // yellow, for everything else (convene, featured, permanent, unset...)
}

/**
 * The swipeable top-level pages, in pager order. Swipe left/right anywhere
 * or tap a tab - the tab bar and the pager stay in lockstep both ways.
 */
/**
 * Top-level sections. Grouping the pages keeps the tab strip short: you pick
 * a group first, and only that group's pages show underneath. Nothing is
 * hidden - there are simply fewer things competing for the same row.
 */
private enum class NavGroup(val label: String) {
    HOME("Home"),
    ECONOMY("Economy"),
    PLANNING("Planning"),
    EARN("Earn"),
    INVENTORY("Shop"),
    EXTRAS("Extras")
}

/**
 * The swipeable top-level pages, in pager order. Swipe left/right anywhere
 * or tap a tab - the tab bar and the pager stay in lockstep both ways.
 */
private enum class RootPage(val label: String, val group: NavGroup) {
    DASHBOARD("Dashboard", NavGroup.HOME),
    ASTRITES("Astrite Tracker", NavGroup.ECONOMY),
    MATRIX("Matrix", NavGroup.PLANNING),
    TODO_LIST("To-Do", NavGroup.PLANNING),
    MATRIX_WIDGET("Matrix Widget", NavGroup.PLANNING),
    PLANNER("Pull Planner", NavGroup.PLANNING),
    EARN_QUIZ("Earn", NavGroup.EARN),
    BANK("Bank", NavGroup.EARN),
    REDEEM("Redeem", NavGroup.EARN),
    SHOP("App Shop", NavGroup.INVENTORY),
    OVERLAY("Overlay", NavGroup.EXTRAS),
    WIDGET("Widget", NavGroup.EXTRAS),
    PROFILE_STUDIO("Profile Studio", NavGroup.EXTRAS);

    companion object {
        fun pagesIn(group: NavGroup): List<RootPage> = entries.filter { it.group == group }
    }
}

@Composable
fun RootScreen(
    context: android.content.Context,
    // Page to open on, when the app was launched from the widget's chooser.
    startPageKey: String? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val repo = remember { WuwaRepository(context) }
    val astriteRepo = remember { AstriteRepository(context) }
    val luniteRepo = remember { LuniteRepository(context, astriteRepo) }
    val profileRepo = remember { ProfileRepository(context) }
    val plannerRepo = remember { PlannerRepository(context, astriteRepo) }
    val todoRepo = remember { TodoRepository(context) }
    val economyRepo = remember { EconomyRepository(context) }
    val shopRepo = remember { ShopRepository(context, repo) }
    val redeemRepo = remember { io.github.arglax.wuwalab.data.RedeemRepository(context, repo, shopRepo) }
    val earnRepo = remember { io.github.arglax.wuwalab.data.EarnRepository(context, repo) }
    val bankRepo = remember { io.github.arglax.wuwalab.data.BankRepository(context, repo) }
    val achievementsRepo = remember { io.github.arglax.wuwalab.data.AchievementsRepository(context, repo, redeemRepo) }
    LaunchedEffect(Unit) { achievementsRepo.checkSupporterUnlock() }
    LaunchedEffect(Unit) { shopRepo.recomputeTitleBonus() }
    val widgetStudioRepo = remember { WidgetStudioRepository(context, repo) }
    val profileStudioRepo = remember { ProfileStudioRepository(context, repo, profileRepo) }

    // --- Centralized, reactive state. ---
    // These flows are the single source of truth: the Profile header, the
    // Dashboard cards AND the Gacha Planner all collect the SAME data, so a
    // spend logged in the planner (or from the overlay bubble) updates every
    // page in the same frame. No more one-shot getXOnce() snapshots up here.
    val astriteEntries by astriteRepo.entriesFlow.collectAsState(initial = emptyList())
    val profile by profileRepo.profileFlow.collectAsState(initial = WuwaProfile())
    val lifetimeAstrites = remember(astriteEntries) { AstriteStats.totalGathered(astriteEntries) }
    val radiantAstrites by repo.radiantAstriteFlow.collectAsState(initial = 0)
    // Earnings-only average, so a heavy convene week can never make the
    // planner's pace estimate read as negative.
    val dailyAverageEarn = remember(astriteEntries) { AstriteStats.dailyAverage(astriteEntries, 30) }
    val shopState by shopRepo.stateFlow.collectAsState()
    val shopAvatarRes = remember(shopState.equippedAvatarId) { ShopArt.equippedAvatarRes(context) }
    val equippedTitleLabel = remember(shopState.equippedTitleId) { ShopArt.equippedTitleLabel(context) }
    val equippedTitleRarity = remember(shopState.equippedTitleId) { ShopArt.equippedTitleItem(context)?.rarity }
    val equippedAvatarBorder = remember(shopState.equippedAvatarBorderId) { ShopArt.equippedAvatarBorderItem(context)?.borderStyle }
    // Hoisted out of the Edit Profile dialog's `if` block - the Profile
    // summary dialog needs the full owned-titles list too, so it can show
    // every title the player owns, not just whichever one is equipped.
    val ownedTitles = remember(shopState.owned) {
        io.github.arglax.wuwalab.data.ShopArt.ownedItemsInCategory(context, io.github.arglax.wuwalab.data.ShopCategory.TITLE)
    }

    val widgetStudioState by widgetStudioRepo.stateFlow.collectAsState()

    LaunchedEffect(Unit) {
        shopRepo.refresh()
        widgetStudioRepo.refresh()
    }

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showProfileStatsDialog by remember { mutableStateOf(false) }

    // --- "How WuWaLab Works" startup dialog ---
    // Defaults to false (hidden) until the DataStore check below resolves,
    // so there's no flash-of-dialog on every single launch while we read
    // the "hidden forever" flag - it only appears once we've confirmed the
    // user hasn't already dismissed it for good.
    var showWelcomeDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        showWelcomeDialog = !repo.isWelcomeDialogHidden()
    }

    if (showWelcomeDialog) {
        WelcomeDialog(
            onDismiss = { showWelcomeDialog = false },
            onHideForever = {
                scope.launch { repo.setWelcomeDialogHiddenForever() }
                showWelcomeDialog = false
            }
        )
    }

    LaunchedEffect(Unit) {
        if (luniteRepo.isEnabledOnce()) {
            LuniteReminderScheduler.rescheduleAll(context, luniteRepo)
        }
    }

    // --- Update checker ---
    val currentVersion = "v" + BuildConfig.VERSION_NAME
    var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val result = UpdateChecker.check(currentVersion)
        if (result is UpdateCheckResult.Available) {
            val skipped = repo.getSkippedUpdateTag()
            if (skipped != result.info.tagName) {
                updateResult = result
            }
        }
        // UpToDate / Error: say nothing - this is a background check, not
        // something worth interrupting the user over when there's no update.
    }

    (updateResult as? UpdateCheckResult.Available)?.let { available ->
        UpdateAvailableDialog(
            info = available.info,
            currentVersion = currentVersion,
            downloading = downloading,
            downloadProgress = downloadProgress,
            onDismiss = { updateResult = null },
            onSkipVersion = {
                scope.launch { repo.setSkippedUpdateTag(available.info.tagName) }
                updateResult = null
            },
            onUpdateNow = {
                val apkUrl = available.info.apkDownloadUrl ?: return@UpdateAvailableDialog
                downloading = true
                scope.launch {
                    try {
                        UpdateChecker.downloadAndInstall(context, apkUrl) { progress ->
                            downloadProgress = progress
                        }
                    } catch (_: Exception) {
                        // Download/install failed silently from the user's view here -
                        // falling back to the release page is the safe recovery path.
                        UpdateChecker.openReleasePage(context, available.info.htmlUrl)
                    } finally {
                        downloading = false
                        updateResult = null
                    }
                }
            },
            onViewOnGitHub = {
                UpdateChecker.openReleasePage(context, available.info.htmlUrl)
                updateResult = null
            }
        )
    }

    if (showEditProfileDialog) {
        val ownedShopAvatars = remember(shopState.owned) {
            io.github.arglax.wuwalab.data.ShopArt.ownedItemsInCategory(context, io.github.arglax.wuwalab.data.ShopCategory.PROFILE_PICTURE)
        }
        EditProfileDialog(
            initial = profile,
            onDismiss = { showEditProfileDialog = false },
            onSave = { ign, unionLevel ->
                // No manual re-read needed anywhere below: profileFlow emits
                // the change and every collector recomposes on its own.
                scope.launch { profileRepo.saveProfile(ign, unionLevel) }
                showEditProfileDialog = false
            },
            onSelectAvatar = { avatar ->
                // Switching to a free avatar has to actively step OUT of both
                // higher-priority sources (custom photo, shop portrait), or
                // ProfileHeader's priority order would keep showing whichever
                // of those was equipped and the tap would look like it did nothing.
                scope.launch {
                    profileRepo.setSelectedAvatar(avatar)
                    profileRepo.setCustomAvatarPath(null)
                    shopRepo.unequip(io.github.arglax.wuwalab.data.ShopCategory.PROFILE_PICTURE)
                }
            },
            onOverrideTodayAstrite = { amount ->
                scope.launch { astriteRepo.overrideTodayAmount(amount) }
            },
            ownedShopAvatars = ownedShopAvatars,
            equippedShopAvatarId = shopState.equippedAvatarId,
            onSelectShopAvatar = { item ->
                scope.launch {
                    profileRepo.setCustomAvatarPath(null) // step out of the custom photo's higher priority
                    shopRepo.equip(item)
                }
            },
            onSelectCustomAvatar = { renderedPath ->
                scope.launch {
                    profileRepo.setActiveCustomAvatar(renderedPath)
                    shopRepo.unequip(io.github.arglax.wuwalab.data.ShopCategory.PROFILE_PICTURE)
                }
            },
            ownedTitles = ownedTitles,
            equippedTitleId = shopState.equippedTitleId,
            onSelectTitle = { item ->
                if (item == null) {
                    shopRepo.unequip(io.github.arglax.wuwalab.data.ShopCategory.TITLE)
                } else {
                    shopRepo.equip(item)
                }
            }
        )
    }

    val bonusPercent by achievementsRepo.bonusPercentFlow.collectAsState(initial = 0f)
    val bonusMultiplier by achievementsRepo.bonusMultiplierFlow.collectAsState(initial = 1f)
    val achievementStates by achievementsRepo.achievementsFlow.collectAsState(initial = emptyList())
    val supporterUnlocked by achievementsRepo.supporterUnlockedFlow.collectAsState(initial = false)

    if (showProfileStatsDialog) {
        ProfileStatsDialog(
            profile = profile,
            lifetimeAstrites = lifetimeAstrites,
            radiantAstrites = radiantAstrites,
            shopUnlocked = shopState.owned.size,
            shopTotal = ShopCatalog.items.size,
            customWidgetBackgrounds = widgetStudioState.uploadsPurchased,
            shopAvatarRes = shopAvatarRes,
            equippedTitle = equippedTitleLabel,
            equippedTitleRarity = equippedTitleRarity,
            equippedAvatarBorder = equippedAvatarBorder,
            bonusPercent = bonusPercent,
            bonusMultiplier = bonusMultiplier,
            achievements = achievementStates,
            supporterUnlocked = supporterUnlocked,
            ownedTitles = ownedTitles,
            equippedTitleId = shopState.equippedTitleId,
            onDismiss = { showProfileStatsDialog = false }
        )
    }

    val startPage = remember(startPageKey) {
        RootPage.entries.firstOrNull { it.name == startPageKey } ?: RootPage.DASHBOARD
    }
    val pagerState = rememberPagerState(
        initialPage = startPage.ordinal,
        pageCount = { RootPage.entries.size }
    )
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    Column(modifier = modifier.fillMaxSize()) {
        ProfileHeader(
            profile = profile,
            lifetimeAstrites = lifetimeAstrites,
            radiantAstrites = radiantAstrites,
            onClick = { showProfileStatsDialog = true },
            onEditClick = { showEditProfileDialog = true },
            onShopClick = { scope.launch { pagerState.animateScrollToPage(RootPage.SHOP.ordinal) } },
            shopAvatarRes = shopAvatarRes,
            equippedTitle = equippedTitleLabel,
            equippedTitleRarity = equippedTitleRarity,
            equippedAvatarBorder = equippedAvatarBorder,
            isLandscape = isLandscape,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        GroupedNavBar(
            selected = RootPage.entries[pagerState.currentPage],
            onSelect = { page -> scope.launch { pagerState.animateScrollToPage(page.ordinal) } },
            isLandscape = isLandscape,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            beyondViewportPageCount = 1
        ) { pageIndex ->
            when (RootPage.entries[pageIndex]) {
                RootPage.DASHBOARD -> DashboardScreen(
                    repo = repo,
                    astriteRepo = astriteRepo,
                    luniteRepo = luniteRepo,
                    astriteEntries = astriteEntries,
                    playerName = profile.ign,
                    achievementsRepo = achievementsRepo,
                    onNavigateToAstriteTracker = {
                        scope.launch { pagerState.animateScrollToPage(RootPage.ASTRITES.ordinal) }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                RootPage.ASTRITES -> AstriteTrackerHost(
                    astriteRepo = astriteRepo,
                    economyRepo = economyRepo,
                    wuwaRepo = repo,
                    modifier = Modifier.fillMaxSize()
                )
                RootPage.PLANNER -> GachaPlannerScreen(
                    plannerRepo = plannerRepo,
                    astriteBalance = lifetimeAstrites,
                    dailyAverageEarn = dailyAverageEarn,
                    wuwaRepo = repo,
                    achievementsRepo = achievementsRepo,
                    modifier = Modifier.fillMaxSize()
                )
                RootPage.MATRIX -> EisenhowerMatrixScreen(todoRepo = todoRepo, achievementsRepo = achievementsRepo, modifier = Modifier.fillMaxSize())
                RootPage.TODO_LIST -> TodoScreen(todoRepo = todoRepo, wuwaRepo = repo, achievementsRepo = achievementsRepo, modifier = Modifier.fillMaxSize())
                RootPage.MATRIX_WIDGET -> MatrixWidgetSettingsScreen(modifier = Modifier.fillMaxSize())
                RootPage.EARN_QUIZ -> io.github.arglax.wuwalab.ui.earn.EarnScreen(
                    earnRepo = earnRepo,
                    modifier = Modifier.fillMaxSize()
                )
                RootPage.BANK -> io.github.arglax.wuwalab.ui.bank.BankScreen(
                    bankRepo = bankRepo,
                    wuwaRepo = repo,
                    modifier = Modifier.fillMaxSize()
                )
                RootPage.OVERLAY -> io.github.arglax.wuwalab.overlay.OverlayScreen(modifier = Modifier.fillMaxSize())
                RootPage.SHOP -> ShopScreen(
                    shopRepo = shopRepo,
                    wuwaRepo = repo,
                    modifier = Modifier.fillMaxSize()
                )
                RootPage.REDEEM -> io.github.arglax.wuwalab.ui.redeem.RedeemScreen(
                    redeemRepo = redeemRepo,
                    achievementsRepo = achievementsRepo,
                    modifier = Modifier.fillMaxSize()
                )
                RootPage.WIDGET -> WidgetStudioScreen(
                    studioRepo = widgetStudioRepo,
                    wuwaRepo = repo,
                    modifier = Modifier.fillMaxSize()
                )
                RootPage.PROFILE_STUDIO -> io.github.arglax.wuwalab.ui.profilestudio.ProfileStudioScreen(
                    studioRepo = profileStudioRepo,
                    wuwaRepo = repo,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
@Suppress("unused")
private fun LegacyRootTabBar(
    selected: RootPage,
    onSelect: (RootPage) -> Unit,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tapFeedback = rememberTapFeedback()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GlassSurface)
            .border(1.dp, GlassBorderSoft, RoundedCornerShape(14.dp))
            .then(
                // Portrait: there isn't room for every tab at a comfortable
                // width, so let the row scroll horizontally like chips.
                // Landscape: there's plenty of width to spare, so instead
                // stretch every tab to share it equally (see weight(1f)
                // below) rather than leaving it scrollable and mostly empty.
                if (isLandscape) Modifier else Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())
            )
            .padding(4.dp)
    ) {
        RootPage.entries.forEach { page ->
            val isSelected = page == selected
            Box(
                modifier = Modifier
                    .then(if (isLandscape) Modifier.weight(1f) else Modifier)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) VioletGlowDeep.copy(alpha = 0.35f) else Color.Transparent)
                    .clickable { tapFeedback(); onSelect(page) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    page.label,
                    color = if (isSelected) TextPrimary else TextMuted,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun DashboardScreen(
    repo: WuwaRepository,
    astriteRepo: AstriteRepository,
    luniteRepo: LuniteRepository,
    astriteEntries: List<AstriteEntry>,
    playerName: String,
    achievementsRepo: io.github.arglax.wuwalab.data.AchievementsRepository,
    onNavigateToAstriteTracker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val tapFeedback = rememberTapFeedback()
    val confirmFeedback = rememberConfirmFeedback()
    val dailySignInRepo = remember { DailySignInRepository(repo.context, astriteRepo, luniteRepo, repo) }
    val dashboardConfiguration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscapeDashboard = dashboardConfiguration.screenWidthDp > dashboardConfiguration.screenHeightDp

    // The real baseline from storage, with its REAL timestamp - never rebuild this
    // with "now" on every recomposition, or all the elapsed-time regen math breaks.
    var baseState by remember { mutableStateOf(WaveplateState(0, 0, System.currentTimeMillis())) }
    var events by remember { mutableStateOf(sampleEvents()) }
    var inputCurrent by remember { mutableStateOf("") }
    var inputFragments by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(EventTab.LIVE) }
    var selectedEventSort by remember { mutableStateOf(EventSort.SOONEST) }
    // Events used to render every banner at full height with a separate tab
    // row AND a separate sort row - three stacked blocks before you saw a
    // single event. It now collapses to the first few and expands on demand.
    var eventsExpanded by remember { mutableStateOf(false) }

    var showNotifyDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showLunitePassDialog by remember { mutableStateOf(false) }
    var notifyPrefs by remember { mutableStateOf(NotifyPrefs()) }

    var signedInToday by remember { mutableStateOf(false) }
    var showSignInClaimedDialog by remember { mutableStateOf(false) }
    var lastClaimResult by remember { mutableStateOf<DailySignInClaimResult?>(null) }

    val pendingArgstrites by repo.pendingArgstriteFlow.collectAsState(initial = 0)
    var claimedArgstriteAmount by remember { mutableStateOf<Int?>(null) }
    var logArgstriteAward by remember { mutableStateOf<Int?>(null) }
    var logArgstriteHint by remember { mutableStateOf<String?>(null) }

    // Lunite activation is reactive too, so toggling it from anywhere is
    // immediately reflected on the Dashboard card.
    val luniteActivated by luniteRepo.enabledFlow.collectAsState(initial = false)

    LaunchedEffect(Unit) {
        signedInToday = dailySignInRepo.hasClaimedToday()
    }

    // Collapsed by default - tap a resource's header row to reveal its
    // progress bar, description, and manual-update field/button.
    var waveplateExpanded by remember { mutableStateOf(false) }
    var crystalExpanded by remember { mutableStateOf(false) }

    // Recomputed once a minute so the countdowns and status pills stay accurate
    // without needing a full re-fetch of the underlying stored state.
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        baseState = repo.getWaveplateStateOnce()
        notifyPrefs = repo.notifyPrefsFlow.first()
        val fetched = repo.getCachedEvents()
            .ifEmpty { runCatching { repo.refreshEventsFromGitHub() }.getOrDefault(emptyList()) }
        if (fetched.isNotEmpty()) events = fetched
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            nowTick = System.currentTimeMillis()
        }
    }

    if (showNotifyDialog) {
        NotifyMeDialog(
            initialPrefs = notifyPrefs,
            onDismiss = { showNotifyDialog = false },
            onSave = { prefs ->
                notifyPrefs = prefs
                scope.launch { repo.setNotifyPrefs(prefs) }
                showNotifyDialog = false
            }
        )
    }
    if (showSettingsDialog) {
        SettingsDialog(onDismiss = { showSettingsDialog = false })
    }
    if (showLunitePassDialog) {
        LunitePassDialog(
            activated = luniteActivated,
            onSetActivated = { checked ->
                scope.launch {
                    luniteRepo.setEnabled(checked)
                    if (checked) {
                        LuniteReminderScheduler.rescheduleAll(repo.context, luniteRepo)
                    } else {
                        LuniteReminderScheduler.cancelAll(repo.context)
                    }
                }
            },
            onDismiss = { showLunitePassDialog = false }
        )
    }
    if (showSignInClaimedDialog) {
        lastClaimResult?.let { result ->
            DailySignInClaimedDialog(
                argstritesGranted = result.argstritesGranted,
                astritesGranted = result.astritesGranted,
                luniteIncluded = result.luniteIncluded,
                onDismiss = { showSignInClaimedDialog = false }
            )
        }
    }
    claimedArgstriteAmount?.let { amount ->
        io.github.arglax.wuwalab.ui.components.ArgstriteAwardDialog(
            amount = amount,
            onDismiss = { claimedArgstriteAmount = null }
        )
    }
    logArgstriteAward?.let { amount ->
        io.github.arglax.wuwalab.ui.components.ArgstriteAwardDialog(
            amount = amount,
            hintMessage = logArgstriteHint,
            onDismiss = { logArgstriteAward = null; logArgstriteHint = null }
        )
    }

    Surface(modifier = modifier.fillMaxSize(), color = Color.Transparent) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        greetingFor(playerName, nowTick),
                        fontWeight = FontWeight.Bold,
                        fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                        color = TextPrimary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        io.github.arglax.wuwalab.ui.components.HelpButton(
                            title = "Dashboard Help",
                            body = "Waveplates and Waveplate Crystals track themselves once you " +
                                "enter your current numbers - tap either card's header to expand it " +
                                "and update your count after you've played.\n\n" +
                                "\"Notify Me\" lets you turn on push alerts for full waveplates, " +
                                "maxed crystals, or a custom threshold.\n\n" +
                                "The Event tabs show what's Live and Ended - tap the sort chip to flip between soonest-ending-first and latest-ending-first.\n\n" +
                                "Daily Sign-In grants a small daily currency bonus once per day, " +
                                "and the Lunite Pass card (if you have the subscription) tracks " +
                                "your daily check-in and reminds you before you miss it."
                        )
                        Spacer(Modifier.width(8.dp))
                        IconGhostButton(onClick = { tapFeedback(); showNotifyDialog = true }) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = "Notify Me", tint = VioletGlow)
                        }
                        Spacer(Modifier.width(8.dp))
                        IconGhostButton(onClick = { tapFeedback(); showSettingsDialog = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = TextSecondary)
                        }
                    }
                }
            }

            item {
                val wpStatus = baseState.waveplateStatus(nowTick)
                val wpNow = baseState.computeCurrent(nowTick)
                ResourceCard(
                    iconRes = R.drawable.ic_waveplate,
                    iconSize = 22.dp,
                    label = "Waveplates",
                    current = wpNow,
                    max = WAVEPLATE_MAX,
                    suffixText = "",
                    status = wpStatus,
                    progress = wpNow / WAVEPLATE_MAX.toFloat(),
                    progressAccent = CyanGlow,
                    descriptionText = when (wpStatus) {
                        ResourceStatus.OVERLOADED -> "Above the 240 soft cap from a manual entry."
                        ResourceStatus.FULL -> "Full - spend them before they're wasted!"
                        else -> "Full in ${formatDuration(baseState.millisUntilWaveplatesFull(nowTick))}"
                    },
                    tooltipTitle = "About Waveplates",
                    tooltipBody = "Waveplates regen +1 every 6 minutes, soft-capped at 240 - passive regen never goes higher. You can manually enter a value above 240 (up to 2400) to reflect an in-game overflow; that shows as Overloaded. This app only knows what you tell it - update your waveplate count here every time you spend or gain them in-game.",
                    expanded = waveplateExpanded,
                    onToggleExpand = { waveplateExpanded = !waveplateExpanded },
                    inputValue = inputCurrent,
                    onInputChange = { inputCurrent = it.filter { c -> c.isDigit() }.take(4) },
                    inputLabel = "Current Waveplates",
                    updateLabel = "Update Waveplates",
                    onUpdate = {
                        val c = inputCurrent.toIntOrNull() ?: baseState.computeCurrent(nowTick)
                        scope.launch {
                            repo.setWaveplates(c, baseState.computeCrystals(nowTick))
                            baseState = repo.getWaveplateStateOnce()
                            nowTick = System.currentTimeMillis()
                            inputCurrent = ""
                            WuwaWidget.updateAll(repo.context)
                            val baseReward = io.github.arglax.wuwalab.ui.components.argstriteRewardFor("")
                            val reward = repo.addPendingArgstrite(baseReward)
                            val hint = repo.consumeArgstriteHint(false)
                            if (hint != null) {
                                logArgstriteHint = hint
                                logArgstriteAward = reward
                            }
                        }
                    },
                    accentColor = CyanGlow,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            item {
                val crystalStatus = baseState.crystalStatus(nowTick)
                val crystalNow = baseState.computeCrystals(nowTick)
                val wpStatus = baseState.waveplateStatus(nowTick)
                // Crystals only actually regen once waveplates are capped out
                // (FULL or OVERLOADED) - otherwise they're just sitting there
                // banked, not "Regenerating", so the status pill should read
                // "Frozen" instead of whatever REGENERATING/DEPLETED the raw
                // crystal math alone would imply.
                val crystalFrozen = wpStatus != ResourceStatus.FULL && wpStatus != ResourceStatus.OVERLOADED
                ResourceCard(
                    iconRes = R.drawable.ic_waveplate_crystal,
                    iconSize = 18.dp,
                    label = "WP Crystals",
                    current = crystalNow,
                    max = CRYSTAL_SOFT_CAP,
                    suffixText = "",
                    status = crystalStatus,
                    frozen = crystalFrozen,
                    progress = (crystalNow / CRYSTAL_SOFT_CAP.toFloat()).coerceAtMost(1f),
                    progressAccent = EmeraldGlow,
                    descriptionText = when {
                        crystalFrozen -> "Frozen - crystals start banking once waveplates cap out."
                        crystalStatus == ResourceStatus.FULL -> "Hard cap reached (480) - spend or convert soon."
                        else -> "Full in ${formatDuration(baseState.millisUntilCrystalsFull(nowTick) ?: 0L)}"
                    },
                    tooltipTitle = "About Waveplate Crystals",
                    tooltipBody = "Crystals only start banking once your waveplates are already sitting at 240/240 - +1 every 12 minutes, hard-capped at 480. Unlike waveplates, this cap can never be exceeded. While waveplates aren't full, crystals show as Frozen since they aren't gaining anything yet.",
                    expanded = crystalExpanded,
                    onToggleExpand = { crystalExpanded = !crystalExpanded },
                    inputValue = inputFragments,
                    onInputChange = { inputFragments = it.filter { c -> c.isDigit() }.take(4) },
                    inputLabel = "Current Crystals",
                    updateLabel = "Update Crystals",
                    onUpdate = {
                        val f = inputFragments.toIntOrNull() ?: baseState.computeCrystals(nowTick)
                        scope.launch {
                            repo.setWaveplates(baseState.computeCurrent(nowTick), f)
                            baseState = repo.getWaveplateStateOnce()
                            nowTick = System.currentTimeMillis()
                            inputFragments = ""
                            WuwaWidget.updateAll(repo.context)
                            val baseReward = io.github.arglax.wuwalab.ui.components.argstriteRewardFor("")
                            val reward = repo.addPendingArgstrite(baseReward)
                            val hint = repo.consumeArgstriteHint(false)
                            if (hint != null) {
                                logArgstriteHint = hint
                                logArgstriteAward = reward
                            }
                        }
                    },
                    accentColor = EmeraldGlow,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            if (astriteEntries.isNotEmpty()) {
                item {
                    // IntrinsicSize.Min on the Row + fillMaxHeight on both
                    // children makes the two sides match height automatically
                    // no matter how much content the right-hand column ends up
                    // with (Lunite Pass card + Daily Sign-In + Total Astrites),
                    // rather than hardcoding a dp value that drifts out of
                    // sync the next time either side's content changes.
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GlassCard(
                            modifier = Modifier
                                .weight(0.6f)
                                .fillMaxHeight()
                                .clickable(onClick = { tapFeedback(); onNavigateToAstriteTracker() }),
                            accent = AmberGlow
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(R.drawable.ic_astrite),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Astrites - Last 7 Days", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
                            }
                            val todayEarned = remember(astriteEntries) {
                                AstriteStats.buckets(
                                    astriteEntries,
                                    ChartPeriod.DAILY,
                                    count = 1
                                ).firstOrNull()?.total ?: 0
                            }

                            Spacer(Modifier.height(10.dp))

                            AstriteBarChart(
                                buckets = AstriteStats.buckets(
                                    astriteEntries,
                                    ChartPeriod.DAILY,
                                    count = 7
                                ),
                                accent = AmberGlow,
                                height = 64.dp,
                                showLabels = true
                            )

                            Spacer(Modifier.height(14.dp))

                            if (isLandscapeDashboard) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "===========================",
                                        color = TextSecondary,
                                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = "Astrites Earned Today",
                                        color = TextSecondary,
                                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                Text(
                                    text = "===========================",
                                    color = TextSecondary,
                                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )

                                Spacer(Modifier.height(14.dp))

                                Text(
                                    text = "Astrites Earned Today",
                                    color = TextSecondary,
                                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                // Faint Astrite background
                                Image(
                                    painter = painterResource(R.drawable.ic_astrite),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(170.dp)
                                        .alpha(0.08f)
                                )

                                // Main centered number
                                Text(
                                    text = todayEarned.toString(),
                                    color = AmberGlow,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 60.sp,
                                    textAlign = TextAlign.Center,
                                    style = TextStyle(
                                        shadow = Shadow(
                                            color = AmberGlow.copy(alpha = 0.6f),
                                            offset = Offset.Zero,
                                            blurRadius = 20f
                                        )
                                    )
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(0.4f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LunitePassCard(
                                activated = luniteActivated,
                                onClick = { tapFeedback(); showLunitePassDialog = true },
                                compact = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (pendingArgstrites > 0) {
                                ClaimArgstritesButton(
                                    amount = pendingArgstrites,
                                    onClick = {
                                        tapFeedback()
                                        scope.launch {
                                            val claimed = repo.claimPendingArgstrite()
                                            if (claimed > 0) claimedArgstriteAmount = claimed
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            DailySignInCard(
                                claimedToday = signedInToday,
                                luniteActive = luniteActivated,
                                onClaim = {
                                    scope.launch {
                                        val result = dailySignInRepo.claim()
                                        if (result != null) {
                                            confirmFeedback()
                                            signedInToday = true
                                            lastClaimResult = result
                                            showSignInClaimedDialog = true
                                            achievementsRepo.recordSignInClaim()
                                        } else {
                                            tapFeedback()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            GlassCard(modifier = Modifier.fillMaxWidth(), accent = VioletGlow) {
                                Text(
                                    "Total Astrites Earned",
                                    color = TextSecondary,
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(painter = painterResource(R.drawable.ic_astrite), contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        AstriteStats.totalGathered(astriteEntries).toString(),
                                        fontWeight = FontWeight.Bold,
                                        color = AmberGlow,
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                AppEventBoostCard(
                    nowTick = nowTick,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            item {
                EventsSection(
                    events = events,
                    tab = selectedTab,
                    onSelectTab = { tapFeedback(); selectedTab = it },
                    sort = selectedEventSort,
                    onToggleSort = {
                        tapFeedback()
                        selectedEventSort = if (selectedEventSort == EventSort.SOONEST) EventSort.LATEST else EventSort.SOONEST
                    },
                    expanded = eventsExpanded,
                    onToggleExpanded = { tapFeedback(); eventsExpanded = !eventsExpanded },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
    }
}

@Composable
private fun IconGhostButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(GlassSurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/**
 * One resource's card: collapsed shows just the icon/label, current count,
 * and status pill (tap the header to expand). Expanded additionally reveals
 * the progress bar, a short status description, and the manual-update
 * field + button.
 *
 * [frozen] overrides the usual [StatusPill] with a plain gray "Frozen" pill -
 * used by the Waveplate Crystals card while waveplates aren't FULL/OVERLOADED
 * yet (crystals don't regen at all until then, so "Regenerating..." would be
 * misleading).
 */
@Composable
private fun ResourceCard(
    iconRes: Int,
    iconSize: androidx.compose.ui.unit.Dp,
    label: String,
    current: Int,
    max: Int,
    suffixText: String,
    status: ResourceStatus,
    progress: Float,
    progressAccent: Color,
    descriptionText: String,
    tooltipTitle: String,
    tooltipBody: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    inputValue: String,
    onInputChange: (String) -> Unit,
    inputLabel: String,
    updateLabel: String,
    onUpdate: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    frozen: Boolean = false
) {
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevronRotation")

    GlassCard(modifier = modifier.fillMaxWidth().animateContentSize(), accent = accentColor) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpand)
        ) {
            Image(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(iconSize))
            Spacer(Modifier.width(6.dp))
            Text(label, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.width(6.dp))
            TooltipIcon(title = tooltipTitle, body = tooltipBody)
            Spacer(Modifier.weight(1f))
            Text(
                "$current / $max$suffixText",
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                modifier = Modifier.padding(end = 8.dp)
            )
            if (frozen) {
                FrozenPill()
            } else {
                StatusPill(status)
            }
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = TextSecondary,
                modifier = Modifier.padding(start = 4.dp).rotate(chevronRotation)
            )
        }

        if (expanded) {
            Spacer(Modifier.height(14.dp))
            GlossyProgressBar(progress = progress, accent = progressAccent)
            Spacer(Modifier.height(4.dp))
            Text(descriptionText, color = TextSecondary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = inputValue,
                onValueChange = onInputChange,
                label = { Text(inputLabel) },
                leadingIcon = {
                    Image(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(iconSize))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onUpdate,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(updateLabel)
            }
        }
    }
}

/** A gray "Frozen" pill, styled to match [StatusPill]'s shape/sizing but for a state that isn't one of [ResourceStatus]'s cases. */
@Composable
private fun FrozenPill(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(TextMuted.copy(alpha = 0.16f))
            .border(1.dp, TextMuted.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(TextMuted)
        )
        Text(
            "Frozen",
            color = TextMuted,
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
private fun GlossyProgressBar(progress: Float, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(50))
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.55f), accent)
                    )
                )
        )
    }
}

/** Tappable header - three segments the user taps to switch which category's banners show below. */
@Composable
private fun EventTabHeader(
    selected: EventTab,
    onSelect: (EventTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassSurface)
            .border(1.dp, GlassBorderSoft, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        EventTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) tab.accent.copy(alpha = 0.20f) else Color.Transparent)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    tab.label,
                    color = if (isSelected) tab.accent else TextMuted,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * The whole Events block, in one place and deliberately compact.
 *
 * The old layout spent three full-width rows (title, tab strip, sort chip)
 * before the first banner and then rendered EVERY event at full height, which
 * is what made this section read as overwhelming. Now the title and the sort
 * control share one line, the banners are shorter, and only the first
 * [COLLAPSED_EVENT_COUNT] show until the user asks for the rest.
 */
private const val COLLAPSED_EVENT_COUNT = 3

@Composable
private fun EventsSection(
    events: List<WuwaEvent>,
    tab: EventTab,
    onSelectTab: (EventTab) -> Unit,
    sort: EventSort,
    onToggleSort: () -> Unit,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis()
    // "Time left" for a Live event is its remaining time until it ends; for an
    // Ended event there's none left, so it's ranked by how long ago it ended
    // instead (still ascending/descending consistently with the toggle).
    val base = when (tab) {
        EventTab.LIVE -> events.filter { it.status() == WuwaEvent.Status.LIVE }
        EventTab.ENDED -> events.filter { it.status() == WuwaEvent.Status.EXPIRED }
    }
    val filtered = when (sort) {
        EventSort.SOONEST -> base.sortedBy { it.endEpochMs - now }
        EventSort.LATEST -> base.sortedByDescending { it.endEpochMs - now }
    }
    val shown = if (expanded) filtered else filtered.take(COLLAPSED_EVENT_COUNT)
    var selectedEvent by remember { mutableStateOf<WuwaEvent?>(null) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Events", fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    filtered.size.toString(),
                    color = TextMuted,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, GlassBorderSoft, RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggleSort)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    sort.label,
                    color = TextSecondary,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                )
            }
        }

        EventTabHeader(selected = tab, onSelect = onSelectTab)

        if (filtered.isEmpty()) {
            Text("No " + tab.label.lowercase() + " events right now.", color = TextMuted)
        } else {
            shown.forEach { event -> EventBanner(event, onClick = { selectedEvent = event }) }
            if (filtered.size > COLLAPSED_EVENT_COUNT) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, GlassBorderSoft, RoundedCornerShape(12.dp))
                        .clickable(onClick = onToggleExpanded)
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (expanded) "Show less" else "Show all " + filtered.size + " events",
                        color = CyanGlow,
                        fontWeight = FontWeight.Bold,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                    )
                }
            }
        }

        selectedEvent?.let {
            EventDetailDialog(it, eventCategoryAccent(it.eventType), onDismiss = { selectedEvent = null })
        }
    }
}

@Composable
@Suppress("DiscouragedApi") // Intentional: see the comment below - this is what makes "drop a file, reference it by name" work for event banners.
private fun EventBanner(event: WuwaEvent, onClick: () -> Unit = {}) {
    val accent = eventCategoryAccent(event.eventType)
    val subtitle = when (event.status()) {
        WuwaEvent.Status.UPCOMING -> "Starts ${formatCountdown(event.startEpochMs)}"
        WuwaEvent.Status.LIVE -> "Ends ${formatCountdown(event.endEpochMs)}"
        WuwaEvent.Status.EXPIRED -> "Ended ${formatPast(event.endEpochMs)}"
    }

    // Look up res/drawable/<bannerImage>.* by name at runtime. This is what lets
    // events.json ship just a string like "convene_changli_bg" - drop a matching
    // file into res/drawable/ later and it starts showing automatically, no code
    // changes needed. Returns 0 (not found) until you've added the file, in which
    // case we silently fall back to the plain accent-stripe layout below.
    val context = androidx.compose.ui.platform.LocalContext.current
    val bannerResId = remember(event.bannerImage) {
        event.bannerImage?.let { name ->
            context.resources.getIdentifier(name, "drawable", context.packageName)
                .takeIf { it != 0 }
        }
    }

    val isLive = event.status() == WuwaEvent.Status.LIVE
    val borderModifier = if (isLive) {
        // Live events get a subtle "breathing" glow instead of a static
        // hairline border - see Modifier.pulsingGlow's kdoc for why this is
        // a Compose animation rather than a bundled flame/shine GIF.
        Modifier.pulsingGlow(accent, 22.dp)
    } else {
        Modifier.border(1.dp, GlassBorderSoft, RoundedCornerShape(22.dp))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .clip(RoundedCornerShape(22.dp))
            .then(borderModifier)
            .clickable(onClick = onClick)
    ) {
        if (bannerResId != null) {
            // ContentScale.Crop = fills the whole rectangle and trims/clips
            // whatever overflows, so any source image size/aspect ratio "just
            // works" without you needing to pre-crop it yourself.
            Image(
                painter = painterResource(bannerResId),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Dark gradient scrim so the title/subtitle stay legible over any artwork.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(SpaceDeep.copy(alpha = 0.85f), SpaceDeep.copy(alpha = 0.25f))
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.05f))
                        )
                    )
            )
            Box(modifier = Modifier.fillMaxSize().background(glassCardGradient()))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent)
            )
            Spacer(Modifier.width(12.dp))
        val eventBannerIsLandscape = androidx.compose.ui.platform.LocalConfiguration.current.let {
            it.screenWidthDp > it.screenHeightDp
        }
        if (eventBannerIsLandscape) {
            // Landscape has plenty of width to spare - put the title and the
            // countdown subtitle on the same line instead of stacking them,
            // so the extra horizontal space is actually used instead of
            // leaving the banner mostly-empty vertical whitespace either side.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        event.name,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize
                    )
                    if (!event.eventType.isNullOrBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accent.copy(alpha = 0.28f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                event.eventType.uppercase(),
                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    subtitle,
                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                    color = TextSecondary
                )
            }
        } else {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        event.name,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize
                    )
                    if (!event.eventType.isNullOrBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accent.copy(alpha = 0.28f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                event.eventType.uppercase(),
                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    subtitle,
                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                    color = TextSecondary
                )
            }
        }
        }
    }
}

@Composable
private fun EventDetailDialog(event: WuwaEvent, accent: Color, onDismiss: () -> Unit) {
    val subtitle = when (event.status()) {
        WuwaEvent.Status.UPCOMING -> "Starts ${formatCountdown(event.startEpochMs)}"
        WuwaEvent.Status.LIVE -> "Ends ${formatCountdown(event.endEpochMs)}"
        WuwaEvent.Status.EXPIRED -> "Ended ${formatPast(event.endEpochMs)}"
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SpaceDeep,
        title = {
            Column {
                Text(event.name, fontWeight = FontWeight.Bold, color = TextPrimary)
                if (!event.eventType.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent.copy(alpha = 0.28f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            event.eventType.uppercase(),
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(subtitle, color = TextSecondary, fontSize = MaterialTheme.typography.labelMedium.fontSize)
                if (event.details.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    event.details.forEach { line ->
                        Row {
                            Text("•  ", color = accent, fontWeight = FontWeight.Bold)
                            Text(line, color = TextPrimary)
                        }
                    }
                } else {
                    Text("No additional details for this event.", color = TextMuted)
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Close", color = accent)
            }
        }
    )
}

private fun formatCountdown(targetEpochMs: Long): String {
    val diff = targetEpochMs - System.currentTimeMillis()
    if (diff <= 0) return "now"
    val days = diff / 86_400_000L
    val hours = (diff % 86_400_000L) / 3_600_000L
    return if (days > 0) "in ${days}d ${hours}h" else "in ${hours}h"
}

private fun formatPast(pastEpochMs: Long): String {
    val diff = System.currentTimeMillis() - pastEpochMs
    val days = diff / 86_400_000L
    return if (days > 0) "${days}d ago" else "recently"
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "now"
    val days = TimeUnit.MILLISECONDS.toDays(ms)
    val hours = TimeUnit.MILLISECONDS.toHours(ms) % 24
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

/**
 * Two-level navigation. The top row picks a group; the pill row beneath it
 * only appears when that group holds more than one page, so simple groups
 * never waste vertical space. Portrait scrolls both rows like chips;
 * landscape shares the width equally, exactly as the old single row did.
 */
@Composable
private fun GroupedNavBar(
    selected: RootPage,
    onSelect: (RootPage) -> Unit,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tapFeedback = rememberTapFeedback()
    val activeGroup = selected.group
    val pagesInGroup = RootPage.pagesIn(activeGroup)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(GlassSurface)
                .border(1.dp, GlassBorderSoft, RoundedCornerShape(14.dp))
                .then(
                    if (isLandscape) Modifier
                    else Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())
                )
                .padding(4.dp)
        ) {
            NavGroup.entries.forEach { group ->
                val isSelected = group == activeGroup
                Box(
                    modifier = Modifier
                        .then(if (isLandscape) Modifier.weight(1f) else Modifier)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) VioletGlowDeep.copy(alpha = 0.35f) else Color.Transparent)
                        .clickable {
                            tapFeedback()
                            RootPage.pagesIn(group).firstOrNull()?.let(onSelect)
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        group.label,
                        color = if (isSelected) TextPrimary else TextMuted,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (pagesInGroup.size > 1) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isLandscape) Modifier
                        else Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())
                    ),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pagesInGroup.forEach { page ->
                    val isSelected = page == selected
                    Box(
                        modifier = Modifier
                            .then(if (isLandscape) Modifier.weight(1f) else Modifier)
                            .clip(RoundedCornerShape(50))
                            .background(if (isSelected) CyanGlow.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f))
                            .border(
                                1.dp,
                                if (isSelected) CyanGlow.copy(alpha = 0.7f) else GlassBorderSoft,
                                RoundedCornerShape(50)
                            )
                            .clickable { tapFeedback(); onSelect(page) }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            page.label,
                            color = if (isSelected) TextPrimary else TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
