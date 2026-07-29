package io.github.arglax.wuwalab.overlay

import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.arglax.wuwalab.R
import io.github.arglax.wuwalab.ui.theme.AmberGlow
import io.github.arglax.wuwalab.ui.theme.CoralGlow
import io.github.arglax.wuwalab.ui.theme.GlassSurface
import io.github.arglax.wuwalab.ui.theme.TextPrimary
import io.github.arglax.wuwalab.ui.theme.VioletGlow
import io.github.arglax.wuwalab.ui.theme.VioletGlowDeep
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val BUBBLE_SIZE_DP = 56
private const val TAP_SLOP_PX = 24f // moves shorter than this count as a tap, not a drag
// The bubble is destroyed by dropping it inside a radius of whichever
// screen corner it's currently closest to (dynamic - not a fixed corner).
private const val KILL_ZONE_RADIUS_DP = 96

/**
 * Cross-window UI state for the corner "kill zone". [OverlayBubbleContent]
 * (the bubble's own small WRAP_CONTENT window) writes to this while
 * dragging; [KillZoneOverlayContent] (a separate full-screen, non-touchable
 * window - see OverlayService) reads it to render the red trash-bin corner
 * indicator. A plain object with a Compose [mutableStateOf] works fine here
 * since both windows live in the same process/composition-aware runtime.
 */
data class KillZoneUiState(
    val visible: Boolean = false,
    // Which screen corner is currently nearest the dragged bubble.
    val cornerAtRight: Boolean = false,
    val cornerAtBottom: Boolean = false,
    // True once the bubble is actually hovering inside the kill zone -
    // i.e. releasing right now would delete it.
    val armed: Boolean = false
)

object OverlayKillZoneState {
    var uiState: KillZoneUiState by mutableStateOf(KillZoneUiState())
}

/**
 * Full-screen, touch-transparent overlay content that renders the red
 * "kill zone" trash-bin indicator in whichever corner [OverlayKillZoneState]
 * currently points at. Lives in its own WindowManager window (added/kept
 * above the bubble but with FLAG_NOT_TOUCHABLE, so it never steals touches
 * from the bubble or from apps underneath) so it can be drawn anywhere on
 * screen regardless of the bubble window's own small WRAP_CONTENT bounds.
 */
@Composable
fun KillZoneOverlayContent() {
    val state = OverlayKillZoneState.uiState
    val scale by animateFloatAsState(if (state.armed) 1.15f else 1f, label = "killZoneScale")

    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = state.visible,
                enter = fadeIn(animationSpec = tween(120)),
                exit = fadeOut(animationSpec = tween(160)),
                modifier = Modifier.align(
                    when {
                        state.cornerAtRight && state.cornerAtBottom -> Alignment.BottomEnd
                        state.cornerAtRight && !state.cornerAtBottom -> Alignment.TopEnd
                        !state.cornerAtRight && state.cornerAtBottom -> Alignment.BottomStart
                        else -> Alignment.TopStart
                    }
                )
            ) {
                Box(
                    modifier = Modifier
                        .padding(18.dp)
                        .size(KILL_ZONE_RADIUS_DP.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(CoralGlow.copy(alpha = if (state.armed) 0.55f else 0.28f))
                        .border(
                            width = if (state.armed) 3.dp else 1.5.dp,
                            color = CoralGlow.copy(alpha = if (state.armed) 1f else 0.6f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.DeleteForever,
                        contentDescription = "Drop here to turn the overlay off",
                        tint = Color.White,
                        modifier = Modifier.size(if (state.armed) 40.dp else 34.dp)
                    )
                }
            }
        }
    }
}

