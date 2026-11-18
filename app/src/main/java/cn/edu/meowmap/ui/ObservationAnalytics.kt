package cn.edu.meowmap.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cn.edu.meowmap.data.CampusArea
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class ActivityRow(val time: Long, val source: String, val campus: String, val species: String, val demo: Boolean)

@Composable
fun ObservationAnalytics(state: MeowState, campuses: List<CampusArea>) {
    var public by rememberSaveable { mutableStateOf(false) }
    var includeDemo by rememberSaveable { mutableStateOf(false) }
    val owner = state.user?.id
    val maps = state.publicObservations.filter { it.mapId != null }.associateBy { it.mapId }
    val ordinary = state.publicObservations.filter { it.kind == "records" }.associateBy { it.key }
    val cats = state.cats.associateBy { it.id }
    val mapped = state.allRecords.filter { public || it.record.userId == owner }.map { summary ->
        val record = summary.record
        val metadata = maps[record.id]
        ActivityRow(metadata?.time?.takeIf { it > 0 } ?: record.createdAt, "地图打卡",
            metadata?.university?.takeIf { it.isNotBlank() }
                ?: campuses.find { it.contains(record.latitude, record.longitude) }?.university.orEmpty(),
            metadata?.species?.takeIf { it.isNotBlank() }
                ?: (if (public) "" else cats[record.catId]?.species.orEmpty()),
            metadata?.demo ?: record.demo)
    }
    val notes = if (public) emptyList() else state.observations.map { note ->
        val metadata = ordinary["records:${note.userId}~${note.id}"]
        ActivityRow(note.observedAt, "普通观察", metadata?.university.orEmpty(), note.catName, metadata?.demo == true)
    }
    val rows = (mapped + notes).filter { includeDemo || !it.demo }
    SectionTitle("数据分析", "${rows.size} 条观察记录 · 按记录分布统计")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!public, { public = false }, label = { Text("我的记录") })
        FilterChip(public, { public = true }, label = { Text("公开地图") })
    }
    FilterChip(includeDemo, { includeDemo = !includeDemo }, label = { Text("包含校园示例") })
    if (rows.isEmpty()) {
        EmptyState("暂无观察数据", if (owner == null && !public) "登录查看我的记录，或切换到公开地图" else "有观察记录后即可查看分布")
        return
    }

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val days = (27 downTo 0).map { today.minusDays(it.toLong()) }
    val times = rows.mapNotNull { row -> row.time.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(zone) } }
    val daily = times.groupingBy { it.toLocalDate() }.eachCount()
    val totals = days.map { daily[it] ?: 0 }
    val maximum = maxOf(1, totals.maxOrNull() ?: 0)
    val ink = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    SectionTitle("近 28 天趋势", "最高一天 ${totals.maxOrNull() ?: 0} 条")
    val daysDescription = days.zip(totals).joinToString("，") { (day, count) -> "$day $count 条" }
    Canvas(Modifier.fillMaxWidth().height(90.dp).semantics { contentDescription = daysDescription }) {
        val step = size.width / totals.size
        totals.forEachIndexed { index, count ->
            val height = size.height * count / maximum
            drawRect(if (count == 0) track else ink, Offset(index*step, size.height - maxOf(1f, height)), Size(step*0.78f, maxOf(1f, height)))
        }
    }
    val dateFormat = DateTimeFormatter.ofPattern("MM-dd")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(days.first().format(dateFormat), style = MaterialTheme.typography.labelSmall)
        Text(today.format(dateFormat), style = MaterialTheme.typography.labelSmall)
    }

    SectionTitle("星期 × 小时", "按设备时区。颜色越深，记录越多")
    val matrix = List(7) { IntArray(24) }
    times.forEach { matrix[it.dayOfWeek.value - 1][it.hour]++ }
    val peak = maxOf(1, matrix.maxOf { it.maxOrNull() ?: 0 })
    matrix.forEachIndexed { day, hours ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("周${"一二三四五六日"[day]}", Modifier.width(32.dp), style = MaterialTheme.typography.labelSmall)
            val description = hours.mapIndexed { hour, count -> "$hour 时 $count 条" }.joinToString("，")
            Canvas(Modifier.weight(1f).height(16.dp).semantics { contentDescription = description }) {
                val step = size.width / 24
                hours.forEachIndexed { hour, count ->
                    drawRect(lerp(track, ink, count.toFloat()/peak), Offset(hour*step, 0f), Size(step*0.88f, size.height))
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 40.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("00", "06", "12", "18", "23").forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
    }

    Breakdown("来源构成", rows.map { it.source })
    Breakdown("品种构成", rows.map { it.species.ifBlank { "未识别品种" } })
    Breakdown("校园覆盖", rows.map { it.campus.ifBlank { "未标注校园" } })
}

@Composable
private fun Breakdown(title: String, labels: List<String>) {
    SectionTitle(title)
    val counts = labels.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5)
    counts.forEach { (label, count) ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$label · $count 条", style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { count.toFloat()/labels.size }, modifier = Modifier.fillMaxWidth())
        }
    }
}
