package cn.edu.meowmap.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PublicObservations(state: MeowState, vm: MeowViewModel, onRecord: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val rows = remember(state.publicObservations, query) {
        state.publicObservations.filter { row ->
            listOf(row.label, row.place, row.species, row.author).any { it.contains(query.trim(), ignoreCase = true) }
        }
    }
    val availableMaps = remember(state.allRecords) { state.allRecords.map { it.record.id }.toSet() }
    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("搜索公开观察") }, singleLine = true)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${rows.size} 条公开观察", Modifier.padding(top = 14.dp), style = MaterialTheme.typography.labelLarge)
        TextButton(onClick = vm::refresh, enabled = !state.busy) { Text("刷新") }
    }
    state.notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (rows.isEmpty()) EmptyState("暂无匹配观察", "试试其他关键词或刷新记录")
    LazyColumn(contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(rows, key = { it.key }) { row ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.label, style = MaterialTheme.typography.titleSmall)
                Text(row.place.ifBlank { "缺少具体地点" }, color = Sage)
                Text(listOf(if (row.kind == "map_records") "地图打卡" else "普通观察", row.author,
                    if (row.time > 0) displayTime(row.time) else "").filter(String::isNotBlank).joinToString(" · "),
                    color = Muted, style = MaterialTheme.typography.bodySmall)
                if (row.demo) Text("校园示例", color = Muted, style = MaterialTheme.typography.labelSmall)
                val mapId = row.mapId
                if (mapId != null && mapId in availableMaps) {
                    TextButton(onClick = { onRecord(mapId) }) { Text("查看地图打卡") }
                }
            }
            HorizontalDivider()
        }
    }
}
