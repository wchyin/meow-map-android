package cn.edu.meowmap

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cn.edu.meowmap.data.*
import cn.edu.meowmap.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger

/** Run on an isolated API 29+ emulator; performs read-only anonymous requests. */
class GuestMapTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun findMap(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findMap(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun style(): Style? {
        var result: Style? = null
        WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::findMap)?.getMapAsync { result = it.style }
        return result
    }
    private fun markerIds(): Set<String> {
        var result = emptySet<String>()
        instrumentation.runOnMainSync {
            style()?.getSourceAs<GeoJsonSource>("raw-cats")?.querySourceFeatures(null)?.let { features ->
                result = features.map { it.getStringProperty("id") }.toSet()
            }
        }
        return result
    }
    private fun hasAvatar(id: String): Boolean {
        var found = false
        instrumentation.runOnMainSync { found = style()?.getImage("custom:$id") != null }
        return found
    }
    private fun screenshot() {
        val directory = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { image ->
            java.io.File(directory, "guest-map.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
    }
    @Test fun slowAvatarDoesNotHidePointsOrRestartOnRecomposition() {
        val gate = CompletableDeferred<ByteArray?>()
        val calls = AtomicInteger()
        val tick = mutableIntStateOf(0)
        val row = RecordSummary(CatRecord("owner~cat", "owner", "cat", "", "", 23.05, 113.40, 1, 1), "", null, 0, 0, 0, false)
        compose.setContent {
            val ignored = tick.intValue
            CampusMap(listOf(row), Modifier.fillMaxSize(), recordAvatars = mapOf(row.record.id to "https://example.test/avatar"),
                loadAvatar = { calls.incrementAndGet(); gate.await() }, showAreas = ignored >= 0)
        }
        compose.waitUntil(30_000) { markerIds() == setOf(row.record.id) }
        assertEquals(1, calls.get())
        repeat(4) { compose.runOnIdle { tick.intValue++ }; compose.waitForIdle() }
        assertEquals(1, calls.get())
        val image = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().apply { image.compress(Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
        image.recycle(); gate.complete(bytes)
        compose.waitUntil(15_000) { hasAvatar(row.record.id) }
        assertEquals(setOf(row.record.id), markerIds())
    }
    @Test fun anonymousServiceRecordsAndUploadedAvatarsReachMap() = runBlocking {
        val context = instrumentation.targetContext
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, MeowDatabase::class.java).build()
        val repo = CloudMeowRepository(context, db, BuildConfig.MEOW_API_URL)
        repo.initialize()
        assertNull(repo.user.first())
        val records = repo.records("").first()
        assertTrue("Public records must load without a session", records.isNotEmpty())
        val metadata = repo.publicObservations.first().mapNotNull { r -> r.mapId?.let { it to r.catAvatar } }.toMap()
        val custom = metadata.filterValues { it?.startsWith("http") == true }
        assertTrue("Live fixture must contain uploaded cat avatars", custom.isNotEmpty())
        for (path in custom.values.filterNotNull().distinct()) assertTrue(repo.catAvatar(path)!!.isNotEmpty())
        compose.setContent { CampusMap(records, Modifier.fillMaxSize(), recordAvatars = metadata, loadAvatar = repo::catAvatar) }
        compose.waitUntil(60_000) { markerIds() == records.map { it.record.id }.toSet() && custom.keys.all(::hasAvatar) }
        screenshot()
        android.util.Log.i("GuestMapTest", "Anonymous markers=${records.size}, uploaded avatar markers=${custom.size}")
        Unit
    }
}
