package cn.edu.meowmap

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.edu.meowmap.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: MeowDatabase
    private lateinit var repo: LocalMeowRepository
    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("session", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, MeowDatabase::class.java).build()
        repo = LocalMeowRepository(context, db)
    }
    @After fun close() { db.close() }
    private fun input(id: String? = null, title: String = "图书馆橘猫") = RecordInput(id, title, "在晒太阳", "图书馆", 23.05, 113.4, emptyList())

    @Test fun recordsLikesCommentsAndCascadeDeletion() = runBlocking {
        repo.register("alice@example.com", "Alice", "campus123")
        val user = repo.user.first()!!
        repo.save(input())
        val id = repo.records(user.id).first().single().record.id
        repo.toggleLike(id)
        repo.comment(id, "今天我也看到了")
        val liked = repo.records(user.id).first().single()
        assertTrue(liked.liked); assertEquals(1, liked.likeCount); assertEquals(1, liked.commentCount)
        repo.toggleLike(id)
        assertFalse(repo.records(user.id).first().single().liked)
        repo.save(input(id, "更新后的标题"))
        assertEquals("更新后的标题", repo.records(user.id).first().single().record.title)
        repo.delete(id)
        assertTrue(repo.records(user.id).first().isEmpty())
        assertTrue(repo.comments.first().isEmpty())
    }
    @Test fun anotherAccountCannotEditOrDeleteRecord() = runBlocking {
        repo.register("alice@example.com", "Alice", "campus123")
        repo.save(input())
        val id = repo.records(repo.user.first()!!.id).first().single().record.id
        repo.logout()
        repo.register("bob@example.com", "Bob", "campus456")
        assertTrue(runCatching { repo.save(input(id, "非法修改")) }.isFailure)
        assertTrue(runCatching { repo.delete(id) }.isFailure)
        assertEquals("图书馆橘猫", db.dao().getRecord(id)!!.title)
    }
    @Test fun duplicateAccountAndWrongPasswordAreRejected() = runBlocking {
        repo.register("ALICE@example.com", "Alice", "campus123")
        repo.logout()
        assertTrue(runCatching { repo.register("alice@example.com", "Other", "campus123") }.isFailure)
        assertTrue(runCatching { repo.login("alice@example.com", "wrong-password") }.isFailure)
        repo.login("alice@example.com", "campus123")
        assertEquals("Alice", repo.user.first()!!.nickname)
    }
    @Test fun sampleInitializationIsIdempotent() = runBlocking {
        repo.initialize(); repo.initialize()
        assertEquals(3, repo.records("").first().size)
        assertTrue(repo.records("").first().all { it.record.demo })
    }
    @Test fun draftSurvivesRepositoryRecreationAndIsPrivateToItsAccount() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repo.register("alice@example.com", "Alice", "campus123")
        repo.saveDraft(input().copy(title = "尚未完成", place = ""), false)
        val reopened = LocalMeowRepository(context, db)
        assertEquals("尚未完成", reopened.getDraft(null)!!.title)
        assertFalse(reopened.getDraft(null)!!.locationSelected)
        assertTrue(repo.records(repo.user.first()!!.id).first().isEmpty())
        repo.logout(); repo.register("bob@example.com", "Bob", "campus456")
        assertNull(repo.getDraft(null))
        repo.logout(); repo.login("alice@example.com", "campus123")
        assertEquals("尚未完成", repo.getDraft(null)!!.title)
        repo.save(input())
        assertNull(repo.getDraft(null))
    }
    @Test fun sharedPhotoIsOnlyDeletedAfterBothRecordAndDraftReleaseIt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repo.register("alice@example.com", "Alice", "campus123")
        val photo = File(File(context.filesDir, "photos").apply { mkdirs() }, "${UUID.randomUUID()}.jpg").apply { writeBytes(byteArrayOf(1)) }
        try {
            repo.save(input().copy(photos = listOf(photo.path)))
            val id = repo.records(repo.user.first()!!.id).first().single().record.id
            repo.saveDraft(input().copy(photos = listOf(photo.path)), true)
            repo.delete(id)
            assertTrue(photo.exists())
            repo.discardDraft(null, emptyList())
            assertFalse(photo.exists())
        } finally { photo.delete() }
    }
    @Test fun automaticMigrationPreservesExistingV1Data() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        var migrated: MeowDatabase? = null
        try {
            val schema = JSONObject(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open("cn.edu.meowmap.data.MeowDatabase/1.json").bufferedReader().use { it.readText() }).getJSONObject("database")
            SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
                val entities = schema.getJSONArray("entities")
                for (index in 0 until entities.length()) {
                    val entity = entities.getJSONObject(index)
                    val table = entity.getString("tableName")
                    old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices")
                    if (indices != null) for (i in 0 until indices.length()) old.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
                old.execSQL("INSERT INTO users VALUES ('old-user','old@example.com','旧用户','hash','salt')")
                old.execSQL("INSERT INTO records VALUES ('old-record','old-user','旧记录','描述','图书馆',23,113,1,1,0)")
                old.version = 1
            }
            migrated = Room.databaseBuilder(context, MeowDatabase::class.java, name).build()
            assertEquals("旧用户", migrated.dao().findUser("old@example.com")!!.nickname)
            assertEquals("旧记录", migrated.dao().getRecord("old-record")!!.title)
            assertTrue(migrated.dao().allDrafts().isEmpty())
            assertTrue(migrated.observationRows("old-user").isEmpty())
        } finally { migrated?.close(); context.deleteDatabase(name) }
    }

    private suspend fun MeowDatabase.observationRows(userId: String) = dao().observeObservations(userId).first()
    @Test fun observationsArePrivateAndEditableWithoutCreatingMapRecords() = runBlocking {
        repo.register("alice@example.com", "Alice", "campus123")
        val alice = repo.user.first()!!.id
        val input = ObservationInput(observedAt = System.currentTimeMillis() - 1000, place = "图书馆", count = 2, catName = "小橘", appearance = "白爪子")
        repo.saveObservation(input)
        val note = repo.observations(alice).first().single()
        repo.saveObservation(input.copy(id = note.id, count = 3))
        assertEquals(3, repo.observations(alice).first().single().count)
        assertEquals(note.createdAt, repo.observations(alice).first().single().createdAt)
        assertTrue(repo.records(alice).first().isEmpty())
        repo.logout(); repo.register("bob@example.com", "Bob", "campus456")
        assertTrue(repo.observations(repo.user.first()!!.id).first().isEmpty())
        assertTrue(runCatching { repo.saveObservation(input.copy(id = note.id)) }.isFailure)
        assertTrue(runCatching { repo.deleteObservation(note.id) }.isFailure)
        repo.logout(); repo.login("alice@example.com", "campus123")
        repo.deleteObservation(note.id)
        assertTrue(repo.observations(alice).first().isEmpty())
    }
}
