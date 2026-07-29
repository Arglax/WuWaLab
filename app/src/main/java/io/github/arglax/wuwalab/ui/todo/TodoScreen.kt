package io.github.arglax.wuwalab.ui.todo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.arglax.wuwalab.data.TodoItem
import io.github.arglax.wuwalab.data.TodoRepository
import io.github.arglax.wuwalab.data.TodoTag
import io.github.arglax.wuwalab.data.WuwaRepository
import io.github.arglax.wuwalab.ui.components.ArgstriteAwardDialog
import io.github.arglax.wuwalab.ui.components.HelpButton
import io.github.arglax.wuwalab.ui.components.argstriteRewardFor
import io.github.arglax.wuwalab.ui.theme.AmberGlow
import io.github.arglax.wuwalab.ui.theme.CoralGlow
import io.github.arglax.wuwalab.ui.theme.CyanGlow
import io.github.arglax.wuwalab.ui.theme.EmeraldGlow
import io.github.arglax.wuwalab.ui.theme.GlassBorderSoft
import io.github.arglax.wuwalab.ui.theme.GlassSurface
import io.github.arglax.wuwalab.ui.theme.TextMuted
import io.github.arglax.wuwalab.ui.theme.TextPrimary
import io.github.arglax.wuwalab.ui.theme.TextSecondary
import io.github.arglax.wuwalab.ui.theme.VioletGlow
import kotlinx.coroutines.launch

private fun tagAccent(tag: TodoTag): Color = when (tag) {
    TodoTag.URGENT -> CoralGlow
    TodoTag.NOT_URGENT -> CyanGlow
    TodoTag.WILL_DO -> EmeraldGlow
    TodoTag.OTHER -> AmberGlow
}

private fun formatAlarmLabel(epochMs: Long): String {
    val fmt = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(epochMs))
}

/**
 * The To-Do planner page. Shares [TodoRepository] with the Eisenhower Matrix.
 *
 *  - "+ New Task" opens a clean popup (Title / Tag chips / Description).
 *  - Checkbox strikes the title through and fades the whole row.
 *  - Tapping a row smoothly expands it (AnimatedVisibility) to reveal the
 *    description and a persistent pencil icon for editing.
 *  - "Clear All" is double-confirmed: two distinct AlertDialogs must both be
 *    accepted before anything is deleted.
 */
