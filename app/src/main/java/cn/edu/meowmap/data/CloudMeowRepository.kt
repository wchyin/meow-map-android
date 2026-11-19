package cn.edu.meowmap.data

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.google.gson.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.UUID

/** Original desktop API adapter. Room v3 data is left intact; cloud drafts use separate account slots. */
class CloudMeowRepository(private val context: Context, db: MeowDatabase, endpoint: String) : MeowRepository {
    private val session = CloudSession(context)
    private var token: String? = null
    private val api = OriginalApi(endpoint) { token }
    private val dao = db.dao()
    private val media = MediaStore(context)
    private val photoFiles = PhotoFiles(File(context.filesDir, "photos"))
    private val mutex = Mutex()
    private val currentUser = MutableStateFlow<LocalUser?>(null)
    private val recordRows = MutableStateFlow<List<RecordSummary>>(emptyList())
    private val photoRows = MutableStateFlow<List<RecordPhoto>>(emptyList())
    private val commentRows = MutableStateFlow<List<CommentSummary>>(emptyList())
    private val profilePhoto = MutableStateFlow<String?>(null)
    override val profileAvatar: Flow<String?> = profilePhoto
    private val publicRows = MutableStateFlow<List<PublicObservation>>(emptyList())
    override val publicObservations: Flow<List<PublicObservation>> = publicRows
    private val observationRows = MutableStateFlow<List<Observation>>(emptyList())
    private val catRows = MutableStateFlow<List<CatProfile>>(emptyList())
    private val message = MutableStateFlow<String?>(null)
    private val statistics = MutableStateFlow<String?>(null)
    override val statisticsSnapshot: Flow<String?> = statistics
    private var raw = JsonObject()
    private var restored = false
    override val user: Flow<LocalUser?> = currentUser
    override val photos: Flow<List<RecordPhoto>> = photoRows
    override val comments: Flow<List<CommentSummary>> = commentRows
    override val cats: Flow<List<CatProfile>> = catRows
    override val notice: Flow<String?> = message
    override fun records(userId: String): Flow<List<RecordSummary>> = recordRows
    override fun observations(userId: String): Flow<List<Observation>> = observationRows.map { rows -> rows.filter { it.userId == userId } }
    private fun account() = currentUser.value?.id ?: error("请先登录云端账号")
    private fun draftAccount() = "cloud:${account()}"
    private fun cacheFile() = File(context.filesDir, "cloud-cache-${currentUser.value?.id ?: "guest"}.json")
    private fun persist() {
        val file = AtomicFile(cacheFile()); val stream = file.startWrite()
        try { stream.write(raw.toString().toByteArray()); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
    private fun restoreCache() {
        raw = runCatching { JsonParser.parseString(cacheFile().readText()).asJsonObject }.getOrDefault(JsonObject())
        render()
    }
    private fun rows(kind: String): List<JsonObject> = raw.getAsJsonArray(kind)?.map { it.asJsonObject } ?: emptyList()
    private fun render() {
        statistics.value = JsonObject().apply {
            add("records", raw.getAsJsonArray("records") ?: JsonArray())
            add("map_records", raw.getAsJsonArray("map_records") ?: JsonArray())
            add("cats", raw.getAsJsonArray("cats") ?: JsonArray())
            add("owner", currentUser.value?.id?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
        }.toString()
        val old = recordRows.value.associateBy { it.record.id }
        recordRows.value = rows("map_records").map { row ->
            val record = cloudRecord(row)
            val images = images(row).map { imageUrl(it) }
            val previous = old[record.id]
            RecordSummary(record, row.getAsJsonObject("profiles")?.text("username") ?: "猫友 ${record.userId.take(6)}",
                images.firstOrNull(), images.size, previous?.likeCount ?: 0, previous?.commentCount ?: 0, previous?.liked ?: false)
        }.sortedByDescending { it.record.createdAt }
        photoRows.value = rows("map_records").flatMap { row ->
            val id = cloudRecord(row).id
            images(row).mapIndexed { index, path -> RecordPhoto("$id:$index", id, imageUrl(path), index) }
        }
        catRows.value = rows("cats").map { CatProfile(it.text("id"), it.text("name"), it.text("species"), it.text("features"), it.text("notes"), it.text("avatar").takeIf(String::isNotBlank)?.let { avatar ->
            if (avatar.startsWith("/uploads/")) imageUrl(avatar) else avatar
        }) }
        val mapMetadata = rows("map_records").associateBy {
            "${it.text("user_id")}~${it.text("id")}"
        }
        publicRows.value = rows("observations").map { row ->
            val kind = row.text("kind")
            val id = "${row.text("user_id")}~${row.text("id")}"
            PublicObservation("$kind:$id", kind, id.takeIf { kind == "map_records" },
                row.text("title").ifBlank { row.text("cat_name").ifBlank { row.text("species", "猫咪观察") } },
                row.text("location").ifBlank { row.text("area").ifBlank { row.text("title") } },
                row.text("species"), row.getAsJsonObject("profiles")?.text("username").orEmpty(),
                originalTime(row.text("time")).takeIf { it > 0 } ?: originalTime(row.text("created_at")),
                row.text("is_demo") in setOf("true", "1") || row.text("demo_batch").isNotBlank(),
                row.text("university").ifBlank { row.text("area").substringBefore('·') },
                catName = row.text("cat_name").ifBlank { mapMetadata[id]?.text("cat_name").orEmpty() },
                catAvatar = row.text("cat_avatar").ifBlank {
                    mapMetadata[id]?.text("cat_avatar").orEmpty()
                }.takeIf { it.startsWith("builtin:") },
                description = row.text("desc").ifBlank { row.text("notes").ifBlank { row.text("gender") } },
                count = row.text("count").toIntOrNull()?.coerceAtLeast(1) ?: 1,
                hasSpecificLocation = row.text("location").isNotBlank() || row.text("title").isNotBlank())
        }.distinctBy { it.key }
        observationRows.value = rows("records").map { row ->
            val time = originalTime(row.text("time"))
            Observation(row.text("id"), currentUser.value?.id ?: "", time, row.text("location"),
                row.text("count", "1").toIntOrNull() ?: 1, row.text("species"), row.text("gender").ifBlank { row.text("appearance").ifBlank { row.text("notes") } },
                originalTime(row.text("created_at")), originalTime(row.text("created_at")), row.text("cat_id").ifBlank { null })
        }
    }
    private fun images(row: JsonObject): List<String> = row.getAsJsonArray("images")?.map { it.asString }
        ?: row.text("image").takeIf { it.isNotBlank() }?.let(::listOf) ?: emptyList()
    private fun imageUrl(path: String): String {
        require(path.matches(Regex("/uploads/[a-f0-9-]{36}"))) { "云端图片引用无效" }
        return endpointRoot + path
    }
    private val endpointRoot = endpoint.trimEnd('/')
    private suspend fun readPages(route: String): JsonArray {
        val maps = JsonArray(); var offset: Int? = 0
        while (offset != null) {
            val page = api.json("$route?offset=$offset").asJsonObject
            page.getAsJsonArray("items").forEach { maps.add(it) }
            val following = page.get("nextOffset")?.takeUnless { it.isJsonNull }?.asInt
            require(following == null || (following > offset && following <= 100_000)) { "云端分页超出范围，请缩小数据集" }
            offset = following
        }
        return maps
    }
    private suspend fun sync() {
        val next = JsonObject()
        next.add("map_records", readPages("/map_records"))
        try { next.add("observations", readPages("/observations")) }
        catch (e: ApiFailure) {
            if (e.status != 404) throw e
            next.add("observations", raw.getAsJsonArray("observations") ?: JsonArray())
        }
        if (currentUser.value != null) {
            next.add("cats", api.json("/cats").asJsonArray)
            next.add("records", api.json("/records").asJsonArray)
            try { applyProfile(api.json("/profile").asJsonObject) }
            catch (e: ApiFailure) { if (e.status != 404) throw e }
        }
        raw = next; render(); persist(); message.value = null
    }
    private suspend fun readSync() {
        try { sync() }
        catch (e: CancellationException) { throw e }
        catch (e: ApiFailure) {
            if (e.status == 401) { clearSession(); restoreCache(); message.value = "登录已过期，请重新登录；草稿仍保留" }
            else message.value = "刷新失败，正在显示缓存：${e.message}"
        }
        catch (e: Exception) { message.value = "暂时无法连接云端，正在显示缓存：${e.message}" }
    }
    override suspend fun initialize() = withContext(Dispatchers.IO) { mutex.withLock {
        if (!restored) {
            session.read()?.let { encoded -> runCatching {
                val saved = JsonParser.parseString(encoded).asJsonObject
                token = saved.text("token")
                currentUser.value = Gson().fromJson(saved.get("user"), LocalUser::class.java)
            } }
            restoreCache(); restored = true
        }
        readSync()
    } }
    override suspend fun refresh() = initialize()
    private suspend fun authenticate(route: String, email: String, password: String) = withContext(Dispatchers.IO) { mutex.withLock {
        val response = api.json("/auth/$route", "POST", JsonObject().apply { addProperty("email", email.trim()); addProperty("password", password) }).asJsonObject
        val remote = response.getAsJsonObject("user")
        val user = LocalUser(remote.text("id"), remote.text("email"), "猫友 ${remote.text("id").take(6)}", "", "")
        val credential = JsonObject().apply { addProperty("token", response.text("token")); add("user", Gson().toJsonTree(user)) }
        session.save(credential.toString()); token = response.text("token"); currentUser.value = user
        commentRows.value = emptyList(); recordRows.value = emptyList(); restoreCache(); readSync()
    } }
    override suspend fun register(email: String, nickname: String, password: String) = authenticate("register", email, password)
    override suspend fun login(email: String, password: String) = authenticate("login", email, password)
    private fun clearSession() { session.save(null); token = null; currentUser.value = null; profilePhoto.value = null; commentRows.value = emptyList(); recordRows.value = emptyList() }
    override fun logout() {
        clearSession(); restoreCache(); message.value = null
    }
    override suspend fun endSession() = withContext(Dispatchers.IO) { mutex.withLock {
        try { if (token != null) api.json("/auth/logout", "POST", JsonObject()) }
        finally { logout() }
        Unit
    } }
    private fun applyProfile(response: JsonObject) {
        val user = currentUser.value ?: return
        val profile = response.getAsJsonObject("profile") ?: return
        profilePhoto.value = profile.text("avatar_url").takeIf { it.startsWith("data:image/jpeg;base64,") }
        val name = profile.text("username")
        if (name.isBlank() || name == user.nickname) return
        val updated = user.copy(nickname = name)
        session.save(JsonObject().apply {
            addProperty("token", token)
            add("user", Gson().toJsonTree(updated))
        }.toString())
        currentUser.value = updated
    }
    override suspend fun rename(name: String) = updateProfile(name, null, false)
    override suspend fun updateProfile(name: String, avatar: Uri?, resetAvatar: Boolean) = withContext(Dispatchers.IO) { mutex.withLock {
        val body = JsonObject().apply {
            addProperty("username", name.trim())
            if (resetAvatar) add("avatar_url", JsonNull.INSTANCE)
            if (avatar != null) {
                val path = media.import(avatar)
                try {
                    val original = requireNotNull(android.graphics.BitmapFactory.decodeFile(path)) { "无法读取头像" }
                    val size = minOf(original.width, original.height)
                    val square = android.graphics.Bitmap.createBitmap(original, (original.width-size)/2, (original.height-size)/2, size, size)
                    val thumbnail = android.graphics.Bitmap.createScaledBitmap(square, 192, 192, true)
                    try {
                        val bytes = java.io.ByteArrayOutputStream()
                        thumbnail.compress(android.graphics.Bitmap.CompressFormat.JPEG, 78, bytes)
                        if (bytes.size() > 40*1024) {
                            bytes.reset()
                            thumbnail.compress(android.graphics.Bitmap.CompressFormat.JPEG, 55, bytes)
                        }
                        require(bytes.size() in 5..40*1024) { "头像压缩后超过 40 KiB" }
                        addProperty("avatar_url", "data:image/jpeg;base64," + android.util.Base64.encodeToString(bytes.toByteArray(), android.util.Base64.NO_WRAP))
                    } finally {
                        if (thumbnail !== square) thumbnail.recycle()
                        if (square !== original) square.recycle()
                        original.recycle()
                    }
                } finally { File(path).delete() }
            }
        }
        applyProfile(write("/profile", "PUT", body).asJsonObject)
    } }
    override suspend fun catAvatar(path: String): ByteArray? = mutex.withLock {
        if (currentUser.value == null || !path.startsWith("$endpointRoot/uploads/")) return@withLock null
        val route = path.removePrefix(endpointRoot)
        imageUrl(route)
        api.request(route)
    }
    override suspend fun askAssistant(question: String): AssistantAnswer = withContext(Dispatchers.IO) { mutex.withLock {
        require(question.trim().length in 1..300) { "请输入 1–300 个字符的问题" }
        val result = write("/cat-assistant", "POST", JsonObject().apply { addProperty("question", question.trim()) }).asJsonObject
        AssistantAnswer(result.text("answer"), result.getAsJsonArray("sources")?.map { element ->
            val row = element.asJsonObject
            val mapId = row.text("id").takeIf { row.has("lat") && !row.get("lat").isJsonNull && row.has("lng") && !row.get("lng").isJsonNull }
            AssistantSource(row.number("reference").toInt(), mapId,
                row.text("name").ifBlank { row.text("title").ifBlank { "公开观察" } }, row.text("location"))
        }.orEmpty())
    } }
    private fun remoteId(id: String): String = id.substringAfter('~').also { require(it.matches(Regex("[\\w-]{1,80}"))) }
    private fun social(id: String): String {
        val parts = id.split('~'); require(parts.size == 2 && parts.all { it.matches(Regex("[\\w-]{1,80}")) })
        return "/social/${parts[0]}/${parts[1]}"
    }
    override suspend fun loadInteractions(recordId: String) = withContext(Dispatchers.IO) { mutex.withLock {
        val route = social(recordId) + "/interactions"
        val response = try {
            api.json(route).asJsonObject
        } catch (error: ApiFailure) {
            if (error.status != 401) throw error
            // Public details remain readable when another device has revoked this session.
            clearSession()
            raw.remove("records")
            raw.remove("cats")
            render()
            persist()
            message.value = "账号已退出，请登录后发布或互动"
            api.json(route, authenticated = false).asJsonObject
        }
        val comments = response.getAsJsonArray("comments").map { it.asJsonObject }.map { row ->
            CommentSummary(RecordComment(row.text("id"), recordId, row.text("user_id"), row.text("content"), originalTime(row.text("created_at"))),
                row.getAsJsonObject("profiles")?.text("username") ?: "猫友")
        }
        commentRows.value = commentRows.value.filterNot { it.comment.recordId == recordId } + comments
        recordRows.value = recordRows.value.map { if (it.record.id != recordId) it else it.copy(likeCount = response.number("likes").toInt(),
            liked = response.get("is_liked")?.asBoolean ?: false, commentCount = comments.size) }
    } }
    private suspend fun write(route: String, method: String, body: JsonObject? = null): JsonElement {
        account()
        try { return api.json(route, method, body) }
        catch (e: ApiFailure) { if (e.status == 401) { clearSession(); restoreCache() }; throw e }
    }
    private suspend fun upload(path: String): String {
        if (path.startsWith("$endpointRoot/uploads/")) return path.removePrefix(endpointRoot)
        val file = File(path)
        require(file.canonicalFile.parentFile == File(context.filesDir, "photos").canonicalFile && file.isFile) { "图片不存在，请重新选择" }
        require(file.length() <= 2*1024*1024) { "图片超过 2 MiB，请选择较小图片" }
        return api.jsonUpload(file.readBytes())
    }
    private suspend fun OriginalApi.jsonUpload(bytes: ByteArray): String = JsonParser.parseString(request("/uploads", "POST", bytes, true).toString(Charsets.UTF_8)).asJsonObject.text("url")
    override suspend fun save(input: RecordInput) = withContext(Dispatchers.IO) { mutex.withLock {
        val owner = account()
        require(input.title.trim().length in 1..60 && input.description.length <= 2000 && input.place.trim().length in 1..80) { "请填写标题和地点，并检查文字长度" }
        require(input.photos.size <= 6 && input.latitude.isFinite() && input.latitude in -85.0..85.0 && input.longitude.isFinite() && input.longitude in -180.0..180.0)
        if (input.id != null) require(recordRows.value.any { it.record.id == input.id && it.record.userId == owner }) { "只能修改自己的记录" }
        val point = Coordinates.toGcj(input.latitude, input.longitude)
        val area = CampusAreas.load(context).find { it.contains(input.latitude, input.longitude) }
        val body = JsonObject().apply {
            addProperty("title", input.title.trim()); addProperty("desc", input.description); addProperty("area", input.place.trim())
            addProperty("lat", point.first); addProperty("lng", point.second); addProperty("coordinateSystem", "GCJ02")
            addProperty("university", area?.university.orEmpty()); addProperty("campus", area?.campus.orEmpty())
            add("cat_id", input.catId?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
            add("images", JsonArray().apply { input.photos.forEach { add(upload(it)) } })
        }
        val id = input.id?.let(::remoteId) ?: UUID.randomUUID().toString()
        body.addProperty("id", id)
        write("/map_records" + if (input.id == null) "" else "/$id", if (input.id == null) "POST" else "PUT", body)
        dao.deleteDraft(draftAccount(), input.id ?: "new")
        readSync()
    } }
    override suspend fun delete(recordId: String) = withContext(Dispatchers.IO) { mutex.withLock {
        require(recordRows.value.any { it.record.id == recordId && it.record.userId == account() }) { "无权删除记录" }
        write("/map_records/${remoteId(recordId)}", "DELETE"); readSync()
    } }
    override suspend fun toggleLike(recordId: String) {
        val liked = recordRows.value.find { it.record.id == recordId }?.liked ?: false
        write(social(recordId) + "/like", if (liked) "DELETE" else "PUT"); loadInteractions(recordId)
    }
    override suspend fun comment(recordId: String, content: String) {
        require(content.trim().length in 1..1000) { "评论需要 1～1000 字" }
        write(social(recordId) + "/comments", "POST", JsonObject().apply { addProperty("content", content.trim()) }); loadInteractions(recordId)
    }
    override suspend fun deleteComment(commentId: String) {
        val row = commentRows.value.find { it.comment.id == commentId && it.comment.userId == account() } ?: error("只能删除自己的评论")
        write("/comments/${remoteId(commentId)}", "DELETE"); loadInteractions(row.comment.recordId)
    }
    override suspend fun createCat(cat: CatProfile) = saveCat(cat, true)
    override suspend fun saveCat(cat: CatProfile) = saveCat(cat, cat.id.isBlank())
    private suspend fun saveCat(cat: CatProfile, create: Boolean) = withContext(Dispatchers.IO) { mutex.withLock {
        require(cat.name.trim().length in 1..80) { "猫咪昵称需要 1～80 字" }
        write("/cats" + if (create) "" else "/${remoteId(cat.id)}", if (create) "POST" else "PUT", Gson().toJsonTree(cat).asJsonObject.apply {
            if (cat.id.isBlank()) addProperty("id", UUID.randomUUID().toString())
            addProperty("avatar", cat.avatar?.let { if (it.startsWith("builtin:")) it else upload(it) }.orEmpty())
        }); readSync()
    } }
    override suspend fun deleteCat(id: String) = withContext(Dispatchers.IO) { mutex.withLock { write("/cats/${remoteId(id)}", "DELETE"); readSync() } }
    override suspend fun saveObservation(input: ObservationInput) = withContext(Dispatchers.IO) { mutex.withLock {
        input.validated()
        val body = rows("records").find { it.text("id") == input.id }?.deepCopy() ?: JsonObject()
        body.addProperty("id", input.id ?: UUID.randomUUID().toString()); body.addProperty("time", Instant.ofEpochMilli(input.observedAt).toString())
        body.addProperty("location", input.place.trim()); body.addProperty("count", input.count); body.addProperty("species", input.catName)
        body.addProperty("gender", input.appearance); body.addProperty("appearance", input.appearance); body.add("cat_id", input.catId?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
        write("/records" + if (input.id == null) "" else "/${remoteId(input.id)}", if (input.id == null) "POST" else "PUT", body); readSync()
    } }
    override suspend fun deleteObservation(id: String) = withContext(Dispatchers.IO) { mutex.withLock { write("/records/${remoteId(id)}", "DELETE"); readSync() } }
    override suspend fun importPhoto(uri: Uri): String = media.import(uri)
    override suspend fun getDraft(recordId: String?): EditorDraft? = dao.draft(draftAccount(), recordId ?: "new")
    override suspend fun saveDraft(input: RecordInput, locationSelected: Boolean) {
        dao.saveDraft(EditorDraft(draftAccount(), input.id ?: "new", input.title, input.description, input.place, input.latitude, input.longitude,
            locationSelected, input.photos, System.currentTimeMillis(), input.catId))
    }
    override suspend fun discardDraft(recordId: String?, photos: List<String>) {
        dao.deleteDraft(draftAccount(), recordId ?: "new"); discardPhotos(photos)
    }
    override suspend fun discardPhotos(paths: List<String>): Unit = withContext(Dispatchers.IO) {
        photoFiles.deleteUnreferenced(paths, dao.referencedPhotoPaths() + dao.allDrafts().flatMap { it.photos })
        Unit
    }
    override suspend fun exportBackup(): ByteArray = withContext(Dispatchers.IO) { mutex.withLock {
        val owner = account()
        // Require a fresh complete snapshot; never export somebody else's public records.
        sync()
        val payload = JsonObject().apply { addProperty("app", "meow-meow-map"); addProperty("version", 1) }
        val photos = mutableMapOf<String, ByteArray>()
        for (kind in listOf("cats", "records", "map_records")) {
            val selected = rows(kind).filter { kind != "map_records" || it.text("user_id") == owner }.map { it.deepCopy() }
            for (row in selected) {
                suspend fun archiveImage(path: String): String {
                    imageUrl(path) // Validate server-relative upload IDs before requesting them.
                    val target = "photos/" + path.substringAfterLast('/')
                    if (target !in photos) {
                        val bytes = api.request(path)
                        require(photos.values.sumOf { it.size } + bytes.size < PortableBackup.MAX_BYTES - 1024*1024) { "图片总量超过备份限制" }
                        photos[target] = bytes
                    }
                    return target
                }
                val replacements = images(row).map { archiveImage(it) }
                if (kind == "cats" && row.text("avatar").startsWith("/uploads/")) {
                    row.addProperty("avatar", archiveImage(row.text("avatar")))
                }
                if (replacements.isNotEmpty()) { row.add("images", Gson().toJsonTree(replacements)); row.addProperty("image", replacements.first()) }
            }
            payload.add(kind, Gson().toJsonTree(selected))
        }
        PortableBackup.encode(payload, photos)
    } }
    override suspend fun importBackup(bytes: ByteArray): Int = withContext(Dispatchers.IO) { mutex.withLock {
        account()
        val (payload, photos) = PortableBackup.decode(bytes)
        require(payload.text("app") == "meow-meow-map" && payload.number("version") == 1.0) { "不支持的喵喵备份格式" }
        // Validate every image before any remote mutation. Desktop paths cannot be opened on Android.
        for (kind in listOf("cats", "records", "map_records")) payload.getAsJsonArray(kind)?.forEach { element ->
            val row = element.asJsonObject
            require(row.text("id").matches(Regex("[\\w-]{1,80}"))) { "备份记录 ID 无效" }
            val avatar = row.text("avatar").takeIf { kind == "cats" && it.isNotBlank() && !it.startsWith("builtin:") }
            (images(row) + listOfNotNull(avatar)).forEach { require(it in photos && photos.getValue(it).size <= 2*1024*1024) {
                "备份缺少照片文件。桌面 JSON 中的本机路径不能直接迁移，请使用包含照片的便携备份。"
            } }
        }
        var count = 0
        val uploaded = mutableMapOf<String, String>()
        try {
            for (kind in listOf("cats", "records", "map_records")) {
                val existing = api.json("/$kind" + if (kind == "map_records") "?mine=1" else "").asJsonArray.map { it.asJsonObject.text("id") }.toMutableSet()
                payload.getAsJsonArray(kind)?.forEach { element ->
                    val row = element.asJsonObject.deepCopy(); val id = row.text("id")
                    if (id !in existing) {
                        row.remove("user_id"); row.remove("profiles"); row.remove("is_mine")
                        suspend fun restoreImage(path: String): String =
                            uploaded[path] ?: api.jsonUpload(photos.getValue(path)).also { uploaded[path] = it }
                        val replacements = images(row).map { restoreImage(it) }
                        if (kind == "cats" && row.text("avatar").startsWith("photos/")) {
                            row.addProperty("avatar", restoreImage(row.text("avatar")))
                        }
                        if (replacements.isNotEmpty()) { row.add("images", Gson().toJsonTree(replacements)); row.addProperty("image", replacements.first()) }
                        write("/$kind", "POST", row); existing.add(id); count++
                    }
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { readSync(); error("已导入 $count 条，随后失败：${e.message}。重试将跳过已有 ID。") }
        readSync(); count
    } }
}
