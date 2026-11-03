package cn.edu.meowmap.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateFilterDialog(
    title: String,
    start: Long?,
    end: Long?,
    onDismiss: () -> Unit,
    onApply: (LocalDate?, LocalDate?) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    fun pickerDay(millis: Long, daysBefore: Long): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().minusDays(daysBefore)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun localDay(millis: Long?): LocalDate? =
        millis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }

    val range = rememberDateRangePickerState(
        initialSelectedStartDateMillis = start?.let { pickerDay(it, 0) },
        initialSelectedEndDateMillis = end?.let { pickerDay(it, 1) },
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 8.dp)
                .heightIn(max = 520.dp).fillMaxHeight(.8f),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column {
                Text(title, Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge)
                DateRangePicker(
                    state = range,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    title = null,
                    headline = null,
                    showModeToggle = false,
                )
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onApply(null, null) }) { Text("清除日期") }
                    TextButton(onClick = {
                        onApply(localDay(range.selectedStartDateMillis), localDay(range.selectedEndDateMillis))
                    }) { Text("应用") }
                }
            }
        }
    }
}
