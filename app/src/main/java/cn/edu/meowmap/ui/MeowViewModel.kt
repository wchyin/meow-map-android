package cn.edu.meowmap.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cn.edu.meowmap.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class MeowState(
    val ready: Boolean = false,
    val user: LocalUser? = null,
    val allRecords: List<RecordSummary> = emptyList(),
    val records: List<RecordSummary> = emptyList(),
    val photos: List<RecordPhoto> = emptyList(),
    val comments: List<CommentSummary> = emptyList(),
    val observations: List<Observation> = emptyList(),
    val filters: Filters = Filters(),
    val busy: Boolean = false,
    val error: String? = null,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MeowViewModel(private val repository: MeowRepository) : ViewModel() {
    private val ready = MutableStateFlow(false)
    private val busy = MutableStateFlow(false)
    private val filters = MutableStateFlow(Filters())
    private val startupError = MutableStateFlow<String?>(null)
    private val attempt = MutableStateFlow(0)
    private var initialization: Job? = null
    private val events = Channel<String>(Channel.BUFFERED)
    val messages = events.receiveAsFlow()
    private fun <T> Flow<T>.recover(fallback: T): Flow<T> = catch { error ->
        startupError.value = error.message ?: "本机数据库读取失败"
        emit(fallback)
    }
    private val user = attempt.flatMapLatest { repository.user.recover(null) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val records = combine(user, attempt) { u, _ -> u?.id ?: "" }.flatMapLatest { repository.records(it).recover(emptyList()) }
    private val photos = attempt.flatMapLatest { repository.photos.recover(emptyList()) }
    private val comments = attempt.flatMapLatest { repository.comments.recover(emptyList()) }
    private val observations = combine(user, attempt) { u, _ -> u?.id ?: "" }
        .flatMapLatest { repository.observations(it).recover(emptyList()) }
    val state = combine(user, records, photos, comments, filters) { u, rs, ps, cs, fs ->
        MeowState(user = u, allRecords = rs, records = rs.filtered(fs, u?.id), photos = ps, comments = cs, filters = fs)
    }.combine(observations) { state, notes -> state.copy(observations = notes.filter { it.userId == state.user?.id }) }
        .combine(combine(ready, busy, startupError) { r, b, e -> Triple(r, b, e) }) { state, (r, b, e) -> state.copy(ready = r, busy = b, error = e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeowState())

    init { retryInitialize() }
    fun retryInitialize() {
        if (initialization?.isActive == true) return
        startupError.value = null
        ready.value = false
        attempt.value++
        initialization = viewModelScope.launch {
            try { repository.initialize(); ready.value = true }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { startupError.value = e.message ?: "本机数据库初始化失败" }
        }
    }
    fun query(query: String) { filters.value = filters.value.copy(query = query) }
    fun mine(mine: Boolean) { filters.value = filters.value.copy(mine = mine) }
    fun clearFilters() { filters.value = Filters() }
    fun dates(start: LocalDate?, endInclusive: LocalDate?) {
        runCatching { filters.value.withDates(start, endInclusive) }
            .onSuccess { filters.value = it }.onFailure { notify(it.message ?: "日期范围无效") }
    }
    fun notify(message: String) { viewModelScope.launch { events.send(message) } }
    private fun action(success: String? = null, done: () -> Unit = {}, onError: ((String) -> Unit)? = null, block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { block(); success?.let { events.send(it) }; done() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val message = e.message ?: "操作失败，请重试"
                if (onError == null) events.send(message) else onError(message)
            }
            finally { busy.value = false }
        }
    }
    fun auth(register: Boolean, email: String, name: String, password: String, onError: ((String) -> Unit)? = null, done: () -> Unit) = action("登录成功", done, onError) {
        if (register) repository.register(email, name, password) else repository.login(email, password)
    }
    fun logout() { repository.logout(); filters.value = filters.value.copy(mine = false) }
    fun rename(name: String, done: () -> Unit) = action("昵称已更新", done) { repository.rename(name) }
    fun save(input: RecordInput, done: () -> Unit) = action("记录已保存到本机", done) { repository.save(input) }
    fun delete(id: String, done: () -> Unit) = action("记录已删除", done) { repository.delete(id) }
    fun like(id: String) = action { repository.toggleLike(id) }
    fun comment(id: String, text: String, done: () -> Unit) = action(done = done) { repository.comment(id, text) }
    fun deleteComment(id: String) = action { repository.deleteComment(id) }
    fun saveObservation(input: ObservationInput, done: () -> Unit) = action("观察手记已保存", done) { repository.saveObservation(input) }
    fun deleteObservation(id: String, done: () -> Unit) = action("观察手记已删除", done) { repository.deleteObservation(id) }
    suspend fun loadDraft(recordId: String?) = repository.getDraft(recordId)
    fun saveDraft(input: RecordInput, selected: Boolean, done: () -> Unit = {}) = action("草稿已保存，可在下次打开时继续", done) { repository.saveDraft(input, selected) }
    fun discardDraft(recordId: String?, paths: List<String>, done: () -> Unit) = action(done = done) { repository.discardDraft(recordId, paths) }
    fun discardPhotos(paths: List<String>) = action { repository.discardPhotos(paths) }
    fun importPhotos(uris: List<Uri>, finished: () -> Unit = {}, done: (List<String>) -> Unit) = action {
        val paths = mutableListOf<String>()
        try {
            for (uri in uris) paths += repository.importPhoto(uri)
            done(paths)
        } catch (e: Exception) {
            withContext(NonCancellable) { runCatching { repository.discardPhotos(paths) } }
            throw e
        } finally { finished() }
    }

    companion object {
        fun factory(repository: MeowRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MeowViewModel(repository) as T
        }
    }
}
