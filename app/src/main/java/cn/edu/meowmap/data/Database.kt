package cn.edu.meowmap.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "users", indices = [Index(value = ["email"], unique = true)])
data class LocalUser(
    @PrimaryKey val id: String,
    val email: String,
    val nickname: String,
    val passwordHash: String,
    val salt: String,
)

@Entity(tableName = "records", indices = [Index("userId"), Index("createdAt")], foreignKeys = [
    ForeignKey(entity = LocalUser::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)
])
data class CatRecord(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val description: String,
    val place: String,
    val latitude: Double,
    val longitude: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val demo: Boolean = false,
)

@Entity(tableName = "photos", indices = [Index("recordId")], foreignKeys = [
    ForeignKey(entity = CatRecord::class, parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.CASCADE)
])
data class RecordPhoto(@PrimaryKey val id: String, val recordId: String, val path: String, val position: Int)

@Entity(tableName = "likes", primaryKeys = ["recordId", "userId"], indices = [Index("userId")], foreignKeys = [
    ForeignKey(entity = CatRecord::class, parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = LocalUser::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)
])
data class RecordLike(val recordId: String, val userId: String)

@Entity(tableName = "comments", indices = [Index("recordId"), Index("userId")], foreignKeys = [
    ForeignKey(entity = CatRecord::class, parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = LocalUser::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)
])
data class RecordComment(@PrimaryKey val id: String, val recordId: String, val userId: String, val content: String, val createdAt: Long)

data class RecordSummary(
    @Embedded val record: CatRecord,
    val author: String,
    val cover: String?,
    val photoCount: Int,
    val likeCount: Int,
    val commentCount: Int,
    val liked: Boolean,
)

data class CommentSummary(@Embedded val comment: RecordComment, val author: String)

/** One draft per account and editor slot; editing an existing record has its own slot. */
@Entity(tableName = "drafts", primaryKeys = ["userId", "slot"])
data class EditorDraft(
    val userId: String,
    val slot: String,
    val title: String,
    val description: String,
    val place: String,
    val latitude: Double,
    val longitude: Double,
    val locationSelected: Boolean,
    val photos: List<String>,
    val updatedAt: Long,
)

class PhotoListConverter {
    @TypeConverter fun encode(paths: List<String>): String = com.google.gson.Gson().toJson(paths)
    @TypeConverter fun decode(json: String): List<String> =
        com.google.gson.Gson().fromJson(json, Array<String>::class.java).toList()
}

@Dao
interface MeowDao {
    @Query("SELECT * FROM users WHERE id = :id")
    fun observeUser(id: String): Flow<LocalUser?>
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun findUser(email: String): LocalUser?
    @Query("SELECT COUNT(*) FROM users")
    suspend fun userCount(): Int
    @Insert suspend fun insertUser(user: LocalUser)
    @Query("UPDATE users SET nickname = :name WHERE id = :id")
    suspend fun renameUser(id: String, name: String)

    @Query("""
        SELECT r.*, u.nickname AS author,
        (SELECT path FROM photos WHERE recordId = r.id ORDER BY position LIMIT 1) AS cover,
        (SELECT COUNT(*) FROM photos WHERE recordId = r.id) AS photoCount,
        (SELECT COUNT(*) FROM likes WHERE recordId = r.id) AS likeCount,
        (SELECT COUNT(*) FROM comments WHERE recordId = r.id) AS commentCount,
        EXISTS(SELECT 1 FROM likes WHERE recordId = r.id AND userId = :userId) AS liked
        FROM records r JOIN users u ON u.id = r.userId ORDER BY r.createdAt DESC, r.id
    """)
    fun observeRecords(userId: String): Flow<List<RecordSummary>>
    @Query("SELECT * FROM records WHERE id = :id") suspend fun getRecord(id: String): CatRecord?
    @Insert suspend fun insertRecord(record: CatRecord)
    @Update suspend fun updateRecord(record: CatRecord)
    @Query("DELETE FROM records WHERE id = :id AND userId = :userId")
    suspend fun deleteRecord(id: String, userId: String): Int
    @Query("SELECT * FROM photos ORDER BY recordId, position")
    fun observePhotos(): Flow<List<RecordPhoto>>
    @Query("SELECT path FROM photos WHERE recordId = :id") suspend fun photoPaths(id: String): List<String>
    @Query("SELECT path FROM photos") suspend fun referencedPhotoPaths(): List<String>
    @Query("DELETE FROM photos WHERE recordId = :id") suspend fun deletePhotos(id: String)
    @Insert suspend fun insertPhotos(photos: List<RecordPhoto>)

    @Query("SELECT EXISTS(SELECT 1 FROM likes WHERE recordId = :recordId AND userId = :userId)")
    suspend fun isLiked(recordId: String, userId: String): Boolean
    @Insert suspend fun insertLike(like: RecordLike)
    @Query("DELETE FROM likes WHERE recordId = :recordId AND userId = :userId")
    suspend fun deleteLike(recordId: String, userId: String)
    @Query("SELECT c.*, u.nickname AS author FROM comments c JOIN users u ON u.id = c.userId ORDER BY c.createdAt, c.id")
    fun observeComments(): Flow<List<CommentSummary>>
    @Insert suspend fun insertComment(comment: RecordComment)
    @Query("DELETE FROM comments WHERE id = :id AND userId = :userId")
    suspend fun deleteComment(id: String, userId: String)

    @Query("SELECT * FROM drafts WHERE userId = :userId AND slot = :slot")
    suspend fun draft(userId: String, slot: String): EditorDraft?
    @Query("SELECT * FROM drafts") suspend fun allDrafts(): List<EditorDraft>
    @Upsert suspend fun saveDraft(draft: EditorDraft)
    @Query("DELETE FROM drafts WHERE userId = :userId AND slot = :slot")
    suspend fun deleteDraft(userId: String, slot: String)

    @Query("SELECT * FROM observations WHERE userId = :userId ORDER BY observedAt DESC, id DESC")
    fun observeObservations(userId: String): Flow<List<Observation>>
    @Query("SELECT * FROM observations WHERE id = :id")
    suspend fun getObservation(id: String): Observation?
    @Insert suspend fun insertObservation(observation: Observation)
    @Update suspend fun updateObservation(observation: Observation)
    @Query("DELETE FROM observations WHERE id = :id AND userId = :userId")
    suspend fun deleteObservation(id: String, userId: String): Int
}

@TypeConverters(PhotoListConverter::class)
@Database(
    entities = [LocalUser::class, CatRecord::class, RecordPhoto::class, RecordLike::class, RecordComment::class, EditorDraft::class, Observation::class],
    version = 3, exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class MeowDatabase : RoomDatabase() {
    abstract fun dao(): MeowDao
}
