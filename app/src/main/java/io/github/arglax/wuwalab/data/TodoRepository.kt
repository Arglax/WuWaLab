package io.github.arglax.wuwalab.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.todoDataStore by preferencesDataStore(name = "wuwa_todos")

/** The chip shown on a to-do entry. Purely descriptive - quadrant placement is separate. */
enum class TodoTag(val label: String) {
    URGENT("Urgent"),
    NOT_URGENT("Not Urgent"),
    WILL_DO("Will Do"),
    OTHER("Other");

    companion object {
        fun fromStorage(value: String?): TodoTag =
            entries.firstOrNull { it.name == value } ?: OTHER
    }
}

/** Where the item sits on the Eisenhower Matrix. UNASSIGNED items wait in the tray. */
enum class EisenhowerQuadrant(val title: String, val subtitle: String) {
    DO_FIRST("Do First", "Urgent + Important"),
    SCHEDULE("Schedule", "Not Urgent + Important"),
    DELEGATE("Delegate", "Urgent + Not Important"),
    ELIMINATE("Eliminate", "Not Urgent + Not Important"),
    UNASSIGNED("Unassigned", "Not on the matrix yet");

    companion object {
        fun fromStorage(value: String?): EisenhowerQuadrant =
            entries.firstOrNull { it.name == value } ?: UNASSIGNED
    }
}

data class TodoItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val tag: TodoTag = TodoTag.OTHER,
    val quadrant: EisenhowerQuadrant = EisenhowerQuadrant.UNASSIGNED,
    val done: Boolean = false,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    /** Optional per-entry reminder. Null when the user hasn't set an alarm for this task. */
    val alarmEpochMs: Long? = null,
    /** Whether a system notification should fire at [alarmEpochMs]. Only meaningful when [alarmEpochMs] is set. */
    val notifyEnabled: Boolean = false
)

/**
 * Single store shared by BOTH the To-Do planner and the Eisenhower Matrix -
 * one list, two views. Checking an item off in one place is instantly
 * reflected in the other because they collect the same [itemsFlow].
 * Persistence mirrors [AstriteRepository]'s JSON-in-DataStore pattern.
 */
class TodoRepository(private val context: Context) {

    private val KEY_ITEMS = stringPreferencesKey("todo_items_json")

    val itemsFlow: Flow<List<TodoItem>> = context.todoDataStore.data.map { prefs ->
        parseItems(prefs[KEY_ITEMS] ?: "[]")
    }

    suspend fun getItemsOnce(): List<TodoItem> = itemsFlow.first()

    suspend fun upsert(item: TodoItem) {
        val current = getItemsOnce().associateBy { it.id }.toMutableMap()
        current[item.id] = item
        saveAll(current.values.sortedByDescending { it.createdAtEpochMs })
    }

    suspend fun setDone(id: String, done: Boolean) {
        saveAll(getItemsOnce().map { if (it.id == id) it.copy(done = done) else it })
    }

    suspend fun setQuadrant(id: String, quadrant: EisenhowerQuadrant) {
        saveAll(getItemsOnce().map { if (it.id == id) it.copy(quadrant = quadrant) else it })
    }

    suspend fun delete(id: String) {
        saveAll(getItemsOnce().filterNot { it.id == id })
    }

    suspend fun clearAll() {
        saveAll(emptyList())
    }

    // Keeps the Matrix home-screen widget (a read-only mirror of this same
    // data) in sync with every write, the same way WuwaWidget is refreshed
    // elsewhere after data changes - see MatrixWidget.updateAll's kdoc.
    private suspend fun saveAll(items: List<TodoItem>) {
        saveAllInternal(items)
        io.github.arglax.wuwalab.widget.MatrixWidget.updateAll(context)
    }

    private suspend fun saveAllInternal(items: List<TodoItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("description", item.description)
                    put("tag", item.tag.name)
                    put("quadrant", item.quadrant.name)
                    put("done", item.done)
                    put("createdAt", item.createdAtEpochMs)
                    if (item.alarmEpochMs != null) put("alarmEpochMs", item.alarmEpochMs)
                    put("notifyEnabled", item.notifyEnabled)
                }
            )
        }
        context.todoDataStore.edit { it[KEY_ITEMS] = arr.toString() }
    }

    private fun parseItems(json: String): List<TodoItem> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            TodoItem(
                id = o.optString("id", UUID.randomUUID().toString()),
                title = o.getString("title"),
                description = o.optString("description", ""),
                tag = TodoTag.fromStorage(o.optString("tag")),
                quadrant = EisenhowerQuadrant.fromStorage(o.optString("quadrant")),
                done = o.optBoolean("done", false),
                createdAtEpochMs = o.optLong("createdAt", System.currentTimeMillis()),
                alarmEpochMs = if (o.has("alarmEpochMs") && !o.isNull("alarmEpochMs")) o.optLong("alarmEpochMs") else null,
                notifyEnabled = o.optBoolean("notifyEnabled", false)
            )
        }.sortedByDescending { it.createdAtEpochMs }
    } catch (_: Exception) {
        emptyList()
    }
}