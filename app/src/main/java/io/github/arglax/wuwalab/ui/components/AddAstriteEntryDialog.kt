package io.github.arglax.wuwalab.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.Alignment
import io.github.arglax.wuwalab.util.rememberTapFeedback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.arglax.wuwalab.data.AstriteEntry
import io.github.arglax.wuwalab.ui.theme.AmberGlow
import io.github.arglax.wuwalab.ui.theme.TextMuted
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Used both for logging a new day and for editing/backfilling a past one, or
 * pre-logging a future date (e.g. a scheduled livestream code redemption) -
 * the date picker has no min/max bound, so any date works either direction.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AddAstriteEntryDialog(
    initial: AstriteEntry?,
    onDismiss: () -> Unit,
    onSave: (AstriteEntry, overwrite: Boolean) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val feedback = rememberTapFeedback()
    var selectedDate by remember {
        mutableStateOf(initial?.date ?: LocalDate.now())
    }
    var amountText by remember { mutableStateOf(initial?.amount?.toString() ?: "") }
    var sourceText by remember { mutableStateOf(initial?.source ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    // Editing an existing entry defaults to overwrite (you're correcting that
    // exact entry); logging fresh defaults to append, so a same-day re-log
    // (e.g. an extra event redemption) stacks instead of clobbering.
    var overwrite by remember { mutableStateOf(initial != null) }

    val amountValue = amountText.toIntOrNull()
    val amountError = amountText.isNotBlank() && amountValue == null

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("Select") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Log Astrites" else "Edit Entry", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Date",
                    color = TextMuted,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedButton(onClick = { feedback(); showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${selectedDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())}, " +
                            selectedDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Change date", modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Astrites Gained",
                    color = TextMuted,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() }.take(6) },
                    placeholder = { Text("0") },
                    leadingIcon = {
                        Icon(Icons.Filled.Tag, contentDescription = null, tint = AmberGlow, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = { Text("Astrites", color = TextMuted, fontSize = MaterialTheme.typography.labelSmall.fontSize) },
                    singleLine = true,
                    isError = amountError,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = sourceText,
                    onValueChange = { sourceText = it.take(40) },
                    label = { Text("Source (Optional)") },
                    placeholder = { Text("e.g. Daily Login, Event") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { feedback(); overwrite = !overwrite }
                ) {
                    Checkbox(
                        checked = overwrite,
                        onCheckedChange = { feedback(); overwrite = it },
                        colors = CheckboxDefaults.colors(checkedColor = AmberGlow)
                    )
                    Text(
                        "Overwrite existing entry for this date",
                        color = TextMuted,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                    )
                }
                Text(
                    if (overwrite) "This will replace whatever's already logged for this date."
                    else "This will be added on top of whatever's already logged for this date.",
                    color = TextMuted,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = { feedback(); onDelete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.height(0.dp))
                }
                TextButton(
                    enabled = amountValue != null,
                    onClick = {
                        feedback()
                        onSave(AstriteEntry.forDate(selectedDate, amountValue ?: 0, sourceText), overwrite)
                    }
                ) { Text("Save") }
            }
        },
        dismissButton = {
            TextButton(onClick = { feedback(); onDismiss() }) { Text("Cancel") }
        }
    )
}