@Composable
fun TodoScreen(
    todoRepo: TodoRepository,
    wuwaRepo: WuwaRepository? = null,
    achievementsRepo: io.github.arglax.wuwalab.data.AchievementsRepository? = null,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val items by todoRepo.itemsFlow.collectAsState(initial = emptyList())

    var expandedId by remember { mutableStateOf<String?>(null) }
    var editorTarget by remember { mutableStateOf<TodoItem?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var clearStep by remember { mutableStateOf(0) } // 0 = idle, 1 = first confirm, 2 = final confirm
    var argstriteAward by remember { mutableStateOf<Int?>(null) }
    var argstriteHint by remember { mutableStateOf<String?>(null) }

    argstriteAward?.let { amount ->
        ArgstriteAwardDialog(amount = amount, hintMessage = argstriteHint, onDismiss = { argstriteAward = null; argstriteHint = null })
    }

    if (showEditor) {
        TodoEditorDialog(
            initial = editorTarget,
            onDismiss = { showEditor = false; editorTarget = null },
            onSave = { item ->
                // A "todo log" is a fresh task being created, not an edit of an
                // existing one - editorTarget being null (before saving) is how
                // we tell the two apart.
                val isNewTask = editorTarget == null
                scope.launch { todoRepo.upsert(item) }
                io.github.arglax.wuwalab.notification.TodoReminderScheduler.schedule(context, item)
                if (isNewTask) {
                    wuwaRepo?.let { repo ->
                        scope.launch {
                            val baseReward = argstriteRewardFor(item.description)
                            val reward = repo.addPendingArgstrite(baseReward)
                            val hint = repo.consumeArgstriteHint(item.description.isNotBlank())
                            if (hint != null) {
                                argstriteHint = hint
                                argstriteAward = reward
                            }
                        }
                    }
                }
                showEditor = false
                editorTarget = null
            }
        )
    }

    // --- Double confirmation for Clear All ---
    if (clearStep == 1) {
        AlertDialog(
            onDismissRequest = { clearStep = 0 },
            title = { Text("Clear all tasks?") },
            text = { Text("This removes every task from the To-Do list AND the Eisenhower Matrix.") },
            confirmButton = { TextButton(onClick = { clearStep = 2 }) { Text("Continue", color = CoralGlow) } },
            dismissButton = { TextButton(onClick = { clearStep = 0 }) { Text("Cancel") } }
        )
    }
    if (clearStep == 2) {
        AlertDialog(
            onDismissRequest = { clearStep = 0 },
            title = { Text("Are you absolutely sure?") },
            text = { Text("There is no undo. ${items.size} task(s) will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    items.forEach { io.github.arglax.wuwalab.notification.TodoReminderScheduler.cancel(context, it.id) }
                    scope.launch { todoRepo.clearAll() }
                    clearStep = 0
                }) { Text("Delete Everything", color = CoralGlow, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { clearStep = 0 }) { Text("Keep My Tasks") } }
        )
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "To-Do Planner",
                fontWeight = FontWeight.Bold,
                fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            HelpButton(
                title = "To-Do Planner Help",
                body = "Tap + to add a task with a title, a tag (Urgent / Not Urgent / Will Do " +
                        "/ Other), and an optional description.\n\n" +
                        "You can also turn on an optional per-task Alarm and pick any date/time - " +
                        "toggle Notify alongside it if you want a system notification when it fires.\n\n" +
                        "Tap a task row to expand it and reveal its description plus a pencil icon " +
                        "for editing. Check the box to mark it done - it strikes through and fades.\n\n" +
                        "This list is shared with the Eisenhower Matrix tab.\n\n" +
                        "The trash icon clears every task - it needs two separate confirmations " +
                        "since it can't be undone."
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(GlassSurface)
                    .border(1.dp, CoralGlow.copy(alpha = 0.5f), CircleShape)
                    .clickable(enabled = items.isNotEmpty()) { clearStep = 1 },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear all tasks", tint = if (items.isEmpty()) TextMuted else CoralGlow, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(VioletGlow.copy(alpha = 0.25f))
                    .border(1.dp, VioletGlow.copy(alpha = 0.6f), CircleShape)
                    .clickable { editorTarget = null; showEditor = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New task", tint = TextPrimary, modifier = Modifier.size(22.dp))
            }
        }

        Spacer(Modifier.height(14.dp))

        if (items.isEmpty()) {
            Text(
                "No tasks yet - tap + to plan your first one. Tasks you add here also appear on the Eisenhower Matrix.",
                color = TextSecondary
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items, key = { it.id }) { item ->
                    TodoRow(
                        item = item,
                        expanded = expandedId == item.id,
                        onToggleExpand = { expandedId = if (expandedId == item.id) null else item.id },
                        onCheckedChange = { done ->
                            scope.launch { todoRepo.setDone(item.id, done) }
                            if (done) {
                                io.github.arglax.wuwalab.notification.TodoReminderScheduler.cancel(context, item.id)
                                scope.launch { achievementsRepo?.recordTodoCompleted(hadNote = item.description.isNotBlank()) }
                            }
                        },
                        onEdit = { editorTarget = item; showEditor = true },
                        onDelete = {
                            scope.launch { todoRepo.delete(item.id) }
                            io.github.arglax.wuwalab.notification.TodoReminderScheduler.cancel(context, item.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TodoRow(
    item: TodoItem,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = tagAccent(item.tag)
    val rowAlpha by animateFloatAsState(if (item.done) 0.45f else 1f, label = "todoFade")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(rowAlpha)
            .clip(RoundedCornerShape(16.dp))
            .background(GlassSurface)
            .border(1.dp, if (expanded) accent.copy(alpha = 0.6f) else GlassBorderSoft, RoundedCornerShape(16.dp))
            .clickable { onToggleExpand() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .animateContentSize()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = item.done,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(checkedColor = accent)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(accent.copy(alpha = 0.18f))
                            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(item.tag.label, color = accent, fontSize = MaterialTheme.typography.labelSmall.fontSize)
                    }
                    if (item.alarmEpochMs != null && item.notifyEnabled) {
                        Spacer(Modifier.width(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(VioletGlow.copy(alpha = 0.16f))
                                .border(1.dp, VioletGlow.copy(alpha = 0.5f), RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Filled.Notifications,
                                contentDescription = "Reminder set",
                                tint = VioletGlow,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                formatAlarmLabel(item.alarmEpochMs),
                                color = VioletGlow,
                                fontSize = MaterialTheme.typography.labelSmall.fontSize
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 8.dp)) {
                Text(
                    item.description.ifBlank { "No description." },
                    color = TextSecondary,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Persistent pencil - always visible while the row is expanded.
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(VioletGlow.copy(alpha = 0.22f))
                            .border(1.dp, VioletGlow.copy(alpha = 0.55f), CircleShape)
                            .clickable { onEdit() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit task", tint = TextPrimary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    TextButton(onClick = onDelete) { Text("Delete", color = CoralGlow) }
                }
            }
        }
    }
}

/** Clean creation/edit popup: Title, Tag chips (Urgent / Not Urgent / Will Do / Other), Description, optional Alarm/Notify. */
@Composable
private fun TodoEditorDialog(
    initial: TodoItem?,
    onDismiss: () -> Unit,
    onSave: (TodoItem) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var tag by remember { mutableStateOf(initial?.tag ?: TodoTag.OTHER) }
    var alarmEnabled by remember { mutableStateOf(initial?.alarmEpochMs != null) }
    var alarmEpochMs by remember { mutableStateOf(initial?.alarmEpochMs) }
    var notifyEnabled by remember { mutableStateOf(initial?.notifyEnabled ?: (initial?.alarmEpochMs != null)) }

    fun launchPicker() {
        val cal = java.util.Calendar.getInstance()
        alarmEpochMs?.let { cal.timeInMillis = it }
        android.app.DatePickerDialog(
            context,
            { _, year, month, day ->
                cal.set(java.util.Calendar.YEAR, year)
                cal.set(java.util.Calendar.MONTH, month)
                cal.set(java.util.Calendar.DAY_OF_MONTH, day)
                android.app.TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
                        cal.set(java.util.Calendar.MINUTE, minute)
                        cal.set(java.util.Calendar.SECOND, 0)
                        alarmEpochMs = cal.timeInMillis
                    },
                    cal.get(java.util.Calendar.HOUR_OF_DAY),
                    cal.get(java.util.Calendar.MINUTE),
                    false
                ).show()
            },
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Task" else "Edit Task") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    label = { Text("Title") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text("Tag", color = TextSecondary, fontSize = MaterialTheme.typography.labelMedium.fontSize)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TodoTag.entries.forEach { t ->
                        val selected = t == tag
                        val accent = tagAccent(t)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) accent.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.06f))
                                .border(1.dp, if (selected) accent else GlassBorderSoft, RoundedCornerShape(50))
                                .clickable { tag = t }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                t.label,
                                color = if (selected) accent else TextSecondary,
                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(500) },
                    label = { Text("Description") },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(14.dp))
                androidx.compose.material3.HorizontalDivider(color = GlassBorderSoft)
                Spacer(Modifier.height(10.dp))

                // --- Optional per-entry alarm + notify (Task: reminders) ---
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Notifications, contentDescription = null, tint = VioletGlow, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Set Alarm", color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(
                        checked = alarmEnabled,
                        onCheckedChange = { checked ->
                            alarmEnabled = checked
                            if (checked && alarmEpochMs == null) launchPicker()
                            if (!checked) notifyEnabled = false
                        },
                        colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = VioletGlow)
                    )
                }

                if (alarmEnabled) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { launchPicker() }) {
                            Text(
                                alarmEpochMs?.let { formatAlarmLabel(it) } ?: "Pick date & time",
                                color = VioletGlow
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Notify (system notification)", color = TextSecondary, modifier = Modifier.weight(1f))
                        androidx.compose.material3.Switch(
                            checked = notifyEnabled,
                            onCheckedChange = { notifyEnabled = it },
                            colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = EmeraldGlow)
                        )
                    }
                    Text(
                        "Both the alarm and notification are optional and per-task - pick whatever time works for this reminder.",
                        color = TextMuted,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val base = initial ?: TodoItem(title = title.trim())
                    onSave(
                        base.copy(
                            title = title.trim(),
                            description = description.trim(),
                            tag = tag,
                            alarmEpochMs = if (alarmEnabled) alarmEpochMs else null,
                            notifyEnabled = alarmEnabled && notifyEnabled && alarmEpochMs != null
                        )
                    )
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = VioletGlow)
            ) { Text(if (initial == null) "Add Task" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}