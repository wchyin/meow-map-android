package cn.edu.meowmap.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.edu.meowmap.data.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObservationScreen(state: MeowState, vm: MeowViewModel, onLogin: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var start by rememberSaveable { mutableStateOf<Long?>(null) }
    var end by rememberSaveable { mutableStateOf<Long?>(null) }
    var dates by rememberSaveable { mutableStateOf(false) }
    val filtered = state.observations.searchObservations(query).filter {
        (start == null || it.observedAt >= start!!) && (end == null || it.observedAt < end!!)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        Text("我的观察手记", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (state.user == null) {
            EmptyState("登录后记录观察手记", "手记仅对自己的账号可见")
            Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("登录 / 注册") }
        } else {
            Button(onClick = { editorId = "new" }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("new_observation")) {
                Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("添加观察手记")
            }
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().testTag("observation_search"), singleLine = true,
                placeholder = { Text("搜索猫咪、地点或特征") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "清除搜索") } })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = start != null || end != null, onClick = { dates = true }, label = { Text("发现日期") })
                if (start != null || end != null || query.isNotEmpty()) TextButton(onClick = { start = null; end = null; query = "" }) { Text("清除筛选") }
            }
            if (start != null || end != null) Text(Filters(start = start, end = end).dateLabel(), style = MaterialTheme.typography.labelSmall, color = Muted)
            Text("${filtered.size} 篇手记 · 累计观察 ${filtered.sumOf { it.count.toLong() }} 只次", color = Sage, style = MaterialTheme.typography.labelLarge)
            Text("累计数量包含重复遇见同一只猫的情况", color = Muted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) EmptyState("没有找到手记", "添加手记或清除筛选")
            else LazyColumn(contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("observation_list")) {
                items(filtered, key = { it.id }) { note ->
                    Card(Modifier.fillMaxWidth().clickable(enabled = !state.busy) { editorId = note.id }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row {
                                Text(note.catName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Text("${note.count} 只", color = Coral)
                            }
                            Text(note.place, color = Sage)
                            if (note.appearance.isNotEmpty()) Text(note.appearance, style = MaterialTheme.typography.bodySmall)
                            Text(displayTime(note.observedAt), color = Muted, style = MaterialTheme.typography.labelSmall)
                            Row {
                                TextButton(onClick = { editorId = note.id }, enabled = !state.busy) { Text("编辑") }
                                TextButton(onClick = { deleteId = note.id }, enabled = !state.busy) { Text("删除") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (editorId != null && state.user != null) key(editorId) {
        val existing = state.observations.find { it.id == editorId }
        if (editorId == "new" || existing != null) ObservationEditor(existing, state.busy,
            onDismiss = { editorId = null }, onSave = { vm.saveObservation(it) { editorId = null } })
        else AlertDialog(onDismissRequest = { editorId = null }, title = { Text("手记已不存在") },
            confirmButton = { TextButton(onClick = { editorId = null }) { Text("返回列表") } })
    }
    if (deleteId != null) AlertDialog(onDismissRequest = { if (!state.busy) deleteId = null },
        title = { Text("删除这篇观察手记？") }, text = { Text("删除后无法恢复。") },
        confirmButton = { TextButton(enabled = !state.busy, modifier = Modifier.testTag("confirm_delete_observation"), onClick = { deleteId?.let { vm.deleteObservation(it) { deleteId = null } } }) { Text("删除") } },
        dismissButton = { TextButton(enabled = !state.busy, onClick = { deleteId = null }) { Text("取消") } })
    if (dates) DateFilterDialog("按发现日期筛选", start, end, onDismiss = { dates = false }) { from, to ->
        val filter = Filters().withDates(from, to)
        start = filter.start
        end = filter.end
        dates = false
    }
}

@Composable
private fun ObservationEditor(existing: Observation?, busy: Boolean, onDismiss: () -> Unit, onSave: (ObservationInput) -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    var time by rememberSaveable { mutableLongStateOf(existing?.observedAt ?: System.currentTimeMillis()) }
    var place by rememberSaveable { mutableStateOf(existing?.place ?: "") }
    var count by rememberSaveable { mutableStateOf(existing?.count?.toString() ?: "1") }
    var name by rememberSaveable { mutableStateOf(existing?.catName ?: "") }
    var appearance by rememberSaveable { mutableStateOf(existing?.appearance ?: "") }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun leave() { if (!busy) { if (dirty) leaving = true else onDismiss() } }
    BackHandler { leave() }
    AlertDialog(onDismissRequest = ::leave, title = { Text(if (existing == null) "添加观察手记" else "编辑观察手记") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("发现时间", style = MaterialTheme.typography.labelMedium)
            val selected = Instant.ofEpochMilli(time).atZone(zone)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(enabled = !busy, onClick = {
                    DatePickerDialog(context, { _, y, m, d ->
                        time = selected.withYear(y).withMonth(m + 1).withDayOfMonth(d).toInstant().toEpochMilli(); dirty = true
                    }, selected.year, selected.monthValue - 1, selected.dayOfMonth).apply { datePicker.maxDate = System.currentTimeMillis(); datePicker.minDate = 0 }.show()
                }) { Text(selected.toLocalDate().toString()) }
                OutlinedButton(enabled = !busy, onClick = {
                    TimePickerDialog(context, { _, h, m ->
                        time = selected.withHour(h).withMinute(m).withSecond(0).withNano(0).toInstant().toEpochMilli(); dirty = true
                    }, selected.hour, selected.minute, true).show()
                }) { Text(selected.format(DateTimeFormatter.ofPattern("HH:mm"))) }
            }
            OutlinedTextField(place, { place = it; dirty = true }, label = { Text("地点") }, singleLine = true, enabled = !busy, modifier = Modifier.testTag("observation_place"))
            OutlinedTextField(count, { count = it; dirty = true }, label = { Text("数量（1～999）") }, singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.testTag("observation_count"))
            OutlinedTextField(name, { name = it; dirty = true }, label = { Text("猫咪名称 / 品种") }, singleLine = true, enabled = !busy, modifier = Modifier.testTag("observation_name"))
            OutlinedTextField(appearance, { appearance = it; dirty = true }, label = { Text("毛色 / 特征（可选）") }, minLines = 2, enabled = !busy, modifier = Modifier.testTag("observation_appearance"))
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { Button(enabled = !busy, modifier = Modifier.testTag("observation_save"), onClick = {
        runCatching {
            ObservationInput(existing?.id, time, place, count.toIntOrNull() ?: 0, name, appearance).validated()
        }.onSuccess { error = null; onSave(it) }.onFailure { error = it.message }
    }) { Text(if (busy) "保存中…" else "保存") } }, dismissButton = { TextButton(enabled = !busy, onClick = ::leave) { Text("取消") } })
    if (leaving) AlertDialog(onDismissRequest = { leaving = false }, title = { Text("放弃未保存的修改？") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("放弃修改") } }, dismissButton = { TextButton(onClick = { leaving = false }) { Text("继续编辑") } })
}
