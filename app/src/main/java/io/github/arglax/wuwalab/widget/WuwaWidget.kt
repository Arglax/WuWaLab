package io.github.arglax.wuwalab.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.arglax.wuwalab.MainActivity
import io.github.arglax.wuwalab.R
import io.github.arglax.wuwalab.data.AstriteRepository
import io.github.arglax.wuwalab.data.AstriteStats
import io.github.arglax.wuwalab.data.ProfileRepository
import io.github.arglax.wuwalab.data.WuwaRepository
import kotlinx.coroutines.flow.first

private const val TAG = "WuwaWidget"

// androidx.glance.color.ColorProvider(day, night) is the public, app-facing factory —
// unlike androidx.glance.unit.ColorProvider(...), which is restricted to Glance internals.
// This widget uses one fixed dark palette, so day and night get the same value.
private fun fixedColor(color: Color) = ColorProvider(day = color, night = color)

private val JadeBackground = fixedColor(Color(0xFF10241C))
private val GoldAccent = fixedColor(Color(0xFFD4AF37))
private val MintText = fixedColor(Color(0xFF9FD8B8))
private val WhiteText = fixedColor(Color.White)

// Scrims sit *on top of* widget_bg to keep the resource values legible.
// Alpha here is the scrim's own opacity, so it's the inverse of how visible
// the art underneath ends up: an 80% opaque scrim leaves ~20% of the art
// showing through.
private val ScrimHeavy = fixedColor(Color(0xF010241C))   // ~94% opaque -> art at ~6-10%
private val ScrimStrong = fixedColor(Color(0xE610241C))  // ~90% opaque -> art at ~10%
private val ScrimLight = fixedColor(Color(0x2610241C))   // ~15% opaque -> art at ~85-90%
private val ScrimNone = fixedColor(Color(0x0010241C))    // fully transparent -> art at ~100%

/** Everything the widget needs, snapshotted once per [provideGlance]/refresh. */
private data class WidgetData(
    val ign: String,
    val unionLevel: Int,
    val waveplates: Int,
    val millisUntilWaveplatesFull: Long,
    val crystals: Int,
    val astrites: Int,
    /** A paid custom background from the Widget Studio, when one is applied. */
    val customBackground: android.graphics.Bitmap? = null
)

class WuwaWidget : GlanceAppWidget() {

    // SizeMode.Exact reports the widget's *actual current* pixel size through
    // LocalSize.current (rather than snapping to one of a fixed set of
    // buckets), so the background can react precisely to how the user has
    // resized the widget on their home screen - full landscape vs. squished
    // into a small square.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = WuwaRepository(context)
        val profileRepo = ProfileRepository(context)
        val astriteRepo = AstriteRepository(context)

        val state = repo.waveplateStateFlow.first()
        val profile = profileRepo.profileFlow.first()
        val astriteEntries = astriteRepo.entriesFlow.first()

        val data = WidgetData(
            ign = profile.ign,
            unionLevel = profile.unionLevel,
            waveplates = state.computeCurrent(),
            millisUntilWaveplatesFull = state.millisUntilWaveplatesFull(),
            crystals = state.computeCrystals(),
            astrites = AstriteStats.totalGathered(astriteEntries),
            customBackground = io.github.arglax.wuwalab.data.WidgetImageProcessor.loadApplied(context)
        )

        provideContent {
            WidgetContent(data)
        }
    }

    companion object {
        /**
         * Call after any data refresh to force all widget instances to redraw.
         *
         * Previously this manually looked up GlanceIds via
         * [GlanceAppWidgetManager] and called `.update()` on each one using
         * whatever [Context] the caller happened to pass in (sometimes an
         * Activity context tied to a coroutine scope that could get
         * cancelled - e.g. mid-recomposition after a dialog collapses -
         * before the update actually landed). That's why editing waveplates
         * in-app sometimes left the widget showing stale numbers until it
         * was removed and re-added (which forces a fresh provideGlance()).
         *
         * Two fixes:
         *  1. Always resolve to [Context.getApplicationContext] so the
         *     update isn't tied to a short-lived Activity/dialog scope.
         *  2. Use Glance's own `GlanceAppWidget.updateAll(context)` extension
         *     instead of manually iterating GlanceIds - it's the
         *     library-maintained path and is more resilient to timing races
         *     between the DataStore write and the redraw.
         */
        suspend fun updateAll(context: Context) {
            val appContext = context.applicationContext
            try {
                WuwaWidget().updateAll(appContext)
            } catch (e: Exception) {
                // Never let a widget refresh failure crash the caller (e.g.
                // the waveplate "Update" button) - just log it so it's
                // debuggable instead of silently stale.
                Log.e(TAG, "Widget updateAll failed", e)
            }
        }
    }
}

