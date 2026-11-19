package cn.edu.meowmap.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import cn.edu.meowmap.data.RecordSummary
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
    var source by rememberSaveable { mutableStateOf("全部") }
    var missing by rememberSaveable { mutableStateOf(false) }
    var field by rememberSaveable { mutableStateOf("全部字段") }
    val rows = remember(state.publicObservations, query, source, missing, field) {
        state.publicObservations.filter { row ->
            val fields = when (field) {
                "猫咪" -> listOf(row.catName, row.species, row.label)
                "地点" -> listOf(row.place, row.university)
                "描述" -> listOf(row.description)
                "作者" -> listOf(row.author)
                else -> listOf(row.label, row.place, row.catName, row.species, row.author, row.description, row.university)
            }
            (source == "全部" || (source == "地图打卡") == (row.kind == "map_records")) &&
                (!missing || !row.hasSpecificLocation) && fields.matchesObservationQuery(query)
        }
    }
    val availableMaps = remember(state.allRecords) { state.allRecords.map { it.record.id }.toSet() }
    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("搜索公开观察") },
        leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ObservationChoices(field, listOf("全部字段", "猫咪", "地点", "描述", "作者")) { field = it }
        ObservationChoices(source, listOf("全部", "观察记录", "地图打卡")) { source = it }
        FilterChip(missing, { missing = !missing }, label = { Text("缺少地点") })
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${rows.size} 条公开观察", Modifier.padding(top = 14.dp), style = MaterialTheme.typography.labelLarge)
        TextButton(onClick = vm::refresh, enabled = !state.busy) { Text("刷新") }
    }
    state.notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (rows.isEmpty()) {
        EmptyState("暂无匹配观察", "试试其他关键词或刷新记录")
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(rows, key = { it.key }) { row ->
            Surface {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(row.label, style = MaterialTheme.typography.titleSmall)
                    Text(row.place.ifBlank { "缺少具体地点" }, color = Sage)
                    val cats = listOf(row.catName, row.species).filter { it.isNotBlank() && it != row.label }.distinct()
                    if (cats.isNotEmpty()) Text(cats.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    if (row.description.isNotBlank()) Text(row.description, color = Muted,
                        style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val sourceLabel = if (row.kind == "map_records") "地图打卡" else "观察记录"
                    val details = listOf(sourceLabel, "${row.count} 只", row.author,
                        if (row.time > 0) displayTime(row.time) else "", if (row.demo) "示例" else "")
                    Text(details.filter(String::isNotBlank).joinToString(" · "), color = Muted, style = MaterialTheme.typography.labelSmall)
                    val mapId = row.mapId
                    if (mapId != null && mapId in availableMaps) {
                        TextButton(onClick = { onRecord(mapId) }) { Text("在地图定位") }
                    }
                }
            }
            HorizontalDivider()
        }
    }
}


private val ObservationWhitespace = Regex("\\s+")

internal fun List<String>.matchesObservationQuery(query: String): Boolean =
    query.trim().split(ObservationWhitespace).all { term -> any { it.contains(term, ignoreCase = true) } }

@Composable
internal fun ObservationChoices(value: String, options: List<String>, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(value != options.first(), { open = true }, label = { Text(value) })
        DropdownMenu(open, { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onChange(option); open = false })
            }
        }
    }
}

@Composable
internal fun MapObservationRow(row: RecordSummary, avatar: String? = null, observedAt: Long? = null, onClick: () -> Unit) {
    val record = row.record
    Surface {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val photo = Modifier.size(56.dp).clip(MaterialTheme.shapes.small)
            if (row.cover == null) {
                val preset = avatarPreset(record.catId ?: record.id.substringAfter('~'), avatar)
                Image(painterResource(preset.resource), null, photo)
            } else CatCover(row.cover, "", photo)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(record.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (record.description.isNotBlank()) Text(record.description, color = Muted,
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(record.place.ifBlank { "缺少具体地点" }, color = Sage, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                val time = observedAt?.takeIf { it > 0 } ?: record.createdAt
                val details = listOf(row.author, if (time > 0) displayTime(time) else "", if (record.demo) "示例" else "")
                Text(details.filter(String::isNotBlank).joinToString(" · "), color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Sage)
        }
    }
    HorizontalDivider()
}
