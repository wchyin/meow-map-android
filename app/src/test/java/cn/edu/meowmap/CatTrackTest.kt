package cn.edu.meowmap

import cn.edu.meowmap.data.CatProfile
import cn.edu.meowmap.data.CatRecord
import cn.edu.meowmap.data.PublicObservation
import cn.edu.meowmap.data.RecordSummary
import cn.edu.meowmap.ui.catTracks
import org.junit.Assert.assertEquals
import org.junit.Test

class CatTrackTest {
    @Test fun groupsByOwnerAndCatAndSortsByObservationTime() {
        val postedFirst = sighting("alice", "first", 1_000)
        val observedFirst = sighting("alice", "second", 2_000)
        val otherOwner = sighting("bob", "single", 500)
        val unrelated = sighting("alice", "unlinked", 1_500, catId = null)
        val undated = sighting("alice", "undated", 0)
        val observations = listOf(
            observation(observedFirst, time = 100),
            observation(otherOwner, time = 0, catName = "小黑"),
        ).associateBy { requireNotNull(it.mapId) }

        val tracks = catTracks(
            listOf(postedFirst, otherOwner, observedFirst, unrelated, undated),
            mapOf("same-cat" to CatProfile(id = "same-cat", name = "大橘")),
            observations,
            viewer = "alice",
        ).associateBy { it.key }

        assertEquals(setOf("alice~same-cat", "bob~same-cat"), tracks.keys)
        val ownTrack = tracks.getValue("alice~same-cat")
        assertEquals("大橘 · alice", ownTrack.name)
        assertEquals(listOf(observedFirst, postedFirst), ownTrack.rows)
        assertEquals(100L, ownTrack.firstTime)
        assertEquals(1_000L, ownTrack.lastTime)
        val publicTrack = tracks.getValue("bob~same-cat")
        assertEquals("小黑 · bob", publicTrack.name)
        assertEquals(listOf(otherOwner), publicTrack.rows)
        assertEquals(500L, publicTrack.firstTime)
    }

    private fun sighting(owner: String, id: String, createdAt: Long, catId: String? = "same-cat"): RecordSummary =
        RecordSummary(
            CatRecord("$owner~$id", owner, "校园打卡", "", "大学城", 23.05, 113.4, createdAt, createdAt, catId = catId),
            owner, null, 0, 0, 0, false,
        )

    private fun observation(sighting: RecordSummary, time: Long, catName: String = ""): PublicObservation {
        val record = sighting.record
        return PublicObservation("map_records:${record.id}", "map_records", record.id, record.title, record.place,
            "", sighting.author, time, false, "", catName = catName)
    }
}
