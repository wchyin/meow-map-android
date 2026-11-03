package cn.edu.meowmap.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.edu.meowmap.data.RecordSummary
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

data class MapPoint(val latitude: Double, val longitude: Double, val requestId: Long = 0)
val CampusCenter = MapPoint(23.0507, 113.4033)

@Composable
fun CampusMap(
    records: List<RecordSummary>,
    modifier: Modifier = Modifier,
    selection: MapPoint? = null,
    focus: MapPoint? = null,
    onPick: ((MapPoint) -> Unit)? = null,
    onRecord: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentPick by rememberUpdatedState(onPick)
    val currentRecord by rememberUpdatedState(onRecord)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val view = remember { MapView(context).apply { onCreate(null) } }

    DisposableEffect(owner, view) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.onStart()
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onResume()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            view.onPause(); view.onStop(); view.onDestroy()
        }
    }
    AndroidView(factory = {
        view.apply {
            getMapAsync { m ->
                map = m
                m.uiSettings.isRotateGesturesEnabled = false
                m.cameraPosition = CameraPosition.Builder().target(LatLng((selection ?: focus ?: CampusCenter).latitude, (selection ?: focus ?: CampusCenter).longitude)).zoom(15.5).build()
                m.setStyle(Style.Builder().fromUri("asset://campus-map.json")) { s ->
                    s.addSource(GeoJsonSource("cats", FeatureCollection.fromFeatures(emptyList<Feature>())))
                    s.addLayer(CircleLayer("cat-points", "cats").withProperties(circleRadius(10f), circleColor("#D56647"), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f)))
                    s.addSource(GeoJsonSource("selection", FeatureCollection.fromFeatures(emptyList<Feature>())))
                    s.addLayer(CircleLayer("selected-point", "selection").withProperties(circleRadius(12f), circleColor("#537867"), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f)))
                    style = s
                }
                m.addOnMapClickListener { coordinate ->
                    val pick = currentPick
                    if (pick != null) pick(MapPoint(coordinate.latitude, coordinate.longitude))
                    else {
                        val point = m.projection.toScreenLocation(coordinate)
                        val features = m.queryRenderedFeatures(android.graphics.RectF(point.x - 24, point.y - 24, point.x + 24, point.y + 24), "cat-points")
                        features.firstOrNull()?.getStringProperty("id")?.let { currentRecord(it) }
                    }
                    true
                }
            }
        }
    }, modifier = modifier)
    LaunchedEffect(style, records) {
        val features = records.map { r -> Feature.fromGeometry(Point.fromLngLat(r.record.longitude, r.record.latitude)).apply { addStringProperty("id", r.record.id) } }
        style?.getSourceAs<GeoJsonSource>("cats")?.setGeoJson(FeatureCollection.fromFeatures(features))
    }
    LaunchedEffect(style, selection) {
        val features = selection?.let { listOf(Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude))) } ?: emptyList()
        style?.getSourceAs<GeoJsonSource>("selection")?.setGeoJson(FeatureCollection.fromFeatures(features))
    }
    LaunchedEffect(map, focus) { focus?.let { map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 16.5)) } }
}

/** One-shot platform location, no Google Play Services dependency. Returns cancellation. */
@SuppressLint("MissingPermission")
fun locate(context: Context, result: (MapPoint?, String?) -> Unit): () -> Unit {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        result(null, "未授权定位，可以直接点击地图选择位置")
        return {}
    }
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val handler = Handler(Looper.getMainLooper())
    var finished = false
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    var listener: LocationListener? = null
    lateinit var timeout: Runnable
    fun complete(point: MapPoint?, message: String?) {
        if (finished) return
        finished = true
        listener?.let { manager.removeUpdates(it) }
        handler.removeCallbacks(timeout)
        result(point, message)
    }
    timeout = Runnable { complete(null, "暂时无法获取位置，请点击地图选点") }
    listener = object : LocationListener {
        override fun onLocationChanged(location: Location) { complete(MapPoint(location.latitude, location.longitude), null) }
        @Deprecated("Deprecated in Java") override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }
    val last = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.filter { System.currentTimeMillis() - it.time < 120_000 }.maxByOrNull { it.time }
    if (last != null) complete(MapPoint(last.latitude, last.longitude), null)
    else if (providers.isEmpty()) complete(null, "请开启系统定位，或直接点击地图选点")
    else {
        handler.postDelayed(timeout, 12_000)
        var started = false
        providers.forEach { provider -> if (runCatching { manager.requestSingleUpdate(provider, listener!!, Looper.getMainLooper()) }.isSuccess) started = true }
        if (!started) complete(null, "无法启动定位，请点击地图选点")
    }
    return { finished = true; listener?.let { manager.removeUpdates(it) }; handler.removeCallbacks(timeout) }
}
