package cn.edu.meowmap.ui

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import cn.edu.meowmap.data.Coordinates
import com.google.gson.Gson
import java.io.ByteArrayInputStream
import java.time.Instant

/** Uses the desktop chart renderers and statistics functions bundled as local assets. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ObservationAnalytics(state: MeowState) {
    val data = remember(state.statisticsSnapshot, state.user?.id, state.observations, state.allRecords, state.cats) {
        state.statisticsSnapshot ?: localStatisticsSnapshot(state)
    }
    val currentData by rememberUpdatedState(data)
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.textZoom = 100
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = false
                isHorizontalScrollBarEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        if (request.url.toString().startsWith("file:///android_asset/statistics/")) return null
                        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(byteArrayOf()))
                    }
                    override fun onPageFinished(view: WebView, url: String) {
                        view.evaluateJavascript("window.updateStatistics($currentData)", null)
                    }
                }
                loadUrl("file:///android_asset/statistics/html/stats.html")
            }
        },
        update = { view -> view.evaluateJavascript("window.updateStatistics && window.updateStatistics($data)", null) },
        onRelease = { view -> view.stopLoading(); view.destroy() },
    )
}

private fun localStatisticsSnapshot(state: MeowState): String {
    val records = state.observations.map { note ->
        mapOf("id" to note.id, "species" to note.catName, "count" to note.count, "location" to note.place,
            "time" to Instant.ofEpochMilli(note.observedAt).toString(),
            "created_at" to Instant.ofEpochMilli(note.createdAt).toString())
    }
    val maps = state.allRecords.map { row ->
        val record = row.record
        val point = Coordinates.toGcj(record.latitude, record.longitude)
        mapOf("id" to record.id, "user_id" to record.userId, "cat_id" to record.catId,
            "title" to record.title, "area" to record.place, "lat" to point.first, "lng" to point.second,
            "created_at" to Instant.ofEpochMilli(record.createdAt).toString(), "is_demo" to record.demo)
    }
    return Gson().toJson(mapOf("owner" to state.user?.id, "records" to records,
        "map_records" to maps, "cats" to state.cats))
}
