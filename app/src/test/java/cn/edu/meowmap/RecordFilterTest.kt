package cn.edu.meowmap

import cn.edu.meowmap.data.*
import cn.edu.meowmap.ui.Filters
import cn.edu.meowmap.ui.filtered
import cn.edu.meowmap.ui.withDates
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class RecordFilterTest {
    private fun entry(id: String, owner: String, time: Long, place: String) = RecordSummary(
        CatRecord(id, owner, "猫咪 $id", "安静观察", place, 23.0, 113.0, time, time), "同学", null, 0, 0, 0, false,
    )
    private val entries = listOf(entry("one", "alice", 1000, "图书馆"), entry("two", "bob", 2000, "湖边"), entry("three", "alice", 3000, "湖边"))

    @Test fun combinedFilterKeepsOriginalRecordIdentity() {
        val result = entries.filtered(Filters(query = "湖边", mine = true, start = 2000, end = 4000), "alice")
        assertEquals(listOf("three"), result.map { it.record.id })
    }
    @Test fun dateRangeIncludesStartAndExcludesNextDayBoundary() {
        assertEquals(listOf("two"), entries.filtered(Filters(start = 2000, end = 3000), null).map { it.record.id })
    }
    @Test fun guestMineFilterNeverShowsOtherUsersRecords() {
        assertTrue(entries.filtered(Filters(mine = true), null).isEmpty())
    }
    @Test fun queryTrimsAndFindsDescription() {
        assertEquals(3, entries.filtered(Filters(query = " 安静 "), null).size)
    }
    @Test fun localDateRangeIncludesWholeFinalDay() {
        val zone = ZoneId.of("Asia/Taipei")
        val day = LocalDate.of(2026, 10, 20)
        val filters = Filters().withDates(day, day, zone)
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(start, filters.start)
        assertEquals(start + 86_400_000, filters.end)
        assertEquals("2026-10-20 至 2026-10-20", filters.dateLabel(zone))
    }
    @Test fun daylightSavingRangeUsesCalendarDaysRatherThanFixed24Hours() {
        val zone = ZoneId.of("America/New_York")
        val day = LocalDate.of(2026, 3, 8)
        val filters = Filters().withDates(day, day, zone)
        assertEquals(23 * 3_600_000L, filters.end!! - filters.start!!)
    }
    @Test fun rejectsReversedDateRange() {
        assertThrows(IllegalArgumentException::class.java) { Filters().withDates(LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 20)) }
    }
    @Test fun dateLabelsSupportOpenRangesAndReset() {
        val zone = ZoneId.of("Asia/Taipei")
        val day = LocalDate.of(2026, 10, 20)
        assertEquals("2026-10-20 起", Filters().withDates(day, null, zone).dateLabel(zone))
        assertEquals("截至 2026-10-20", Filters().withDates(null, day, zone).dateLabel(zone))
        assertEquals("全部日期", Filters().dateLabel(zone))
        assertFalse(Filters().active)
        assertTrue(Filters(mine = true).active)
        assertFalse(Filters(query = "  ").active)
    }
}
