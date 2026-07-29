package io.github.arglax.wuwalab.util

import android.view.SoundEffectConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/**
 * Shared "tap" feedback (system click sound + a light haptic tick) for the
 * app's buttons and other tappable controls.
 *
 * Deliberately reuses the platform's built-in click sound and haptic engine
 * (`View.playSoundEffect` + Compose's `HapticFeedback`) rather than bundling
 * custom audio assets - this respects the user's system volume/haptics
 * settings (including silent mode and "touch sounds" toggle) for free, and
 * needs no raw/ audio files shipped in the APK.
 *
 * Usage: `val feedback = rememberTapFeedback(); Button(onClick = { feedback(); doThing() })`
 */
@Composable
fun rememberTapFeedback(): () -> Unit {
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current
    return remember(view, haptics) {
        {
            view.playSoundEffect(SoundEffectConstants.CLICK)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
}

/**
 * A slightly stronger confirmation tick - for "big" moments like a
 * successful claim/check-in, not routine navigation taps.
 */
@Composable
fun rememberConfirmFeedback(): () -> Unit {
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current
    return remember(view, haptics) {
        {
            view.playSoundEffect(SoundEffectConstants.CLICK)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
}