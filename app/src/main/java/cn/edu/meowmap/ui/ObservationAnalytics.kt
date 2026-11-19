package cn.edu.meowmap.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cn.edu.meowmap.BuildConfig
import cn.edu.meowmap.data.CampusArea
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private data class ActivityRow(
    val time: Long,
    val source: String,
    val campus: String,
    val species: String,
    val place: String,
    val count: Int,
    val demo: Boolean,
    val catKey: Pair<String, String>? = null,
)

@Composable
fun ObservationAnalytics(state: MeowState, campuses: List<CampusArea>) {
    var public by rememberSaveable { mutableStateOf(state.user == null && !BuildConfig.MEOW_LOCAL_MODE) }
    var includeDemo by rememberSaveable { mutableStateOf(true) }
    val owner = state.user?.id
    val rows = remember(public, includeDemo, owner, state.publicObservations, state.allRecords, state.cats, state.observations, campuses) {
        val maps = state.publicObservations.mapNotNull { row -> row.mapId?.let { it to row } }.toMap()
        val ordinary = state.publicObservations.filter { it.kind == "records" }.associateBy { it.key }
        val cats = state.cats.associateBy { it.id }
        val mapped = state.allRecords.filter { public || it.record.userId == owner }.map { summary ->
            val record = summary.record
            val metadata = maps[record.id]
            ActivityRow(
                time = metadata?.time?.takeIf { it > 0 } ?: record.createdAt,
                source = "地图打卡",
                campus = metadata?.university?.takeIf { it.isNotBlank() }
                    ?: campuses.find { it.contains(record.latitude, record.longitude) }?.university.orEmpty(),
                species = metadata?.species?.takeIf { it.isNotBlank() }
                    ?: cats[record.catId]?.species.orEmpty().takeIf { record.userId == owner }.orEmpty(),
                place = record.title.ifBlank { record.place },
                count = metadata?.count ?: 1,
                demo = metadata?.demo == true || record.demo,
                catKey = record.catId?.let { record.userId to it },
            )
        }
        val notes = if (public) emptyList() else state.observations.map { note ->
            val metadata = ordinary["records:${note.userId}~${note.id}"]
            ActivityRow(
                time = note.observedAt,
                source = "观察记录",
                campus = metadata?.university.orEmpty(),
                species = metadata?.species?.takeIf { it.isNotBlank() } ?: cats[note.catId]?.species.orEmpty(),
                place = note.place,
                count = note.count,
                demo = metadata?.demo == true,
            )
        }
        (mapped + notes).filter { includeDemo || !it.demo }
    }
    SectionTitle("统计面板")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!public, { public = false }, label = { Text("我的记录") })
        FilterChip(public, { public = true }, label = { Text("公开地图") })
        FilterChip(includeDemo, { includeDemo = !includeDemo }, label = { Text("包含示例记录") })
    }
    if (rows.isEmpty()) {
        EmptyState("暂无观察数据", if (owner == null && !public) "登录查看我的记录，或切换到公开地图" else "有观察记录后即可查看分布")
        return
    }

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val currentMonth = YearMonth.from(today)
    val dated = rows.filter { it.time > 0 }.map { it to Instant.ofEpochMilli(it.time).atZone(zone) }
    val times = dated.map { it.second }
    val monthEncounters = dated.sumOf { (row, time) -> if (YearMonth.from(time) == currentMonth) row.count.toLong() else 0L }
    val samples = rows.count { it.demo }
    Text("${rows.size} 条观察记录" + if (includeDemo) " · 含 $samples 条示例" else "",
        style = MaterialTheme.typography.bodySmall, color = Muted)

    ChartPanel("观察概览") {
        val catCount = if (public) rows.mapNotNull { it.catKey }.distinct().size else state.cats.size
        val catLabel = if (public) "关联猫咪" else "猫咪档案"
        val metrics = listOf(
            "本月遇见只次" to monthEncounters.toString(),
            "累计记录条数" to rows.size.toString(),
            "地图打卡" to rows.count { it.source == "地图打卡" }.toString(),
            catLabel to catCount.toString(),
            "累计遇见只次" to rows.sumOf { it.count.toLong() }.toString(),
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 620.dp) 5 else if (maxWidth >= 390.dp) 3 else 2
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                metrics.chunked(columns).forEach { group ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.forEach { (label, value) ->
                            Column(Modifier.weight(1f)) {
                                Text(value, style = MaterialTheme.typography.headlineSmall, color = Sage)
                                Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
                            }
                        }
                        repeat(columns - group.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }

    DailyTrend(times, today)
    Breakdown("记录来源占比", rows.map { it.source })
    Breakdown("品种／花色构成", rows.map { it.species.ifBlank { "未识别品种" } })
    ActivityHeatmap(times)
    MonthlyTrend(times, currentMonth)
    Breakdown("最常出没地点", rows.mapNotNull { it.place.takeIf(String::isNotBlank) })
    Breakdown("校园覆盖分布", rows.map { it.campus.ifBlank { "未标注校园" } })
}

@Composable
private fun DailyTrend(times: List<ZonedDateTime>, today: LocalDate) {
    val days = (27 downTo 0).map { today.minusDays(it.toLong()) }
    val daily = times.groupingBy { it.toLocalDate() }.eachCount()
    val totals = days.map { daily[it] ?: 0 }
    val maximum = maxOf(1, totals.max())
    val ink = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    ChartPanel("近 28 天观察趋势", "按观察日期统计记录条数") {
        val description = days.zip(totals).joinToString("，") { (day, count) -> "$day $count 条" }
        Canvas(Modifier.fillMaxWidth().height(104.dp).semantics { contentDescription = description }) {
            val padding = 3.dp.toPx()
            val width = size.width - padding * 2
            val height = size.height - padding * 2
            for (index in 0..2) {
                val y = padding + height * index / 2
                drawLine(outline, Offset(padding, y), Offset(size.width - padding, y), strokeWidth = 1.dp.toPx())
            }
            val points = totals.mapIndexed { index, count ->
                Offset(padding + width * index / totals.lastIndex, padding + height * (1f - count.toFloat() / maximum))
            }
            points.zipWithNext().forEach { (from, to) -> drawLine(ink, from, to, strokeWidth = 2.dp.toPx()) }
            points.forEach { drawCircle(ink, radius = 2.dp.toPx(), center = it) }
        }
        val dateFormat = DateTimeFormatter.ofPattern("MM-dd")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0, 9, 18, 27).forEach { index ->
                Text(days[index].format(dateFormat), style = MaterialTheme.typography.labelSmall, color = Muted)
            }
        }
    }
}

