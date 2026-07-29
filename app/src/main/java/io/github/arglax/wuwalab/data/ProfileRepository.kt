package io.github.arglax.wuwalab.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.profileDataStore by preferencesDataStore(name = "wuwalab_profile")

/**
 * Which of the free/default avatar images is currently selected. This is
 * separate from [WuwaProfile.avatarUnlocked]/[WuwaProfile.customAvatarPath]
 * (the "upload your own photo" feature, available to everyone via Profile
 * Studio) - these three are simply bundled drawables anyone can pick between.
 */
enum class FreeAvatarId(val drawableName: String) {
    DEFAULT("ic_default_avatar"),
    ROVER("ic_avatar_rover"),
    BEACON("ic_avatar_beacon");

    companion object {
        fun fromStorageValue(value: String?): FreeAvatarId =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

/** The user's personalization profile, shown in the header at the top of the app. */
data class WuwaProfile(
    val ign: String = "Rover",
    val unionLevel: Int = 1,
    // Which of the bundled free avatars (default/Rover/Beacon) is selected.
    val selectedAvatar: FreeAvatarId = FreeAvatarId.DEFAULT,
    // True once the user has applied at least one custom photo via Profile
    // Studio. Until then this stays false and the app falls back to one of
    // the free avatars above.
    val avatarUnlocked: Boolean = false,
    // Once avatarUnlocked is true, this optionally points at a file the user
    // picked (copied into internal storage) - null means "use the default".
    val customAvatarPath: String? = null,
    // Every custom photo the user has ever applied via Profile Studio, kept
    // PERMANENTLY (even after switching to a different picture) so it can be
    // re-selected/swapped back in from the profile editor at any time. Keyed
    // by the original source photo, so re-framing the same upload updates its
    // existing entry in place instead of spawning duplicates.
    val customAvatarCollection: List<CustomAvatarEntry> = emptyList()
)

/** One permanently-owned custom avatar: the original upload plus its latest rendered/framed output. */
data class CustomAvatarEntry(val sourcePath: String, val renderedPath: String)

class ProfileRepository(private val context: Context) {

    private object Keys {
        val IGN = stringPreferencesKey("profile_ign")
        val UNION_LEVEL = intPreferencesKey("profile_union_level")
        val SELECTED_AVATAR = stringPreferencesKey("profile_selected_avatar")
        val AVATAR_UNLOCKED = booleanPreferencesKey("profile_avatar_unlocked")
        val CUSTOM_AVATAR_PATH = stringPreferencesKey("profile_custom_avatar_path")
        val CUSTOM_AVATAR_COLLECTION = stringPreferencesKey("profile_custom_avatar_collection_json")
    }

    val profileFlow: Flow<WuwaProfile> = context.profileDataStore.data.map { prefs ->
        WuwaProfile(
            ign = prefs[Keys.IGN] ?: "Rover",
            unionLevel = prefs[Keys.UNION_LEVEL] ?: 1,
            selectedAvatar = FreeAvatarId.fromStorageValue(prefs[Keys.SELECTED_AVATAR]),
            avatarUnlocked = prefs[Keys.AVATAR_UNLOCKED] ?: false,
            customAvatarPath = prefs[Keys.CUSTOM_AVATAR_PATH],
            customAvatarCollection = parseCollection(prefs[Keys.CUSTOM_AVATAR_COLLECTION])
        )
    }

    private fun parseCollection(raw: String?): List<CustomAvatarEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val source = o.optString("source", "").ifBlank { null } ?: return@mapNotNull null
                val rendered = o.optString("rendered", "").ifBlank { null } ?: return@mapNotNull null
                CustomAvatarEntry(source, rendered)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeCollection(entries: List<CustomAvatarEntry>): String {
        val arr = JSONArray()
        entries.forEach { entry ->
            arr.put(JSONObject().apply {
                put("source", entry.sourcePath)
                put("rendered", entry.renderedPath)
            })
        }
        return arr.toString()
    }

    suspend fun getProfileOnce(): WuwaProfile = profileFlow.first()

    suspend fun saveProfile(ign: String, unionLevel: Int) {
        context.profileDataStore.edit { prefs ->
            prefs[Keys.IGN] = ign.trim().ifBlank { "Rover" }.take(24)
            prefs[Keys.UNION_LEVEL] = unionLevel.coerceIn(1, 80)
        }
    }

    suspend fun setSelectedAvatar(avatar: FreeAvatarId) {
        context.profileDataStore.edit { prefs -> prefs[Keys.SELECTED_AVATAR] = avatar.name }
    }

    // Tracks whether the user has EVER applied a custom photo (i.e. owns at
    // least one permanent custom avatar). Deliberately NOT toggled off just
    // because the user is currently showing a free/shop avatar instead - it
    // only ever flips true (once a custom photo is applied) and stays true
    // for the life of the profile.
    suspend fun setAvatarUnlocked(unlocked: Boolean) {
        context.profileDataStore.edit { prefs -> prefs[Keys.AVATAR_UNLOCKED] = unlocked }
    }

    /** Sets which picture is ACTIVE right now. null = fall back to the selected free/shop avatar. Does not touch the permanent collection. */
    suspend fun setCustomAvatarPath(path: String?) {
        context.profileDataStore.edit { prefs ->
            if (path == null) prefs.remove(Keys.CUSTOM_AVATAR_PATH) else prefs[Keys.CUSTOM_AVATAR_PATH] = path
        }
    }

    /**
     * Adds/updates a permanent entry in the user's custom-avatar collection
     * and makes it the active picture. If [sourcePath] already has an entry
     * (i.e. this is a re-frame of a photo already owned), that entry's
     * rendered file is swapped in place rather than creating a duplicate -
     * every other source photo gets its own permanent, separate entry that
     * is never deleted just because the user switches away from it.
     */
    suspend fun addOrUpdateCustomAvatar(sourcePath: String, renderedPath: String) {
        context.profileDataStore.edit { prefs ->
            val existing = parseCollection(prefs[Keys.CUSTOM_AVATAR_COLLECTION]).toMutableList()
            val idx = existing.indexOfFirst { it.sourcePath == sourcePath }
            if (idx >= 0) existing[idx] = CustomAvatarEntry(sourcePath, renderedPath)
            else existing.add(CustomAvatarEntry(sourcePath, renderedPath))
            prefs[Keys.CUSTOM_AVATAR_COLLECTION] = serializeCollection(existing)
            prefs[Keys.CUSTOM_AVATAR_PATH] = renderedPath
            prefs[Keys.AVATAR_UNLOCKED] = true
        }
    }

    /** Swaps the active picture to an already-owned entry from the permanent collection - always free. */
    suspend fun setActiveCustomAvatar(renderedPath: String) {
        context.profileDataStore.edit { prefs -> prefs[Keys.CUSTOM_AVATAR_PATH] = renderedPath }
    }
}