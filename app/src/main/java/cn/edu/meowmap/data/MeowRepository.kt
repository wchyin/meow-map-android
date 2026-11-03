package cn.edu.meowmap.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

data class RecordInput(
    val id: String? = null,
    val title: String,
    val description: String,
    val place: String,
    val latitude: Double,
    val longitude: Double,
    val photos: List<String>,
)

/** The UI only talks to this contract; a remote source can be added behind it. */
interface MeowRepository {
    val user: Flow<LocalUser?>
    fun records(userId: String): Flow<List<RecordSummary>>
    val photos: Flow<List<RecordPhoto>>
    val comments: Flow<List<CommentSummary>>
    suspend fun initialize()
    suspend fun register(email: String, nickname: String, password: String)
    suspend fun login(email: String, password: String)
    fun logout()
    suspend fun rename(name: String)
    suspend fun save(input: RecordInput)
    suspend fun delete(recordId: String)
    suspend fun toggleLike(recordId: String)
    suspend fun comment(recordId: String, content: String)
    suspend fun deleteComment(commentId: String)
    suspend fun importPhoto(uri: Uri): String
    suspend fun getDraft(recordId: String?): EditorDraft?
    suspend fun saveDraft(input: RecordInput, locationSelected: Boolean)
    suspend fun discardDraft(recordId: String?, photos: List<String>)
    suspend fun discardPhotos(paths: List<String>)
    fun observations(userId: String): Flow<List<Observation>>
    suspend fun saveObservation(input: ObservationInput)
    suspend fun deleteObservation(id: String)
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LocalMeowRepository(context: Context, private val db: MeowDatabase) : MeowRepository {
    private val dao = db.dao()
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val session = MutableStateFlow(prefs.getString("user_id", "") ?: "")
    private val media = MediaStore(context)
    private val photoFiles = PhotoFiles(File(context.filesDir, "photos"))
    private val mutationMutex = Mutex()
    override val user = session.flatMapLatest { dao.observeUser(it) }
    override fun records(userId: String) = dao.observeRecords(userId)
    override val photos = dao.observePhotos()
    override val comments = dao.observeComments()
    override fun observations(userId: String) = dao.observeObservations(userId)

    override suspend fun saveObservation(input: ObservationInput) {
        val userId = requireUser()
        val now = System.currentTimeMillis()
        val validated = input.validated(now)
        db.withTransaction {
            val old = input.id?.let { dao.getObservation(it) }
            if (input.id != null) require(old != null && old.userId == userId) { "只能修改自己的观察手记" }
            val observation = Observation(input.id ?: UUID.randomUUID().toString(), userId,
                validated.observedAt, validated.place, validated.count, validated.catName, validated.appearance,
                old?.createdAt ?: now, now)
            if (old == null) dao.insertObservation(observation) else dao.updateObservation(observation)
        }
    }
    override suspend fun deleteObservation(id: String) {
        check(dao.deleteObservation(id, requireUser()) == 1) { "手记不存在或无权删除" }
    }

    override suspend fun initialize() {
        db.withTransaction {
            if (dao.userCount() == 0) {
                dao.insertUser(LocalUser("campus-demo", "demo@local.invalid", "校园观察员 · 示例", "", ""))
                val now = System.currentTimeMillis()
                listOf(
                    CatRecord("demo-library", "campus-demo", "图书馆旁的橘色身影", "下午在图书馆东边的花坛看到一只橘猫在晒太阳。", "图书馆东侧", 23.0507, 113.4033, now - 3_600_000, now, true),
                    CatRecord("demo-lake", "campus-demo", "湖边散步的小三花", "湖边树下趴着一只三花，四只爪子是白的。", "校园湖畔", 23.0477, 113.4060, now - 86_400_000, now, true),
                    CatRecord("demo-dorm", "campus-demo", "宿舍楼下的晚安猫", "晚饭后在宿舍楼下的小路旁看到的。", "生活区步道", 23.0541, 113.3996, now - 172_800_000, now, true),
                ).forEach { dao.insertRecord(it) }
            }
        }
    }

    private fun signIn(id: String) { prefs.edit().putString("user_id", id).apply(); session.value = id }
    private fun requireUser(): String = session.value.takeIf { it.isNotBlank() } ?: error("请先登录本机账号")
    override suspend fun register(email: String, nickname: String, password: String) = withContext(Dispatchers.IO) {
        val normalized = email.trim().lowercase()
        require(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(normalized)) { "请输入有效邮箱" }
        require(nickname.trim().length in 1..20) { "昵称需要 1～20 个字符" }
        require(password.length in 8..128) { "密码需要 8～128 个字符" }
        val salt = PasswordHasher.salt()
        val user = LocalUser(UUID.randomUUID().toString(), normalized, nickname.trim(), PasswordHasher.hash(password, salt), salt)
        db.withTransaction {
            require(dao.findUser(normalized) == null) { "这个邮箱已在本机注册" }
            dao.insertUser(user)
        }
        signIn(user.id)
    }
    override suspend fun login(email: String, password: String) = withContext(Dispatchers.IO) {
        require(password.length <= 128) { "邮箱或密码不正确" }
        val user = dao.findUser(email.trim().lowercase())
        require(user != null && PasswordHasher.verify(password, user.salt, user.passwordHash)) { "邮箱或密码不正确" }
        signIn(user.id)
    }
    override fun logout() { signIn("") }
    override suspend fun rename(name: String) {
        require(name.trim().length in 1..20) { "昵称需要 1～20 个字符" }
        dao.renameUser(requireUser(), name.trim())
    }
    override suspend fun save(input: RecordInput): Unit = withContext(Dispatchers.IO) { mutationMutex.withLock {
        val userId = requireUser()
        require(input.title.trim().length in 1..60) { "标题需要 1～60 个字符" }
        require(input.description.length <= 2000) { "描述最多 2000 个字符" }
        require(input.place.trim().length in 1..80) { "请填写地点，最多 80 个字符" }
        require(input.latitude.isFinite() && input.latitude in -85.0..85.0 && input.longitude.isFinite() && input.longitude in -180.0..180.0) { "请选择有效的地图位置" }
        require(input.photos.size <= 6) { "每条记录最多 6 张照片" }
        val candidates = db.withTransaction {
            val old = input.id?.let { dao.getRecord(it) }
            if (input.id != null) require(old != null && old.userId == userId) { "只能修改自己的记录" }
            val now = System.currentTimeMillis()
            val previousPhotos = dao.photoPaths(input.id ?: "") + (dao.draft(userId, input.id ?: "new")?.photos ?: emptyList())
            val record = CatRecord(input.id ?: UUID.randomUUID().toString(), userId, input.title.trim(), input.description.trim(), input.place.trim(), input.latitude, input.longitude, old?.createdAt ?: now, now)
            if (old == null) dao.insertRecord(record) else dao.updateRecord(record)
            dao.deletePhotos(record.id)
            dao.insertPhotos(input.photos.mapIndexed { index, path -> RecordPhoto(UUID.randomUUID().toString(), record.id, path, index) })
            dao.deleteDraft(userId, input.id ?: "new")
            previousPhotos
        }
        cleanup(candidates)
    } }
    override suspend fun delete(recordId: String): Unit = withContext(Dispatchers.IO) { mutationMutex.withLock {
        val userId = requireUser()
        val candidates = db.withTransaction {
            val paths = dao.photoPaths(recordId) + (dao.draft(userId, recordId)?.photos ?: emptyList())
            check(dao.deleteRecord(recordId, userId) == 1) { "记录不存在或无权删除" }
            dao.deleteDraft(userId, recordId)
            paths
        }
        cleanup(candidates)
    } }
    override suspend fun toggleLike(recordId: String) {
        val userId = requireUser()
        db.withTransaction {
            require(dao.getRecord(recordId) != null) { "记录已删除" }
            if (dao.isLiked(recordId, userId)) dao.deleteLike(recordId, userId) else dao.insertLike(RecordLike(recordId, userId))
        }
    }
    override suspend fun comment(recordId: String, content: String) {
        val userId = requireUser()
        require(content.trim().length in 1..500) { "评论需要 1～500 个字符" }
        dao.insertComment(RecordComment(UUID.randomUUID().toString(), recordId, userId, content.trim(), System.currentTimeMillis()))
    }
    override suspend fun deleteComment(commentId: String) { dao.deleteComment(commentId, requireUser()) }
    override suspend fun importPhoto(uri: Uri) = media.import(uri)

    override suspend fun getDraft(recordId: String?) = dao.draft(requireUser(), recordId ?: "new")
    override suspend fun saveDraft(input: RecordInput, locationSelected: Boolean): Unit = withContext(Dispatchers.IO) { mutationMutex.withLock {
        val userId = requireUser()
        require(input.title.length <= 60 && input.description.length <= 2000 && input.place.length <= 80) { "草稿内容超出长度限制" }
        require(input.photos.size <= 6) { "每条记录最多 6 张照片" }
        require(input.latitude.isFinite() && input.latitude in -85.0..85.0 && input.longitude.isFinite() && input.longitude in -180.0..180.0) { "草稿位置无效" }
        val candidates = db.withTransaction {
            if (input.id != null) require(dao.getRecord(input.id)?.userId == userId) { "只能保存自己的记录草稿" }
            val slot = input.id ?: "new"
            val old = dao.draft(userId, slot)
            dao.saveDraft(EditorDraft(userId, slot, input.title, input.description, input.place, input.latitude, input.longitude, locationSelected, input.photos, System.currentTimeMillis()))
            old?.photos ?: emptyList()
        }
        cleanup(candidates)
    } }
    override suspend fun discardDraft(recordId: String?, photos: List<String>): Unit = withContext(Dispatchers.IO) { mutationMutex.withLock {
        val userId = requireUser()
        val candidates = db.withTransaction {
            val slot = recordId ?: "new"
            val old = dao.draft(userId, slot)
            dao.deleteDraft(userId, slot)
            photos + (old?.photos ?: emptyList())
        }
        cleanup(candidates)
    } }
    override suspend fun discardPhotos(paths: List<String>): Unit = withContext(Dispatchers.IO) { mutationMutex.withLock { cleanup(paths) } }

    private suspend fun cleanup(candidates: List<String>) {
        if (candidates.isEmpty()) return
        // File cleanup is best-effort after commit; a cleanup failure must not report a saved record as failed.
        runCatching {
            val referenced = dao.referencedPhotoPaths() + dao.allDrafts().flatMap { it.photos }
            photoFiles.deleteUnreferenced(candidates, referenced)
        }
    }
}