@Composable
private fun WidgetContent(data: WidgetData) {
    // Built from an explicit Intent rather than the reified
    // actionStartActivity<MainActivity>() overload, since that generic
    // entry point isn't available in every glance-appwidget version -
    // this Intent-based one is the stable, version-safe API.
    val context = LocalContext.current
    // Tapping now opens a small chooser (Dashboard / Pull Planner / To-Do /
    // set an alarm) rather than dropping straight into the app.
    val openAppAction = actionStartActivity(
        Intent(context, WidgetActionActivity::class.java).apply {
            putExtra(EXTRA_CHOOSER_MODE, CHOOSER_MODE_MAIN)
        }
    )

    // Widget is roughly landscape once it's noticeably wider than it is
    // tall (e.g. resized to the full 4x2 target). Anything squarer than
    // that - dragged down into a small square/2x2 - is treated as compact.
    val size = LocalSize.current
    val isWideLandscape = size.width > size.height * 1.3f

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(JadeBackground) // fallback in case the art fails to decode
            .clickable(openAppAction) // tapping anywhere opens the action chooser
    ) {
        // --- Background artwork ---
        Image(
            // Uses the shop skin the player has equipped, falling back to the bundled art.
            // Priority: the player's own Widget Studio upload, then an
            // equipped shop skin, then the bundled artwork.
            provider = data.customBackground?.let { ImageProvider(it) }
                ?: ImageProvider(io.github.arglax.wuwalab.data.ShopArt.widgetBackgroundRes(context)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = GlanceModifier.fillMaxSize()
        )

        // --- Scrim: dims the art just enough that resource text stays readable ---
        if (isWideLandscape) {
            // Two-zone scrim: heavy on the left (~20% of the art shows
            // through, where the waveplate/IGN/values sit), fading to
            // almost no scrim on the right (~90-100% of the art shows),
            // so the character art stays visible on that side.
            Row(modifier = GlanceModifier.fillMaxSize()) {
                // 0-40%: held at full strength - this is where the text sits.
                Box(
                    modifier = GlanceModifier
                        .fillMaxHeight()
                        .width(size.width * 0.40f)
                        .background(ScrimStrong)
                ) {}
                // 40-60%: a true gradient, ramping the dimming away to nothing.
                Box(
                    modifier = GlanceModifier
                        .fillMaxHeight()
                        .width(size.width * 0.20f)
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.widget_scrim_ramp),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = GlanceModifier.fillMaxSize()
                    )
                }
                // 60-100%: absolutely no filter - the art is fully visible.
                Box(modifier = GlanceModifier.fillMaxHeight().width(size.width * 0.40f)) {}
            }
        } else {
            // Compact/near-square: a single strong, even scrim over the
            // whole widget so info stays legible no matter where it sits -
            // only ~10-20% of the art shows through.
            Box(modifier = GlanceModifier.fillMaxSize().background(ScrimHeavy)) {}
        }

        // --- Resource values, always drawn on top of the art + scrim ---
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // --- Profile Name/IGN ---
            Text(
                text = data.ign,
                style = TextStyle(color = WhiteText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = GlanceModifier.height(2.dp))

            // --- Union Level: icon + "UL" + value ---
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_union),
                    contentDescription = null,
                    modifier = GlanceModifier.width(13.dp).height(13.dp)
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = "UL ${data.unionLevel}",
                    style = TextStyle(color = GoldAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            // --- Waveplates: icon only + value, time until max ---
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_waveplate),
                    contentDescription = "Waveplates",
                    modifier = GlanceModifier.width(15.dp).height(15.dp)
                )
                Spacer(modifier = GlanceModifier.width(5.dp))
                Text(
                    text = "${data.waveplates}",
                    style = TextStyle(color = WhiteText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = formatUntilFull(data.millisUntilWaveplatesFull),
                    style = TextStyle(color = MintText, fontSize = 11.sp)
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // --- WP Crystals: icon only + value ---
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_waveplate_crystal),
                    contentDescription = "WP Crystals",
                    modifier = GlanceModifier.width(14.dp).height(14.dp)
                )
                Spacer(modifier = GlanceModifier.width(5.dp))
                Text(
                    text = "${data.crystals}",
                    style = TextStyle(color = MintText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // --- Astrites: icon only + value ---
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_astrite),
                    contentDescription = "Astrites",
                    modifier = GlanceModifier.width(14.dp).height(14.dp)
                )
                Spacer(modifier = GlanceModifier.width(5.dp))
                Text(
                    text = "${data.astrites}",
                    style = TextStyle(color = GoldAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                )
            }
        } // close resource values Column
    } // close outer Box (background + scrim + content)
}

private fun formatUntilFull(millisUntilFull: Long): String {
    if (millisUntilFull <= 0L) return "Full"
    val hours = millisUntilFull / 3_600_000L
    val minutes = (millisUntilFull % 3_600_000L) / 60_000L
    return if (hours > 0) "Full in ${hours}h ${minutes}m" else "Full in ${minutes}m"
}

class WuwaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WuwaWidget()

    // First instance ever pinned -> start the 10s live-refresh loop.
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetTickReceiver.start(context)
    }

    // Last instance removed -> stop it, so nothing keeps waking the device
    // for a widget that isn't on any home screen anymore.
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetTickReceiver.stop(context)
    }
}
