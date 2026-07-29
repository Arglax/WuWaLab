package io.github.arglax.wuwalab.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Where a tap on the Matrix widget lands. */
enum class MatrixWidgetTapTarget(val label: String, val description: String) {
    ASK("Ask me", "Show the chooser - Open Matrix or Open To-Do"),
    MATRIX("Matrix", "Go straight to the four quadrants"),
    TODO("To-Do", "Go straight to the plain task list")
}

data class MatrixWidgetSettings(
    val showHeader: Boolean = true,
    val headerTitle: String = "Eisenhower Matrix",
    val itemsPerQuadrant: Int = 3,
    val showCompleted: Boolean = false,
    val tapTarget: MatrixWidgetTapTarget = MatrixWidgetTapTarget.ASK
)

// Its own store, separate from the main app preferences, so widget settings
// can never collide with (or be wiped alongside) unrelated app state.
private val Context.matrixWidgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "matrix_widget_prefs")

class MatrixWidgetPrefs(private val context: Context) {

    private val KEY_SHOW_HEADER = booleanPreferencesKey("matrix_widget_show_header")
    private val KEY_HEADER_TITLE = stringPreferencesKey("matrix_widget_header_title")
    private val KEY_ITEMS_PER_QUADRANT = intPreferencesKey("matrix_widget_items_per_quadrant")
    private val KEY_SHOW_COMPLETED = booleanPreferencesKey("matrix_widget_show_completed")
    private val KEY_TAP_TARGET = stringPreferencesKey("matrix_widget_tap_target")

    val settingsFlow: Flow<MatrixWidgetSettings> = context.matrixWidgetDataStore.data.map { prefs ->
        MatrixWidgetSettings(
            showHeader = prefs[KEY_SHOW_HEADER] ?: true,
            headerTitle = (prefs[KEY_HEADER_TITLE] ?: "Eisenhower Matrix").ifBlank { "Eisenhower Matrix" },
            itemsPerQuadrant = (prefs[KEY_ITEMS_PER_QUADRANT] ?: 3).coerceIn(1, 6),
            showCompleted = prefs[KEY_SHOW_COMPLETED] ?: false,
            tapTarget = runCatching {
                MatrixWidgetTapTarget.valueOf(prefs[KEY_TAP_TARGET] ?: MatrixWidgetTapTarget.ASK.name)
            }.getOrDefault(MatrixWidgetTapTarget.ASK)
        )
    }

    suspend fun getOnce(): MatrixWidgetSettings = settingsFlow.first()

    suspend fun save(settings: MatrixWidgetSettings) {
        context.matrixWidgetDataStore.edit {
            it[KEY_SHOW_HEADER] = settings.showHeader
            it[KEY_HEADER_TITLE] = settings.headerTitle.trim().ifBlank { "Eisenhower Matrix" }
            it[KEY_ITEMS_PER_QUADRANT] = settings.itemsPerQuadrant.coerceIn(1, 6)
            it[KEY_SHOW_COMPLETED] = settings.showCompleted
            it[KEY_TAP_TARGET] = settings.tapTarget.name
        }
    }
}
