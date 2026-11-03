package cn.edu.meowmap

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun captureAppScreens() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("地图分享").fetchSemanticsNodes().isNotEmpty() }
        capture("01-map")
        compose.onNodeWithText("地图分享", useUnmergedTree = true).performClick()
        capture("02-records")
        compose.onNodeWithTag("record_list").performScrollToNode(hasText("图书馆旁的橘色身影"))
        compose.onNodeWithText("图书馆旁的橘色身影").performClick()
        capture("03-detail")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("我的", useUnmergedTree = true).performClick()
        capture("04-profile")
    }
}
