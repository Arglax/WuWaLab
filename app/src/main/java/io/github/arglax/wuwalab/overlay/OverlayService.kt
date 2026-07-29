package io.github.arglax.wuwalab.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.arglax.wuwalab.MainActivity
import io.github.arglax.wuwalab.R
import io.github.arglax.wuwalab.data.AstriteEntry
import io.github.arglax.wuwalab.data.AstriteRepository
import io.github.arglax.wuwalab.data.AstriteEconomy
import io.github.arglax.wuwalab.data.EconomyRepository
import io.github.arglax.wuwalab.data.OverlayPrefs
import io.github.arglax.wuwalab.data.SpendOutcome
import io.github.arglax.wuwalab.data.WuwaRepository
import io.github.arglax.wuwalab.notification.CHANNEL_ID_ALERTS
import io.github.arglax.wuwalab.notification.NotificationUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * The floating "chat head"-style overlay service.
 *
 * Physics/bounds changes vs. the previous version:
 *  - FLAG_LAYOUT_NO_LIMITS is GONE. The window is now hard-clamped by the
 *    bubble content itself (see OverlayBubbleContent's applyWindowPosition),
 *    so nothing can ever be dragged or expanded off-screen.
 *  - Screen size comes from windowManager.currentWindowMetrics (exact, insets
 *    aware on API 30+, which is this app's minSdk) instead of displayMetrics.
 *  - The restored position is clamped against the CURRENT screen size, so a
 *    position saved on a taller/wider device (or before a rotation) can never
 *    spawn the bubble out of bounds - this is what makes the saved
 *    fraction-based position genuinely resolution/aspect-ratio independent.
 *
 * Requires the "Display over other apps" special permission (OverlayScreen
 * owns requesting it, with a gentle fallback that routes to Android Settings).
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayPrefs: OverlayPrefs
    private lateinit var astriteRepo: AstriteRepository
    private lateinit var lifecycleOwner: OverlayLifecycleOwner
    private var bubbleView: ComposeView? = null
    // A separate, full-screen, touch-transparent window that draws the red
    // corner "kill zone" while the bubble is being dragged. It has to be its
    // own window (not just content inside the bubble's own ComposeView)
    // because the bubble window is WRAP_CONTENT-sized around the bubble
    // itself, so it can't draw something anchored to an arbitrary screen
    // corner that may be far away from the bubble.
    private var killZoneView: ComposeView? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayPrefs = OverlayPrefs(this)
        astriteRepo = AstriteRepository(this)
        startForeground(NOTIFICATION_ID, buildNotification())
        addBubble()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Floating overlay", NotificationManager.IMPORTANCE_MIN)
            )
        }
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WuWaLab overlay is active")
            .setContentText("Tap the bubble to log Astrites - drag it to the corner to remove it.")
            .setSmallIcon(R.drawable.ic_astrite)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .build()
    }

    private fun addBubble() {
        lifecycleOwner = OverlayLifecycleOwner().apply {
            performRestore(null)
            handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            handleLifecycleEvent(Lifecycle.Event.ON_START)
            handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        addKillZoneOverlay()

        val settings = overlayPrefs.load()

        // Exact current window bounds (API 30+): the full screen this window
        // can occupy, which is what the bubble's clamping math needs. This is
        // re-read every time addBubble() runs (including from
        // onConfigurationChanged after a rotation), so the bubble is never
        // clamped against a stale, pre-rotation screen size.
        val bounds = windowManager.currentWindowMetrics.bounds
        val screenW = bounds.width()
        val screenH = bounds.height()
        val bubblePx = (56 * resources.displayMetrics.density).toInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // No FLAG_LAYOUT_NO_LIMITS: the window must never leave the screen.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Restore from fractions, clamped so the whole bubble is on-screen
            // regardless of which device/orientation saved them.
            x = (settings.lastXFraction * screenW).toInt().coerceIn(0, (screenW - bubblePx).coerceAtLeast(0))
            y = (settings.lastYFraction * screenH).toInt().coerceIn(0, (screenH - bubblePx).coerceAtLeast(0))
        }

        lateinit var composeView: ComposeView
        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                OverlayBubbleContent(
                    windowManager = windowManager,
                    layoutParams = params,
                    screenWidthPx = screenW,
                    screenHeightPx = screenH,
                    // Must update the layout of the exact View instance passed
                    // to windowManager.addView() (the outer ComposeView) - NOT
                    // LocalView.current, which resolves to the internal
                    // AndroidComposeView child and throws "View not attached".
                    onUpdateLayout = {
                        runCatching { windowManager.updateViewLayout(composeView, params) }
                    },
                    onPositionSettled = { xFraction, yFraction ->
                        overlayPrefs.savePosition(xFraction, yFraction)
                    },
                    onDelete = {
                        overlayPrefs.setEnabled(false)
                        stopSelf()
                    },
                    onLogAstrites = { amount -> logAstritesToday(amount) },
                    onLogSpend = { amount, onDone -> logSpendToday(amount, onDone) },
                    onLogPopupClosed = { added, wasSpend, amount -> notifyLogResult(added, wasSpend, amount) }
                )
            }
        }
        bubbleView = composeView
        runCatching { windowManager.addView(composeView, params) }
    }

    /**
     * Full-screen window that only ever shows the red corner kill-zone
     * indicator while the bubble is being dragged (see
     * [io.github.arglax.wuwalab.overlay.OverlayKillZoneState]). FLAG_NOT_TOUCHABLE
     * + FLAG_NOT_FOCUSABLE mean it never intercepts touches - they pass
     * straight through to the bubble window (added after this one, i.e. on
     * top) or to whatever app is underneath.
     */
    private fun addKillZoneOverlay() {
        val bounds = windowManager.currentWindowMetrics.bounds
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent { KillZoneOverlayContent() }
        }
        killZoneView = composeView
        runCatching { windowManager.addView(composeView, params) }
    }

    /**
     * Rotating the device changes the screen's width/height, but the bubble's
     * clamping math above only ever saw the dimensions captured once at
     * [addBubble] time - so after a rotation it kept clamping the bubble
     * against the OLD (pre-rotation) screen size, effectively "trapping" it
     * inside whichever dimension used to be the shorter one. Tearing down and
     * re-adding the bubble on every configuration change re-reads
     * currentWindowMetrics fresh, so the bubble is always clamped against the
     * screen it is actually on.
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (bubbleView == null) return
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        killZoneView?.let { runCatching { windowManager.removeView(it) } }
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        addBubble()
    }

    private fun notifyLogResult(added: Boolean, wasSpend: Boolean, amount: Int) {
        NotificationUtils.ensureChannels(this)
        val title = when {
            added && wasSpend -> "Spend Logged"
            added -> "Astrites Logged"
            else -> "Nothing Logged"
        }
        val text = when {
            added && wasSpend -> "$amount Astrites were logged as spent, from the overlay bubble."
            added -> "$amount Astrites were added to today's total, from the overlay bubble."
            else -> "The overlay popup was closed without logging anything."
        }
        NotificationUtils.notify(
            context = this,
            channelId = CHANNEL_ID_ALERTS,
            id = LOG_RESULT_NOTIFICATION_ID,
            title = title,
            text = text,
            header = "WuWaLab · Overlay"
        )
    }

    private fun logAstritesToday(amount: Int) {
        serviceScope.launch {
            val today = LocalDate.now()
            val todayIso = AstriteEntry.ISO.format(today)
            val existing = astriteRepo.getEntriesOnce().find { it.dateIso == todayIso }
            val newTotal = (existing?.amount ?: 0) + amount
            astriteRepo.upsertEntry(
                AstriteEntry.forDate(today, newTotal, existing?.source?.ifBlank { "Overlay Quick Add" } ?: "Overlay Quick Add")
            )
            // Quick-add from the overlay is a real, functional action too - it
            // just banks +1 Argstrite (no custom note here, so never +2) into
            // the unclaimed pile, to be swept up from the Dashboard's Claim button.
            WuwaRepository(this@OverlayService).addPendingArgstrite(1)
        }
    }

    /**
     * Logs a spend straight through [AstriteEconomy] (same door the Pull
     * Planner and Shop use), so an overlay spend shows up in the Economic
     * Dashboard logbook exactly like any other spend - it's a real ledger
     * entry, not just a number subtracted somewhere.
     */
    private fun logSpendToday(amount: Int, onResult: (Boolean) -> Unit) {
        serviceScope.launch {
            val economy = AstriteEconomy(astriteRepo, EconomyRepository(this@OverlayService))
            val outcome = economy.spend(amount, category = "Overlay Quick Spend", note = "Logged from overlay")
            withContext(Dispatchers.Main) {
                onResult(outcome is SpendOutcome.Success)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        killZoneView?.let { runCatching { windowManager.removeView(it) } }
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "overlay_channel"
        private const val NOTIFICATION_ID = 4201
        private const val LOG_RESULT_NOTIFICATION_ID = 4202

        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}

/**
 * A minimal Lifecycle/ViewModelStore/SavedStateRegistry owner so a
 * ComposeView can run inside a bare WindowManager overlay, which has no
 * Activity (and therefore none of the owners ComposeView normally expects)
 * behind it. This is the standard recipe for "Compose outside an Activity".
 */
private class OverlayLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    fun performRestore(bundle: Bundle?) {
        savedStateRegistryController.performRestore(bundle)
    }

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }
}