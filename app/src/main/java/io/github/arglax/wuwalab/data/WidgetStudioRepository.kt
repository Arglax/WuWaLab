package io.github.arglax.wuwalab.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import kotlin.math.max

/** What one custom widget background costs, in Argstrites. */
const val WIDGET_CUSTOM_UPLOAD_COST = 50

/** The one-time welcome gift handed out the first time the Widget page is opened. */
const val WIDGET_STUDIO_WELCOME_GRANT = 50

/**
 * ---------------------------------------------------------------------------
 * Widget Studio - custom home-screen widget backgrounds
 * ---------------------------------------------------------------------------
 *
 * The player picks a photo, frames it (zoom + drag), previews it as both a
 * wide and a square widget, and pays 20 Argstrites to apply it. The framed
 * result is flattened into a single 1024x576 PNG in the app's private storage,
 * which is what the widget actually draws - so the widget never has to decode
 * the original photo, apply a transform, or ask for storage permission.
 *
 * Stored as a plain JSON file (same reasoning as [ShopStore]): the widget runs
 * outside a coroutine and needs a synchronous read.
 */
data class WidgetStudioState(
    /** The imported, un-cropped source photo. */
    val sourcePath: String? = null,
    /** The flattened, paid-for image the widget is currently drawing. */
    val appliedPath: String? = null,
    val zoom: Float = 1f,
    /** Framing offsets as a fraction of the frame, -1f..1f. */
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val welcomeGrantClaimed: Boolean = false,
    val uploadsPurchased: Int = 0
)

object WidgetStudioStore {

    private val _state = MutableStateFlow(WidgetStudioState())
    val state: StateFlow<WidgetStudioState> = _state.asStateFlow()

    private var loaded = false

    private fun file(context: Context) = File(context.filesDir, "widget_studio.json")

    @Synchronized
    fun ensureLoaded(context: Context): WidgetStudioState {
        if (!loaded) {
            _state.value = readFile(context)
            loaded = true
        }
        return _state.value
    }

    /** Always hits disk - used by the widget process, which has no shared memory with the app. */
    fun readFile(context: Context): WidgetStudioState = try {
        val f = file(context)
        if (!f.exists()) WidgetStudioState() else {
            val o = JSONObject(f.readText())
            WidgetStudioState(
                sourcePath = o.optString("sourcePath", "").ifBlank { null },
                appliedPath = o.optString("appliedPath", "").ifBlank { null },
                zoom = o.optDouble("zoom", 1.0).toFloat(),
                offsetX = o.optDouble("offsetX", 0.0).toFloat(),
                offsetY = o.optDouble("offsetY", 0.0).toFloat(),
                welcomeGrantClaimed = o.optBoolean("welcomeGrantClaimed", false),
                uploadsPurchased = o.optInt("uploadsPurchased", 0)
            )
        }
    } catch (_: Exception) {
        WidgetStudioState()
    }

    @Synchronized
    fun update(context: Context, transform: (WidgetStudioState) -> WidgetStudioState) {
        val next = transform(ensureLoaded(context))
        _state.value = next
        loaded = true
        runCatching {
            val o = JSONObject().apply {
                put("sourcePath", next.sourcePath ?: "")
                put("appliedPath", next.appliedPath ?: "")
                put("zoom", next.zoom.toDouble())
                put("offsetX", next.offsetX.toDouble())
                put("offsetY", next.offsetY.toDouble())
                put("welcomeGrantClaimed", next.welcomeGrantClaimed)
                put("uploadsPurchased", next.uploadsPurchased)
            }
            file(context).writeText(o.toString())
        }
    }
}

/**
 * Turns a picked photo plus a zoom/offset framing into the flat image the
 * widget draws. Keeping the maths in one place is what makes the in-app
 * preview and the real widget agree with each other.
 */
object WidgetImageProcessor {

    const val OUT_W = 1024
    const val OUT_H = 576
    private const val MAX_SOURCE_EDGE = 2048

    /** Copies the picked image into private storage, downscaled to something sane. */
    fun importSource(context: Context, uri: Uri): File? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / sample > MAX_SOURCE_EDGE) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        val out = File(context.filesDir, "widget_source.png")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        out
    }.getOrNull()

    fun decodeSource(state: WidgetStudioState): Bitmap? {
        val path = state.sourcePath ?: return null
        val f = File(path)
        if (!f.exists()) return null
        return runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
    }

    /**
     * Flattens the framed photo into a 1024x576 PNG.
     *
     * The framing maths, matched exactly by the on-screen preview:
     *  1. scale the photo so it COVERS the 16:9 frame (this is scale = 1x),
     *  2. multiply by the player's zoom,
     *  3. shift by the offsets, expressed as a fraction of the frame.
     */
    fun render(context: Context, state: WidgetStudioState): File? = runCatching {
        val src = decodeSource(state) ?: return null
        val out = Bitmap.createBitmap(OUT_W, OUT_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val cover = max(OUT_W.toFloat() / src.width, OUT_H.toFloat() / src.height)
        val total = cover * state.zoom.coerceIn(1f, 4f)
        val drawW = src.width * total
        val drawH = src.height * total
        val dx = (OUT_W - drawW) / 2f + state.offsetX * OUT_W
        val dy = (OUT_H - drawH) / 2f + state.offsetY * OUT_H

        val matrix = Matrix().apply {
            setScale(total, total)
            postTranslate(dx, dy)
        }
        canvas.drawBitmap(src, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        src.recycle()

        val file = File(context.filesDir, "widget_custom.png")
        file.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 95, it) }
        out.recycle()
        file
    }.getOrNull()

    /** The applied custom background, or null when the player hasn't set one. */
    fun loadApplied(context: Context): Bitmap? {
        val path = WidgetStudioStore.readFile(context).appliedPath ?: return null
        val f = File(path)
        if (!f.exists()) return null
        return runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
    }
}

