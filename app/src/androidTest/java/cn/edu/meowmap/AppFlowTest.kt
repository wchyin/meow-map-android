package cn.edu.meowmap

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File

class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @After fun captureFinalState() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(dir, "flow-final.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        compose.onRoot().printToLog("MeowFlow")
    }

    @Test fun registerPublishDiscussAndDelete() {
        val title = "校园猫${System.nanoTime()}"
        compose.waitUntil(30_000) { compose.onAllNodesWithText("地图分享").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("我的", useUnmergedTree = true).performClick()
        if (compose.onAllNodesWithText("退出登录").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("退出登录").performClick()
        compose.onNodeWithTag("profile_login").performClick()
        compose.onNodeWithTag("auth_submit").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("邮箱或密码不正确").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("auth_switch").performClick()
        compose.onNodeWithTag("auth_email").performTextInput("test${System.nanoTime()}@example.com")
        compose.onNodeWithTag("auth_nickname").performTextInput("测试观察员")
        compose.onNodeWithTag("auth_password").performTextInput("campus12345")
        compose.onNodeWithTag("auth_submit").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("auth_submit").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("地图分享", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("new_record").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("record_title") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("record_title").performTextInput("草稿$title")
        compose.onNodeWithTag("save_draft").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("new_record").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("观察手记", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("new_observation").performClick()
        compose.onNodeWithTag("observation_place").performTextInput("手记测试地点")
        compose.onNodeWithTag("observation_name").performScrollTo().performTextInput("手记$title")
        compose.onNodeWithTag("observation_save").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("observation_save").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("observation_search").performTextInput("手记测试地点")
        compose.onNodeWithText("手记$title").performClick()
        compose.onNodeWithTag("observation_count").performScrollTo().performTextClearance()
        compose.onNodeWithTag("observation_count").performTextInput("3")
        compose.onNodeWithTag("observation_save").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("3 只").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("删除").performClick()
        compose.onNodeWithTag("confirm_delete_observation").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("手记$title").fetchSemanticsNodes().isEmpty() && compose.onAllNodesWithTag("confirm_delete_observation").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("地图分享", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("new_record").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("已恢复草稿").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("record_title").assertTextContains("草稿$title")
        compose.onNodeWithTag("record_title").performTextClearance()
        compose.onNodeWithTag("record_title").performTextInput(title)
        compose.onNodeWithTag("record_description").performTextInput("今天在图书馆前见到")
        compose.onNodeWithTag("record_place").performScrollTo().performTextInput("测试地点")
        compose.onNodeWithTag("pick_map").performScrollTo().performTouchInput { click(center) }
        // Native map gestures confirm a single tap asynchronously (double-tap detection).
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("已选：", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("save_record").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("save_record").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("record_list").performScrollToNode(hasText(title))
        compose.onNodeWithText(title).performClick()
        compose.onNodeWithTag("comment_input").performTextInput("一条测试评论")
        compose.onNodeWithTag("comment_send").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("一条测试评论").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("删除记录").performClick()
        compose.onNodeWithTag("confirm_delete_record").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("new_record").fetchSemanticsNodes().isNotEmpty() }
    }
}
