package cn.edu.meowmap.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cn.edu.meowmap.data.*

@Composable
fun DiscoveryScreen(state: MeowState, vm: MeowViewModel, onRecord: (String) -> Unit, onLogin: () -> Unit, onDates: () -> Unit, onAssistant: () -> Unit) {
    val context = LocalContext.current
    val areas = remember { CampusAreas.load(context) }
    var campus by rememberSaveable { mutableStateOf<String?>(null) }
    var campusSummary by remember { mutableStateOf<CampusArea?>(null) }
    var radius by rememberSaveable { mutableIntStateOf(2) }
    var radiusMenu by remember { mutableStateOf(false) }
    var campusMenu by remember { mutableStateOf(false) }
    var list by rememberSaveable { mutableStateOf(false) }
    var heat by rememberSaveable { mutableStateOf(false) }
    var nearby by remember { mutableStateOf<MapPoint?>(null) }
    var cancelLocation by remember { mutableStateOf<(() -> Unit)?>(null) }
    var locating by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { cancelLocation?.invoke() } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) {
            cancelLocation?.invoke(); locating = true
            cancelLocation = locate(context) { point, error -> nearby = point; locating = false; error?.let(vm::notify) }
        } else vm.notify("需要定位授权才能查看附近；也可以按校园筛选")
    }
    val records = remember(state.records, campus, nearby, radius) {
        val selected = areas.find { it.label == campus }
        val filtered = state.records.filter { row ->
            val r = row.record
            (selected == null || selected.contains(r.latitude, r.longitude)) && (nearby == null || Coordinates.meters(r.latitude, r.longitude, nearby!!.latitude, nearby!!.longitude) <= radius * 1000.0)
        }
        if (nearby == null) filtered else filtered.sortedBy { Coordinates.meters(it.record.latitude, it.record.longitude, nearby!!.latitude, nearby!!.longitude) }
    }
    val focusedArea = areas.find { it.label == campus }
    val focus = nearby ?: focusedArea?.rings?.firstOrNull()?.firstOrNull()?.let { MapPoint(it.first, it.second) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("喵喵地图", style = MaterialTheme.typography.titleLarge)
                Row {
                    if (!cn.edu.meowmap.BuildConfig.MEOW_LOCAL_MODE) TextButton(onClick = onAssistant) { Text("寻猫助手") }
                    TextButton(onClick = vm::refresh, enabled = !state.busy) { Text("刷新") }
                }
            }
            state.notice?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            OutlinedTextField(state.filters.query, vm::query, placeholder = { Text("搜索标题、描述或地点") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(state.filters.mine, { if (state.user == null) onLogin() else vm.mine(!state.filters.mine) }, label = { Text("只看我的") })
                FilterChip(state.filters.start != null || state.filters.end != null, onDates, label = { Text("日期") })
                Box {
                    FilterChip(campus != null, { campusMenu = true }, label = { Text(campus ?: "全部校园") })
                    DropdownMenu(campusMenu, { campusMenu = false }) {
                        DropdownMenuItem(text = { Text("全部校园") }, onClick = { campus = null; campusMenu = false })
                        areas.forEach { area -> DropdownMenuItem(text = { Text(area.label) }, onClick = { campus = area.label; campusMenu = false }) }
                    }
                }
                Box {
                    FilterChip(nearby != null, { radiusMenu = true }, enabled = !locating,
                        label = { Text(if (locating) "定位中…" else if (nearby == null) "附近" else "附近 $radius km") })
                    DropdownMenu(radiusMenu, { radiusMenu = false }) {
                        listOf(1, 2, 5, 10).forEach { distance ->
                            DropdownMenuItem(text = { Text("$distance km") }, onClick = {
                                radius = distance
                                radiusMenu = false
                                if (nearby == null) permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            })
                        }
                        if (nearby != null) DropdownMenuItem(text = { Text("关闭附近筛选") }, onClick = { nearby = null; radiusMenu = false })
                    }
                }
                FilterChip(list, { list = !list }, modifier = Modifier.testTag("toggle_map_list"), label = { Text("列表") })
                if (!list) FilterChip(heat, { heat = !heat }, label = { Text("热力图") })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${records.size} 条观察" + if (nearby != null) " · 距离从近到远" else "", color = Muted, style = MaterialTheme.typography.labelSmall)
                if (state.filters.active || campus != null || nearby != null) TextButton(onClick = { vm.clearFilters(); campus = null; nearby = null }) { Text("清除筛选") }
            }
        }
        if (list) LazyColumn(Modifier.weight(1f).testTag("record_list"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (records.isEmpty()) item { EmptyState("没有找到记录", "试试其他关键词、校园或日期") }
            items(records, key = { it.record.id }) { RecordCard(it) { onRecord(it.record.id) } }
        } else CampusMap(records, Modifier.weight(1f).fillMaxWidth(), focus = focus, onRecord = onRecord, areas = areas, heatmap = heat, onCampus = { campusSummary = it })
    }
    campusSummary?.let { area ->
        val count = state.allRecords.count { area.contains(it.record.latitude, it.record.longitude) }
        AlertDialog(onDismissRequest = { campusSummary = null }, title = { Text(area.label) },
            text = { Text("已加载 $count 条地图打卡") },
            confirmButton = { TextButton(onClick = { campus = area.label; campusSummary = null }) { Text("筛选此校园") } },
            dismissButton = { TextButton(onClick = { campusSummary = null }) { Text("关闭") } })
    }
}
