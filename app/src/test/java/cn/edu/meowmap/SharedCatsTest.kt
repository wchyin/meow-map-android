package cn.edu.meowmap

import cn.edu.meowmap.data.*
import cn.edu.meowmap.ui.catTracks
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class SharedCatsTest {
    @Test fun updatePathKeepsCreatorIdentity() {
        assertEquals("alice~cat-1", catReferencePath("alice~cat-1"))
    }
    @Test(expected = IllegalArgumentException::class) fun updatePathRejectsMalformedIdentity() {
        catReferencePath("alice~cat-1/other")
    }
    @Test fun sharedSearchIdentityIsPreservedAndNotesStayPrivate() {
        val row = JsonParser.parseString("""{"id":"alice~cat-1","name":"小橘","is_mine":false,"notes":"private","species":"橘猫"}""").asJsonObject
        val cat = parseCloudCat(row, "https://example.test")
        assertEquals("alice~cat-1", cat.id)
        assertEquals("小橘", cat.name)
        assertFalse(cat.isMine)
        assertEquals("", cat.notes)
    }
    @Test fun ownCatKeepsNotesAndUploadedAvatar() {
        val row = JsonParser.parseString("""{"id":"alice~cat-1","name":"小橘","is_mine":true,"notes":"观察备注","avatar":"/uploads/12345678-1234-1234-1234-123456789abc"}""").asJsonObject
        val cat = parseCloudCat(row, "https://example.test/meow-api")
        assertTrue(cat.isMine)
        assertEquals("观察备注", cat.notes)
        assertTrue(cat.avatar!!.startsWith("https://example.test/meow-api/uploads/"))
    }
    private fun record(owner: String, id: String, cat: String) = RecordSummary(
        CatRecord("$owner~$id", owner, "看到小猫", "", "校园", 23.05, 113.4, 100, 100, catId = cat),
        owner, null, 0, 0, 0, false)
    @Test fun sameSharedCatHasOneTrackAcrossObservers() {
        val rows = listOf(record("alice", "one", "alice~cat-1"), record("bob", "two", "alice~cat-1"))
        val tracks = catTracks(rows, emptyMap(), emptyMap(), "bob")
        assertEquals(1, tracks.size)
        assertEquals("alice~cat-1", tracks.single().key)
        assertEquals(2, tracks.single().rows.size)
    }
    @Test fun differentCatsAreNeverMergedBySameLocalId() {
        val rows = listOf(record("alice", "one", "alice~cat-1"), record("bob", "two", "bob~cat-1"))
        assertEquals(2, catTracks(rows, emptyMap(), emptyMap(), null).size)
    }
}
