package cn.edu.meowmap.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import cn.edu.meowmap.R
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
fun DiscoveryScreen(state: MeowState, vm: MeowViewModel, onRecord: (String) -> Unit, onLogin: () -> Unit, onDates: () -> Unit, onNew: () -> Unit, targetId: String?, targetRequest: Long) {
    val context = LocalContext.current
    val areas = remember { CampusAreas.load(context) }
    var campus by rememberSaveable { mutableStateOf<String?>(null) }
    var campusSummary by remember { mutableStateOf<CampusArea?>(null) }
    var radius by rememberSaveable { mutableIntStateOf(2) }
    var radiusMenu by remember { mutableStateOf(false) }
    var campusMenu by remember { mutableStateOf(false) }
    var list by rememberSaveable { mutableStateOf(false) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    var boundaries by rememberSaveable { mutableStateOf(true) }
    var cluster by rememberSaveable { mutableStateOf(false) }
    var trackOpen by rememberSaveable { mutableStateOf(false) }
    var trackMenu by remember { mutableStateOf(false) }
    var trackKey by rememberSaveable { mutableStateOf<String?>(null) }
    var consumedRequest by rememberSaveable { mutableLongStateOf(0L) }
    var highlightId by rememberSaveable { mutableStateOf<String?>(null) }
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
    val observationsByMapId = remember(state.publicObservations) {
        state.publicObservations.mapNotNull { row -> row.mapId?.let { it to row } }.toMap()
    }
    val catsById = remember(state.cats) { state.cats.associateBy { it.id } }
    val recordAvatars = remember(state.allRecords, catsById, observationsByMapId, state.user?.id) {
        state.allRecords.associate { row ->
            val record = row.record
            val ownAvatar = catsById[record.catId]?.avatar.takeIf { record.userId == state.user?.id }
            record.id to (ownAvatar ?: observationsByMapId[record.id]?.catAvatar)
        }
    }
    val records = remember(state.allRecords, state.filters, state.user?.id, campus, nearby, radius, observationsByMapId, catsById) {
        val selected = areas.find { it.label == campus }
        val location = nearby
        val base = state.allRecords.filtered(state.filters.copy(query = ""), state.user?.id)
        val matching = base.filter { row ->
            val record = row.record
            val cat = catsById[record.catId]?.takeIf { record.userId == state.user?.id }
            val metadata = observationsByMapId[record.id]
            (selected == null || selected.contains(record.latitude, record.longitude)) &&
                listOf(record.title, record.description, record.place, row.author, cat?.name.orEmpty(),
                    metadata?.catName.orEmpty(), metadata?.place.orEmpty()).matchesObservationQuery(state.filters.query)
        }
        if (location == null) matching else matching.map { row ->
            val record = row.record
            row to Coordinates.meters(record.latitude, record.longitude, location.latitude, location.longitude)
        }.filter { (_, distance) -> distance <= radius * 1000.0 }.sortedBy { it.second }.map { it.first }
    }
    val tracks = remember(state.allRecords, catsById, observationsByMapId, state.user?.id) {
        catTracks(state.allRecords, catsById, observationsByMapId, state.user?.id)
    }
    val track = tracks.find { it.key == trackKey }
    LaunchedEffect(targetRequest, targetId) {
        if (targetId != null && targetRequest != consumedRequest) {
            consumedRequest = targetRequest
            highlightId = targetId
            trackOpen = false
            trackKey = null
            heat = false
            list = false
        } else if (targetId == null) highlightId = null
    }
    val highlighted = state.allRecords.firstOrNull { it.record.id == highlightId }
    val point = highlighted?.record?.let { MapPoint(it.latitude, it.longitude, targetRequest) }
    val focusedArea = areas.find { it.label == campus }
    val focus = point ?: nearby ?: focusedArea?.rings?.firstOrNull()?.firstOrNull()?.let { MapPoint(it.first, it.second) }
    val mapRecords = track?.rows ?: if (highlighted != null && records.none { it.record.id == highlightId }) records + highlighted else records
    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(painterResource(R.drawable.ic_launcher), "喵喵地图", Modifier.size(32.dp))
                Text("发现地图", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                FilledTonalButton(onClick = onNew, modifier = Modifier.testTag("new_record")) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新增观察")
                }
            }
        }
        HorizontalDivider()
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(list, {
                    list = !list
                    trackOpen = false
                    trackKey = null
                    highlightId = null
                }, modifier = Modifier.testTag("toggle_map_list"), label = { Text(if (list) "返回地图" else "观察列表") })
                if (list) Text("观察列表", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.titleSmall)
                if (!list) {
                    FilterChip(heat, {
                        heat = !heat
                        trackOpen = false
                        trackKey = null
                        highlightId = null
                    }, label = { Text("热力图") })
                    FilterChip(boundaries, { boundaries = !boundaries }, label = { Text("校园范围") })
                    FilterChip(trackOpen, {
                        trackOpen = !trackOpen
                        if (!trackOpen) trackKey = null
                        heat = false
                        highlightId = null
                    }, label = { Text("喵喵轨迹") })
                }
            }
            state.notice?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (trackOpen) {
                Box {
                    OutlinedButton(onClick = { trackMenu = true }, enabled = tracks.isNotEmpty()) {
                        Text(track?.name ?: "选择猫咪档案", maxLines = 1)
                    }
                    DropdownMenu(trackMenu, { trackMenu = false }) {
                        tracks.forEach { item ->
                            DropdownMenuItem(text = { Text("${item.name} · ${item.rows.size} 条") },
                                onClick = { trackKey = item.key; trackMenu = false })
                        }
                    }
                }
                Text(if (track == null) {
                    if (tracks.isEmpty()) "暂无关联猫咪档案的地图打卡" else "按观察时间连接地图打卡点"
                } else {
                    val start = displayTime(track.firstTime)
                    val end = displayTime(track.lastTime)
                    val examples = track.rows.count { it.record.demo || observationsByMapId[it.record.id]?.demo == true }
                    val range = if (start == end) start else "$start – $end"
                    "${track.rows.size} 个观察点 · $range" + if (examples > 0) " · $examples 条示例" else ""
                }, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            if (!trackOpen && (list || filtersOpen)) {
                OutlinedTextField(state.filters.query, {
                    highlightId = null
                    vm.query(it)
                }, placeholder = { Text("搜索猫咪、地点或描述") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            if (highlighted != null && !list && !trackOpen && !heat) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(highlighted.record.title, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { onRecord(highlighted.record.id) }) { Text("查看详情") }
                    IconButton(onClick = { highlightId = null }) { Icon(Icons.Outlined.Close, "取消地图高亮") }
                }
            }
            if (!trackOpen) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${records.size} 条观察" + if (nearby != null) " · 距离从近到远" else "",
                    Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { filtersOpen = !filtersOpen }) { Text(if (filtersOpen) "收起筛选" else if (list) "筛选" else "搜索与筛选") }
                IconButton(onClick = vm::refresh, enabled = !state.busy) { Icon(Icons.Outlined.Refresh, "刷新") }
            }
            if (filtersOpen && !trackOpen) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.filters.mine, {
                        if (state.user == null) onLogin() else {
                            highlightId = null
                            vm.mine(!state.filters.mine)
                        }
                    }, label = { Text("只看我的") })
                    FilterChip(state.filters.start != null || state.filters.end != null, { highlightId = null; onDates() }, label = { Text("日期") })
                    Box {
                        FilterChip(campus != null, { campusMenu = true }, label = { Text(campus ?: "全部校园") })
                        DropdownMenu(campusMenu, { campusMenu = false }) {
                            DropdownMenuItem(text = { Text("全部校园") }, onClick = { highlightId = null; campus = null; campusMenu = false })
                            areas.forEach { area -> DropdownMenuItem(text = { Text(area.label) }, onClick = { highlightId = null; campus = area.label; campusMenu = false }) }
                        }
                    }
                    Box {
                        FilterChip(nearby != null, { radiusMenu = true }, enabled = !locating,
                            label = { Text(if (locating) "定位中…" else if (nearby == null) "附近" else "附近 $radius km") })
                        DropdownMenu(radiusMenu, { radiusMenu = false }) {
                            listOf(1, 2, 5, 10).forEach { distance ->
                                DropdownMenuItem(text = { Text("$distance km") }, onClick = {
                                    highlightId = null
                                    radius = distance
                                    radiusMenu = false
                                    if (nearby == null) permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                })
                            }
                            if (nearby != null) DropdownMenuItem(text = { Text("关闭附近筛选") }, onClick = { nearby = null; radiusMenu = false })
                        }
                    }
                    FilterChip(cluster, { cluster = !cluster }, label = { Text("按位置聚合") })
                    if (state.filters.active || campus != null || nearby != null || cluster) {
                        TextButton(onClick = { vm.clearFilters(); highlightId = null; campus = null; nearby = null; cluster = false }) { Text("重置筛选") }
                    }
                }
            }
        }
        if (list) LazyColumn(Modifier.weight(1f).testTag("record_list"), state = rememberLazyListState(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
            if (records.isEmpty()) item { EmptyState("没有找到记录", "试试其他关键词、校园或日期") }
            items(records, key = { it.record.id }) { row ->
                MapObservationRow(row, recordAvatars[row.record.id], observationsByMapId[row.record.id]?.time) { onRecord(row.record.id) }
            }
        } else CampusMap(mapRecords, Modifier.weight(1f).fillMaxWidth(), focus = focus, selection = point,
            onRecord = onRecord, areas = areas, showAreas = boundaries, clustered = cluster && track == null && point == null,
            heatmap = heat, track = track?.rows.orEmpty(), recordAvatars = recordAvatars, onCampus = { campusSummary = it })
    }
    campusSummary?.let { area ->
        val sightings = state.allRecords.filter { area.contains(it.record.latitude, it.record.longitude) }
        val catCount = sightings.mapNotNull { row -> row.record.catId?.let { row.record.userId to it } }.distinct().size
        val latest = sightings.maxByOrNull { observationsByMapId[it.record.id]?.time ?: it.record.createdAt }
        AlertDialog(onDismissRequest = { campusSummary = null }, title = { Text(area.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${sightings.size} 条地图打卡 · $catCount 份关联档案")
                    latest?.let { Text("最近发现：${it.record.title}") }
                }
            },
            confirmButton = { TextButton(onClick = { highlightId = null; campus = area.label; campusSummary = null }) { Text("筛选此校园") } },
            dismissButton = { TextButton(onClick = { campusSummary = null }) { Text("关闭") } })
    }
}

