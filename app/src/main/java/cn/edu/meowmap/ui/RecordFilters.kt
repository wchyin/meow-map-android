package cn.edu.meowmap.ui

import cn.edu.meowmap.data.RecordSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class Filters(val query: String = "", val mine: Boolean = false, val start: Long? = null, val end: Long? = null) {
    val active: Boolean get() = query.isNotBlank() || mine || start != null || end != null
    fun dateLabel(zone: ZoneId = ZoneId.systemDefault()): String {
        val from = start?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString() }
        val until = end?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().minusDays(1).toString() }
        return when {
            from != null && until != null -> "$from 至 $until"
            from != null -> "$from 起"
            until != null -> "截至 $until"
            else -> "全部日期"
        }
    }
}

fun Filters.withDates(startDate: LocalDate?, endInclusive: LocalDate?, zone: ZoneId = ZoneId.systemDefault()): Filters {
    require(startDate == null || endInclusive == null || !startDate.isAfter(endInclusive)) { "开始日期不能晚于结束日期" }
    return copy(start = startDate?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(), end = endInclusive?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli())
}

fun List<RecordSummary>.filtered(filters: Filters, userId: String?): List<RecordSummary> {
    val query = filters.query.trim()
    return filter {
        val r = it.record
        (!filters.mine || r.userId == userId) &&
            (query.isBlank() || listOf(r.title, r.description, r.place, it.author).any { text -> text.contains(query, true) }) &&
            (filters.start == null || r.createdAt >= filters.start) && (filters.end == null || r.createdAt < filters.end)
    }
}
