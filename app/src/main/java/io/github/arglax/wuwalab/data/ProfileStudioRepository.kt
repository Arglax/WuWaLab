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

/** What one custom profile picture costs, in Argstrites. */
const val PROFILE_CUSTOM_UPLOAD_COST = 50

/**
 * ---------------------------------------------------------------------------
 * Profile Studio - custom square profile pictures
 * ---------------------------------------------------------------------------
 *
 * The square sibling of the Widget Studio: pick a photo, frame it (pinch to
 * zoom, drag to reposition), preview it as it will appear in the profile
 * header, and pay [PROFILE_CUSTOM_UPLOAD_COST] Argstrites to apply it.
 *
 * Important difference from Widget Studio: re-framing (zoom/position only,
 * same photo) and re-applying it again is FREE. The charge only applies the
 * first time a given source photo is applied - [purchasedSourcePath] is the
 * bookkeeping that makes that distinction. Uploading a genuinely different
 * photo always costs [PROFILE_CUSTOM_UPLOAD_COST] again.
 */
data class ProfileStudioState(
    val sourcePath: String? = null,
    val appliedPath: String? = null,
    val zoom: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    // The sourcePath that Argstrites have already been spent on. As long as
    // sourcePath == purchasedSourcePath, re-framing and re-applying costs
    // nothing - it's the same photo, just repositioned.
    val purchasedSourcePath: String? = null
)

object ProfileStudioStore {

    private val _state = MutableStateFlow(ProfileStudioState())
    val state: StateFlow<ProfileStudioState> = _state.asStateFlow()

    private var loaded = false

    private fun file(context: Context) = File(context.filesDir, "profile_studio.json")

    @Synchronized
    fun ensureLoaded(context: Context): ProfileStudioState {
        if (!loaded) {
            _state.value = readFile(context)
            loaded = true
        }
        return _state.value
    }

    fun readFile(context: Context): ProfileStudioState = try {
        val f = file(context)
        if (!f.exists()) ProfileStudioState() else {
            val o = JSONObject(f.readText())
            ProfileStudioState(
                sourcePath = o.optString("sourcePath", "").ifBlank { null },
                appliedPath = o.optString("appliedPath", "").ifBlank { null },
                zoom = o.optDouble("zoom", 1.0).toFloat(),
                offsetX = o.optDouble("offsetX", 0.0).toFloat(),
                offsetY = o.optDouble("offsetY", 0.0).toFloat(),
                purchasedSourcePath = o.optString("purchasedSourcePath", "").ifBlank { null }
            )
        }
    } catch (_: Exception) {
        ProfileStudioState()
    }

    @Synchronized
    fun update(context: Context, transform: (ProfileStudioState) -> ProfileStudioState) {
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
                put("purchasedSourcePath", next.purchasedSourcePath ?: "")
            }
            file(context).writeText(o.toString())
        }
    }
}

/** Square (1:1) equivalent of WidgetImageProcessor. */
object ProfileImageProcessor {

    const val OUT_SIZE = 512
    private const val MAX_SOURCE_EDGE = 2048

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

        val out = File(context.filesDir, "profile_source.png")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        out
    }.getOrNull()

    fun decodeSource(state: ProfileStudioState): Bitmap? {
        val path = state.sourcePath ?: return null
        if (!File(path).exists()) return null
        return runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
    }

    /**
     * Renders straight to a UNIQUE filename per call (not a fixed
     * "profile_custom.png") - this is what actually makes "re-frame and
     * re-apply" visible. A fixed filename means re-applying the SAME photo,
     * just repositioned, produces an identical [ProfileStudioState.appliedPath]
     * string; since [WuwaProfile] is a data class, an unchanged string means
     * an unchanged (`equals()`-true) profile value, which Compose's
     * `collectAsState()` silently drops as a no-op - so the header and
     * profile summary never actually re-render with the new framing, even
     * though the file on disk was rewritten. A fresh filename every time
     * guarantees the state genuinely changes, so every downstream reader
     * (header, profile summary, edit-profile picker) picks it up.
     */
    fun render(context: Context, state: ProfileStudioState): File? = runCatching {
        val src = decodeSource(state) ?: return null
        val out = Bitmap.createBitmap(OUT_SIZE, OUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val cover = max(OUT_SIZE.toFloat() / src.width, OUT_SIZE.toFloat() / src.height)
        val total = cover * state.zoom.coerceIn(1f, 4f)
        val drawW = src.width * total
        val drawH = src.height * total
        val dx = (OUT_SIZE - drawW) / 2f + state.offsetX * OUT_SIZE
        val dy = (OUT_SIZE - drawH) / 2f + state.offsetY * OUT_SIZE

        val matrix = Matrix().apply {
            setScale(total, total)
            postTranslate(dx, dy)
        }
        canvas.drawBitmap(src, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        src.recycle()

        val file = File(context.filesDir, "profile_custom_${System.currentTimeMillis()}.png")
        file.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 95, it) }
        out.recycle()
        file
    }.getOrNull()

    fun loadApplied(context: Context): Bitmap? {
        val path = ProfileStudioStore.readFile(context).appliedPath ?: return null
        if (!File(path).exists()) return null
        return runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
    }
}