@Composable
private fun MonthlyTrend(times: List<ZonedDateTime>, currentMonth: YearMonth) {
    val months = (5 downTo 0).map { currentMonth.minusMonths(it.toLong()) }
    val monthly = times.groupingBy { YearMonth.from(it) }.eachCount()
    val peak = maxOf(1, months.maxOf { monthly[it] ?: 0 })
    val ink = MaterialTheme.colorScheme.primary
    ChartPanel("最近 6 个月记录趋势") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            months.forEach { month ->
                val count = monthly[month] ?: 0
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$count", style = MaterialTheme.typography.labelSmall)
                    Canvas(Modifier.fillMaxWidth().height(72.dp).semantics { contentDescription = "$month $count 条" }) {
                        val height = size.height * count / peak
                        if (count > 0) drawRect(ink, Offset(0f, size.height - height), Size(size.width, height))
                    }
                    Text("${month.monthValue} 月", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ActivityHeatmap(times: List<ZonedDateTime>) {
    val ink = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val matrix = List(7) { IntArray(24) }
    times.forEach { matrix[it.dayOfWeek.value - 1][it.hour]++ }
    val peak = maxOf(1, matrix.maxOf { it.max() })
    ChartPanel("一周中的观察时段", "按设备时区统计，颜色越深，记录越多") {
        matrix.forEachIndexed { day, hours ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("周${"一二三四五六日"[day]}", Modifier.width(32.dp), style = MaterialTheme.typography.labelSmall)
                val description = hours.mapIndexed { hour, count -> "$hour 时 $count 条" }.joinToString("，")
                Canvas(Modifier.weight(1f).height(16.dp).semantics { contentDescription = description }) {
                    val step = size.width / 24
                    hours.forEachIndexed { hour, count ->
                        drawRect(lerp(track, ink, count.toFloat() / peak), Offset(hour * step, 0f), Size(step * 0.88f, size.height))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 40.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("00", "06", "12", "18", "23").forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun Breakdown(title: String, labels: List<String>) {
    val counts = labels.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5)
    ChartPanel(title) {
        if (counts.isEmpty()) Text("暂无可统计的记录", style = MaterialTheme.typography.bodySmall, color = Muted)
        counts.forEach { (label, count) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { count.toFloat() / labels.size }, modifier = Modifier.weight(1.5f))
                Text("$count", Modifier.widthIn(min = 24.dp), style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
    }
}

@Composable
private fun ChartPanel(title: String, caption: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
            content()
        }
    }
}