/**
 * The floating bubble's Compose content, with screen-aware physics:
 *
 *  SMART BOUNDS - the BUBBLE's position (not the window's) is the source of
 *  truth, clamped so it can never leave the screen. When the popup opens, the
 *  window grows - and the window's x/y are recomputed so the popup expands
 *  toward the side with room: bubble on the right half -> content draws to
 *  the LEFT; bubble on the bottom half -> content draws ABOVE (and vice
 *  versa). The bubble itself never visually moves when the popup opens,
 *  because the window origin shift exactly compensates the size change
 *  (applied from onSizeChanged, i.e. with the REAL measured size - no
 *  guessed popup dimensions).
 *
 *  RESOLUTION SCALING - the bubble is sized in dp and converted through
 *  LocalDensity, and the saved position is stored as screen fractions, so
 *  any density/aspect-ratio combination lands in the same relative spot.
 *
 *  DRAGGABILITY - a single manual awaitEachGesture loop (not
 *  detectDragGestures, whose internal touch slop silently swallows taps)
 *  tracks every pointer move itself, so taps and drags are both always
 *  detected from the same gesture with no dead zones.
 */
@Composable
fun OverlayBubbleContent(
    windowManager: WindowManager,
    layoutParams: WindowManager.LayoutParams,
    screenWidthPx: Int,
    screenHeightPx: Int,
    onUpdateLayout: () -> Unit,
    onPositionSettled: (xFraction: Float, yFraction: Float) -> Unit,
    onDelete: () -> Unit,
    onLogAstrites: (amount: Int) -> Unit,
    onLogSpend: (amount: Int, onDone: (Boolean) -> Unit) -> Unit,
    onLogPopupClosed: (added: Boolean, wasSpend: Boolean, amount: Int) -> Unit
) {
    val density = LocalDensity.current
    val bubblePx = with(density) { BUBBLE_SIZE_DP.dp.roundToPx() }

    var showLogPopup by remember { mutableStateOf(false) }
    // Which of the two logging modes the popup is currently showing - both
    // append their own kind of entry: "add" bumps today's Astrite total the
    // way it always did, "spend" writes a real SPEND ledger entry through the
    // same door the Pull Planner and Shop use (see OverlayService.logSpendToday).
    var isSpendMode by remember { mutableStateOf(false) }
    var inDeleteZone by remember { mutableStateOf(false) }
    var dragDistance by remember { mutableFloatStateOf(0f) }

    // The bubble's own top-left corner in screen coordinates - the single
    // source of truth the window position is derived from.
    var bubbleX by remember { mutableIntStateOf(layoutParams.x) }
    var bubbleY by remember { mutableIntStateOf(layoutParams.y) }
    var windowSize by remember { mutableStateOf(IntSize(bubblePx, bubblePx)) }

    val expandLeft = bubbleX + bubblePx / 2 > screenWidthPx / 2
    val expandUp = bubbleY + bubblePx / 2 > screenHeightPx / 2

    fun clampBubble() {
        bubbleX = bubbleX.coerceIn(0, (screenWidthPx - bubblePx).coerceAtLeast(0))
        bubbleY = bubbleY.coerceIn(0, (screenHeightPx - bubblePx).coerceAtLeast(0))
    }

    /**
     * Derives the WINDOW origin from the bubble anchor + measured window size
     * so expanded content always draws toward the roomy side, then clamps the
     * whole window on-screen. Called on every drag step AND every size change.
     */
    fun applyWindowPosition() {
        val w = windowSize.width.coerceAtLeast(bubblePx)
        val h = windowSize.height.coerceAtLeast(bubblePx)
        layoutParams.x = (if (expandLeft) bubbleX + bubblePx - w else bubbleX)
            .coerceIn(0, (screenWidthPx - w).coerceAtLeast(0))
        layoutParams.y = (if (expandUp) bubbleY + bubblePx - h else bubbleY)
            .coerceIn(0, (screenHeightPx - h).coerceAtLeast(0))
        onUpdateLayout()
    }

    val killZoneRadiusPx = with(density) { KILL_ZONE_RADIUS_DP.dp.roundToPx() / 2 }

    // The kill zone always tracks whichever corner the bubble is CURRENTLY
    // closest to (same quadrant math as the popup's expandLeft/expandUp
    // already above), so it follows the drag rather than sitting fixed.
    fun isInDeleteZone(): Boolean {
        val bubbleCenterX = bubbleX + bubblePx / 2f
        val bubbleCenterY = bubbleY + bubblePx / 2f
        val cornerX = if (expandLeft) screenWidthPx.toFloat() else 0f
        val cornerY = if (expandUp) screenHeightPx.toFloat() else 0f
        return hypot(bubbleCenterX - cornerX, bubbleCenterY - cornerY) <= killZoneRadiusPx
    }

    val view = LocalView.current
    // Haptic + scale-down feedback the instant the bubble enters the zone,
    // so the user feels/sees the "this will be deleted" moment before they
    // actually let go.
    LaunchedEffect(inDeleteZone) {
        if (inDeleteZone) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }
    val bubbleScale by animateFloatAsState(if (inDeleteZone) 0.8f else 1f, label = "bubbleKillZoneScale")

    val bubble: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(BUBBLE_SIZE_DP.dp)
                .scale(bubbleScale)
                .clip(CircleShape)
                .background(if (inDeleteZone) CoralGlow.copy(alpha = 0.85f) else VioletGlowDeep)
                .border(
                    width = if (inDeleteZone) 3.dp else 1.5.dp,
                    color = if (inDeleteZone) CoralGlow else VioletGlow.copy(alpha = 0.6f),
                    shape = CircleShape
                )
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        dragDistance = 0f
                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (!change.pressed) {
                                // Pointer lifted - decide tap vs. drag vs. delete-drop.
                                if (dragDistance < TAP_SLOP_PX) {
                                    showLogPopup = !showLogPopup
                                    if (!showLogPopup) onLogPopupClosed(false, false, 0)
                                    else isSpendMode = false
                                } else if (isInDeleteZone()) {
                                    onDelete()
                                } else {
                                    val xFraction = (bubbleX.toFloat() / screenWidthPx).coerceIn(0f, 1f)
                                    val yFraction = (bubbleY.toFloat() / screenHeightPx).coerceIn(0f, 1f)
                                    onPositionSettled(xFraction, yFraction)
                                }
                                inDeleteZone = false
                                OverlayKillZoneState.uiState = KillZoneUiState()
                                break
                            }
                            val dragAmount = change.positionChange()
                            if (dragAmount != Offset.Zero) {
                                change.consume()
                                dragDistance += abs(dragAmount.x) + abs(dragAmount.y)
                                bubbleX += dragAmount.x.roundToInt()
                                bubbleY += dragAmount.y.roundToInt()
                                clampBubble()
                                val wasInDeleteZone = inDeleteZone
                                inDeleteZone = isInDeleteZone()
                                // Only show the kill-zone indicator once this is a
                                // genuine drag (past tap slop), and keep it tracking
                                // whichever corner is currently nearest.
                                OverlayKillZoneState.uiState = if (dragDistance >= TAP_SLOP_PX) {
                                    KillZoneUiState(
                                        visible = true,
                                        cornerAtRight = expandLeft,
                                        cornerAtBottom = expandUp,
                                        armed = inDeleteZone
                                    )
                                } else {
                                    KillZoneUiState()
                                }
                                applyWindowPosition()
                                // Destroy the instant the bubble touches/enters the
                                // kill zone WHILE DRAGGING - don't wait for the
                                // pointer to be lifted first. Guarded by
                                // dragDistance already having passed TAP_SLOP_PX
                                // (required to have reached the zone at all), so a
                                // simple tap can never trigger this.
                                if (inDeleteZone && !wasInDeleteZone) {
                                    onDelete()
                                    inDeleteZone = false
                                    OverlayKillZoneState.uiState = KillZoneUiState()
                                    break
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (inDeleteZone) {
                Icon(Icons.Filled.DeleteForever, contentDescription = "Release to remove overlay", tint = Color.White, modifier = Modifier.size(26.dp))
            } else {
                // The overlay bubble shows the same "WuWa Lab" app logo as the
                // launcher icon, not the Astrite glyph - so the floating
                // bubble is instantly recognizable as WuWa Lab itself.
                //
                // NOTE: this used to be painterResource(R.mipmap.ic_launcher),
                // the adaptive launcher icon. Adaptive icons are backed by
                // AdaptiveIconDrawable, which painterResource cannot render
                // outside a normal Activity/View hierarchy - inside this bare
                // WindowManager overlay (no Activity behind it) that threw and
                // crashed the whole app the moment the bubble was tapped. A
                // plain flat PNG drawable has no such requirement, so it's
                // used here instead.
                Image(
                    painter = painterResource(R.drawable.wuwa_lab_logo),
                    contentDescription = "WuWa Lab overlay",
                    modifier = Modifier.size(38.dp).clip(CircleShape)
                )
            }
        }
    }

    // Popup grows from whichever corner is nearest the bubble, instead of
    // Compose's default expandIn/shrinkOut (a directional wipe that - on a
    // small, off-center popup like this one - reads as an abrupt "unsheathing"
    // motion rather than a natural pop-open). A soft fade + scale from that
    // same corner feels like the content is growing out of the bubble.
    val popupTransformOrigin = androidx.compose.ui.graphics.TransformOrigin(
        pivotFractionX = if (expandLeft) 1f else 0f,
        pivotFractionY = if (expandUp) 1f else 0f
    )

    val popup: @Composable () -> Unit = {
        AnimatedVisibility(
            visible = showLogPopup,
            enter = fadeIn(animationSpec = tween(180)) +
                scaleIn(initialScale = 0.85f, animationSpec = tween(180), transformOrigin = popupTransformOrigin),
            exit = fadeOut(animationSpec = tween(140)) +
                scaleOut(targetScale = 0.85f, animationSpec = tween(140), transformOrigin = popupTransformOrigin)
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 10.dp, horizontal = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(GlassSurface)
                    .border(1.dp, AmberGlow.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painter = painterResource(R.drawable.ic_astrite), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isSpendMode) "Log Spend Astrites" else "Add to Today's Astrites",
                        color = TextPrimary,
                        fontSize = MaterialTheme.typography.labelMedium.fontSize
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close without logging",
                        tint = TextPrimary,
                        modifier = Modifier
                            .size(18.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = {
                                    showLogPopup = false
                                    onLogPopupClosed(false, false, 0)
                                })
                            }
                    )
                }
                Spacer(Modifier.height(8.dp))

                // Add / Log Spend mode switcher - each mode appends its own
                // kind of entry: Add bumps today's earned total, Log Spend
                // writes a real SPEND ledger entry.
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(3.dp)
                ) {
                    ModeTab("Add", selected = !isSpendMode, accent = AmberGlow) { isSpendMode = false }
                    ModeTab("Log Spend", selected = isSpendMode, accent = CoralGlow) { isSpendMode = true }
                }
                Spacer(Modifier.height(8.dp))

                Row {
                    listOf(10, 60, 100, 160).forEach { amount ->
                        QuickAmountChip(amount, accent = if (isSpendMode) CoralGlow else AmberGlow) {
                            if (isSpendMode) {
                                onLogSpend(amount) { success ->
                                    onLogPopupClosed(success, true, amount)
                                }
                            } else {
                                onLogAstrites(amount)
                                onLogPopupClosed(true, false, amount)
                            }
                            showLogPopup = false
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                }
            }
        }
    }

    MaterialTheme {
        Column(
            horizontalAlignment = if (expandLeft) Alignment.End else Alignment.Start,
            modifier = Modifier.onSizeChanged { newSize ->
                windowSize = newSize
                // Re-anchor the window around the (unmoved) bubble with the
                // REAL measured size - this is the moment the popup visually
                // expands left/right/up/down instead of clipping off-screen.
                applyWindowPosition()
            }
        ) {
            if (expandUp) {
                popup()
                bubble()
            } else {
                bubble()
                popup()
            }
        }
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) accent.copy(alpha = 0.28f) else Color.Transparent)
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            label,
            color = if (selected) TextPrimary else TextPrimary.copy(alpha = 0.55f),
            fontSize = MaterialTheme.typography.labelSmall.fontSize
        )
    }
}

@Composable
private fun QuickAmountChip(amount: Int, accent: Color = AmberGlow, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.18f))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text("+$amount", color = TextPrimary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
    }
}