internal data class CatTrack(
    val key: String,
    val name: String,
    val rows: List<RecordSummary>,
    val firstTime: Long,
    val lastTime: Long,
)

internal fun catTracks(
    records: List<RecordSummary>,
    cats: Map<String, CatProfile>,
    observations: Map<String, PublicObservation>,
    viewer: String?,
): List<CatTrack> {
    val times = records.associate { row ->
        val record = row.record
        record.id to (observations[record.id]?.time?.takeIf { it > 0 } ?: record.createdAt)
    }
    return records.filter {
        val record = it.record
        !record.catId.isNullOrBlank() && times.getValue(record.id) > 0 &&
            record.latitude in -85.0..85.0 && record.longitude in -180.0..180.0
    }.groupBy { "${it.record.userId}~${it.record.catId}" }.map { (key, rows) ->
        val first = rows.first()
        val record = first.record
        val catId = requireNotNull(record.catId)
        val ownName = cats[catId]?.name?.takeIf { record.userId == viewer && it.isNotBlank() }
        val name = ownName ?: rows.firstNotNullOfOrNull { observations[it.record.id]?.catName?.takeIf(String::isNotBlank) }
            ?: "猫咪 ${catId.takeLast(8)}"
        val ordered = rows.sortedBy { times.getValue(it.record.id) }
        CatTrack(key, "$name · ${first.author}", ordered, times.getValue(ordered.first().record.id),
            times.getValue(ordered.last().record.id))
    }.sortedBy { it.name }
}