/** Outcome of paying for and applying a custom background. */
sealed class WidgetApplyResult {
    data class Success(val remainingArgstrites: Int) : WidgetApplyResult()
    data class NotEnoughArgstrites(val needed: Int, val balance: Int) : WidgetApplyResult()
    data object NoImageChosen : WidgetApplyResult()
    data object RenderFailed : WidgetApplyResult()
}

class WidgetStudioRepository(
    private val context: Context,
    private val wuwaRepo: WuwaRepository
) {

    val stateFlow: StateFlow<WidgetStudioState> = WidgetStudioStore.state

    fun refresh() {
        WidgetStudioStore.ensureLoaded(context)
    }

    /**
     * Hands out the one-time welcome gift the first time the Widget page is
     * opened, and reports whether it actually granted anything so the screen
     * knows whether to show the explainer popup.
     */
    suspend fun claimWelcomeGrantIfNeeded(): Boolean {
        val current = WidgetStudioStore.ensureLoaded(context)
        if (current.welcomeGrantClaimed) return false
        wuwaRepo.addRadiantAstrite(WIDGET_STUDIO_WELCOME_GRANT)
        WidgetStudioStore.update(context) { it.copy(welcomeGrantClaimed = true) }
        return true
    }

    /** Imports a picked photo and resets the framing to a neutral starting point. */
    fun setSource(uri: Uri): Boolean {
        val file = WidgetImageProcessor.importSource(context, uri) ?: return false
        WidgetStudioStore.update(context) {
            it.copy(sourcePath = file.absolutePath, zoom = 1f, offsetX = 0f, offsetY = 0f)
        }
        return true
    }

    fun setFraming(zoom: Float, offsetX: Float, offsetY: Float) {
        WidgetStudioStore.update(context) {
            it.copy(
                zoom = zoom.coerceIn(1f, 4f),
                offsetX = offsetX.coerceIn(-1f, 1f),
                offsetY = offsetY.coerceIn(-1f, 1f)
            )
        }
    }

    /**
     * Charges [WIDGET_CUSTOM_UPLOAD_COST] Argstrites and applies the framed
     * photo. Nothing is deducted unless the render succeeds, and nothing is
     * rendered unless the deduction succeeds - so a failure never costs
     * anything, and a charge never leaves the player without the background.
     */
    suspend fun purchaseAndApply(): WidgetApplyResult {
        val state = WidgetStudioStore.ensureLoaded(context)
        if (state.sourcePath == null) return WidgetApplyResult.NoImageChosen

        val balance = wuwaRepo.getRadiantAstriteOnce()
        if (balance < WIDGET_CUSTOM_UPLOAD_COST) {
            return WidgetApplyResult.NotEnoughArgstrites(WIDGET_CUSTOM_UPLOAD_COST, balance)
        }
        if (!wuwaRepo.trySpendRadiantAstrite(WIDGET_CUSTOM_UPLOAD_COST)) {
            return WidgetApplyResult.NotEnoughArgstrites(WIDGET_CUSTOM_UPLOAD_COST, wuwaRepo.getRadiantAstriteOnce())
        }

        val rendered = WidgetImageProcessor.render(context, state)
        if (rendered == null) {
            // Refund - the player should never pay for a failed render.
            // Uses refundRadiantAstrite (not addRadiantAstrite): the spend
            // above was never bonus-scaled, so reversing it must not be
            // either, or an active Bonus % would net the player free
            // Argstrites on every failed render.
            wuwaRepo.refundRadiantAstrite(WIDGET_CUSTOM_UPLOAD_COST)
            return WidgetApplyResult.RenderFailed
        }

        WidgetStudioStore.update(context) {
            it.copy(appliedPath = rendered.absolutePath, uploadsPurchased = it.uploadsPurchased + 1)
        }
        return WidgetApplyResult.Success(wuwaRepo.getRadiantAstriteOnce())
    }

    /** Reverts to the bundled/shop artwork. Free - only applying costs anything. */
    fun clearCustom() {
        WidgetStudioStore.update(context) { it.copy(appliedPath = null) }
    }
}