/** Outcome of applying a custom profile picture. */
sealed class ProfileApplyResult {
    data class Success(val remainingArgstrites: Int, val wasFree: Boolean) : ProfileApplyResult()
    data class NotEnoughArgstrites(val needed: Int, val balance: Int) : ProfileApplyResult()
    data object NoImageChosen : ProfileApplyResult()
    data object RenderFailed : ProfileApplyResult()
}

class ProfileStudioRepository(
    private val context: Context,
    private val wuwaRepo: WuwaRepository,
    private val profileRepo: ProfileRepository
) {

    val stateFlow: StateFlow<ProfileStudioState> = ProfileStudioStore.state

    fun refresh() {
        ProfileStudioStore.ensureLoaded(context)
    }

    fun setSource(uri: Uri): Boolean {
        val file = ProfileImageProcessor.importSource(context, uri) ?: return false
        ProfileStudioStore.update(context) {
            it.copy(sourcePath = file.absolutePath, zoom = 1f, offsetX = 0f, offsetY = 0f)
        }
        return true
    }

    fun setFraming(zoom: Float, offsetX: Float, offsetY: Float) {
        ProfileStudioStore.update(context) {
            it.copy(
                zoom = zoom.coerceIn(1f, 4f),
                offsetX = offsetX.coerceIn(-1f, 1f),
                offsetY = offsetY.coerceIn(-1f, 1f)
            )
        }
    }

    /** True when re-applying the current source photo would be free (already paid for). */
    fun isCurrentSourceAlreadyPurchased(): Boolean {
        val s = ProfileStudioStore.ensureLoaded(context)
        return s.sourcePath != null && s.sourcePath == s.purchasedSourcePath
    }

    /**
     * Charges [PROFILE_CUSTOM_UPLOAD_COST] Argstrites only the first time a
     * given source photo is applied. Re-framing (zoom/position) the SAME
     * photo and applying again afterwards is free - only a genuinely new
     * upload resets [ProfileStudioState.purchasedSourcePath] and costs again.
     */
    suspend fun applyCurrentFraming(): ProfileApplyResult {
        val state = ProfileStudioStore.ensureLoaded(context)
        if (state.sourcePath == null) return ProfileApplyResult.NoImageChosen

        val alreadyPurchased = state.sourcePath == state.purchasedSourcePath
        val balance = wuwaRepo.getRadiantAstriteOnce()

        if (!alreadyPurchased) {
            if (balance < PROFILE_CUSTOM_UPLOAD_COST) {
                return ProfileApplyResult.NotEnoughArgstrites(PROFILE_CUSTOM_UPLOAD_COST, balance)
            }
            if (!wuwaRepo.trySpendRadiantAstrite(PROFILE_CUSTOM_UPLOAD_COST)) {
                return ProfileApplyResult.NotEnoughArgstrites(PROFILE_CUSTOM_UPLOAD_COST, wuwaRepo.getRadiantAstriteOnce())
            }
        }

        val rendered = ProfileImageProcessor.render(context, state)
        if (rendered == null) {
            // refundRadiantAstrite (not addRadiantAstrite): the spend above
            // was never bonus-scaled, so reversing it must not be either.
            if (!alreadyPurchased) wuwaRepo.refundRadiantAstrite(PROFILE_CUSTOM_UPLOAD_COST)
            return ProfileApplyResult.RenderFailed
        }

        // Filenames are unique per render now (see ProfileImageProcessor.render's
        // kdoc). If this is a re-frame of an already-owned source photo, the
        // OLD rendered file for that same source is now safely superseded and
        // can be deleted - profileRepo.addOrUpdateCustomAvatar swaps it out
        // for the new one in the same collection slot. If it's a brand-new
        // source photo, nothing is deleted: it becomes its own permanent,
        // separate entry in the user's avatar collection.
        val previousRenderedForThisSource = if (alreadyPurchased) {
            profileRepo.getProfileOnce().customAvatarCollection
                .firstOrNull { it.sourcePath == state.sourcePath }?.renderedPath
        } else null

        ProfileStudioStore.update(context) {
            it.copy(appliedPath = rendered.absolutePath, purchasedSourcePath = state.sourcePath)
        }
        profileRepo.addOrUpdateCustomAvatar(state.sourcePath, rendered.absolutePath)
        if (previousRenderedForThisSource != null && previousRenderedForThisSource != rendered.absolutePath) {
            runCatching { File(previousRenderedForThisSource).delete() }
        }
        return ProfileApplyResult.Success(wuwaRepo.getRadiantAstriteOnce(), wasFree = alreadyPurchased)
    }

    /**
     * Steps out of the custom photo back to a free/shop avatar. The photo
     * itself is NOT deleted - it stays permanently in the user's avatar
     * collection ([ProfileRepository.customAvatarCollection]) and can be
     * swapped back in for free from the profile editor at any time.
     */
    suspend fun clearCustom() {
        profileRepo.setCustomAvatarPath(null)
    }
}