package cn.edu.meowmap

import cn.edu.meowmap.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ObservationTest {
    private val now = Instant.parse("2026-10-29T12:00:00.000Z").toEpochMilli()
    private fun input() = ObservationInput(observedAt = now - 1000, place = " 图书馆 ", count = 2, catName = " 小橘 ", appearance = "白爪子")
    @Test fun invalidCountsAndDatesCannotBeSaved() {
        listOf(0, -1, 1000).forEach { count -> assertThrows(IllegalArgumentException::class.java) { input().copy(count = count).validated(now) } }
        listOf(-1L, now + 1).forEach { time -> assertThrows(IllegalArgumentException::class.java) { input().copy(observedAt = time).validated(now) } }
        assertEquals("图书馆", input().validated(now).place)
        assertThrows(IllegalArgumentException::class.java) { input().copy(catName = "  ").validated(now) }
    }
    @Test fun searchMatchesPlaceNameOrAppearance() {
        val rows = listOf(Observation("note-1", "u", 1, "Library", 2, "小橘", "白爪子", 1, 1))
        for (query in listOf("LIB", "小橘", " 白爪 ", "")) assertEquals(1, rows.searchObservations(query).size)
        assertTrue(rows.searchObservations("三花").isEmpty())
    }
}
