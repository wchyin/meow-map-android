package cn.edu.meowmap

import android.net.Uri
import androidx.lifecycle.ViewModelStore
import cn.edu.meowmap.data.*
import cn.edu.meowmap.ui.MeowViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    val dispatcher = StandardTestDispatcher()
    override fun starting(description: Description) { Dispatchers.setMain(dispatcher) }
    override fun finished(description: Description) { Dispatchers.resetMain() }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MeowViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    @After fun close() { store.clear() }
    private fun TestScope.viewModel(repo: FakeRepository): MeowViewModel {
        val vm = MeowViewModel(repo)
        store.put("test", vm)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
        advanceUntilIdle()
        return vm
    }

    @Test fun initializationFailureIsVisibleAndRetryRecovers() = runTest(main.dispatcher) {
        val repo = FakeRepository().apply { initializationFailures = 1 }
        val vm = viewModel(repo)
        assertFalse(vm.state.value.ready)
        assertEquals("初始化失败", vm.state.value.error)
        vm.retryInitialize(); advanceUntilIdle()
        assertTrue(vm.state.value.ready); assertNull(vm.state.value.error)
    }
    @Test fun retryAlsoRestartsFailedDatabaseObservers() = runTest(main.dispatcher) {
        val repo = FakeRepository().apply { readFails = true }
        val vm = viewModel(repo)
        assertEquals("读取失败", vm.state.value.error)
        repo.readFails = false
        vm.retryInitialize(); advanceUntilIdle()
        assertTrue(vm.state.value.ready); assertNull(vm.state.value.error)
    }
    @Test fun rapidRepeatedActionsOnlyRunOnce() = runTest(main.dispatcher) {
        val repo = FakeRepository().apply { loginGate = CompletableDeferred() }
        val vm = viewModel(repo)
        var callbacks = 0
        vm.auth(false, "a@example.com", "", "password") { callbacks++ }
        vm.auth(false, "a@example.com", "", "password") { callbacks++ }
        advanceUntilIdle()
        assertEquals(1, repo.loginCalls); assertTrue(vm.state.value.busy)
        repo.loginGate!!.complete(Unit); advanceUntilIdle()
        assertEquals(1, callbacks); assertFalse(vm.state.value.busy)
    }
    @Test fun failedOperationReleasesBusyAndReportsError() = runTest(main.dispatcher) {
        val repo = FakeRepository().apply { loginFails = true }
        val vm = viewModel(repo)
        vm.auth(false, "a@example.com", "", "password") {}
        advanceUntilIdle()
        assertFalse(vm.state.value.busy)
        assertEquals("登录失败", vm.messages.first())
        repo.loginFails = false
        vm.auth(false, "a@example.com", "", "password") {}
        advanceUntilIdle()
        assertEquals(2, repo.loginCalls); assertFalse(vm.state.value.busy)
    }
    @Test fun filtersCanBeClearedTogether() = runTest(main.dispatcher) {
        val vm = viewModel(FakeRepository())
        vm.query("图书馆"); vm.mine(true); advanceUntilIdle()
        assertTrue(vm.state.value.filters.active)
        vm.clearFilters(); advanceUntilIdle()
        assertFalse(vm.state.value.filters.active)
        assertEquals("", vm.state.value.filters.query)
    }
    @Test fun savingDraftDoesNotPublishAndCallsCompletion() = runTest(main.dispatcher) {
        val repo = FakeRepository()
        val vm = viewModel(repo)
        val input = RecordInput(title = "写到一半", description = "", place = "", latitude = 23.0, longitude = 113.0, photos = emptyList())
        var complete = false
        vm.saveDraft(input, false) { complete = true }; advanceUntilIdle()
        assertTrue(complete); assertEquals(0, repo.publishCalls)
        assertEquals("写到一半", vm.loadDraft(null)?.title)
        assertFalse(vm.loadDraft(null)!!.locationSelected)
    }
    @Test fun switchingAccountNeverExposesPreviousAccountsObservationRows() = runTest(main.dispatcher) {
        val repo = FakeRepository()
        val alice = LocalUser("alice", "alice@example.com", "Alice", "", "")
        repo.user.value = alice
        // Simulate a delayed source that still emits the previous account's rows.
        repo.noteRows.value = listOf(Observation("private-note", "alice", 1, "图书馆", 1, "小橘", "", 1, 1))
        val vm = viewModel(repo)
        assertEquals(1, vm.state.value.observations.size)
        repo.user.value = alice.copy(id = "bob", email = "bob@example.com")
        advanceUntilIdle()
        assertTrue(vm.state.value.observations.isEmpty())
        repo.user.value = null
        advanceUntilIdle()
        assertTrue(vm.state.value.observations.isEmpty())
    }
}

private class FakeRepository : MeowRepository {
    override val user = MutableStateFlow<LocalUser?>(null)
    override val photos: Flow<List<RecordPhoto>> = flowOf(emptyList())
    override val comments: Flow<List<CommentSummary>> = flowOf(emptyList())
    val noteRows = MutableStateFlow<List<Observation>>(emptyList())
    override fun observations(userId: String): Flow<List<Observation>> = noteRows
    override suspend fun saveObservation(input: ObservationInput) = Unit
    override suspend fun deleteObservation(id: String) = Unit
    var initializationFailures = 0
    var readFails = false
    var loginFails = false
    var loginGate: CompletableDeferred<Unit>? = null
    var loginCalls = 0
    var publishCalls = 0
    var draft: EditorDraft? = null
    override fun records(userId: String): Flow<List<RecordSummary>> = flow { check(!readFails) { "读取失败" }; emit(emptyList()) }
    override suspend fun initialize() { if (initializationFailures-- > 0) error("初始化失败") }
    override suspend fun login(email: String, password: String) { loginCalls++; loginGate?.await(); check(!loginFails) { "登录失败" } }
    override suspend fun register(email: String, nickname: String, password: String) = Unit
    override fun logout() = Unit
    override suspend fun rename(name: String) = Unit
    override suspend fun save(input: RecordInput) { publishCalls++ }
    override suspend fun delete(recordId: String) = Unit
    override suspend fun toggleLike(recordId: String) = Unit
    override suspend fun comment(recordId: String, content: String) = Unit
    override suspend fun deleteComment(commentId: String) = Unit
    override suspend fun importPhoto(uri: Uri): String = error("Not used by these tests")
    override suspend fun getDraft(recordId: String?) = draft
    override suspend fun saveDraft(input: RecordInput, locationSelected: Boolean) {
        draft = EditorDraft("test", input.id ?: "new", input.title, input.description, input.place, input.latitude, input.longitude, locationSelected, input.photos, 1)
    }
    override suspend fun discardDraft(recordId: String?, photos: List<String>) { draft = null }
    override suspend fun discardPhotos(paths: List<String>) = Unit
}
