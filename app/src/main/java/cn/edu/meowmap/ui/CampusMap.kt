package cn.edu.meowmap.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Canvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Path
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.edu.meowmap.data.CampusArea
import cn.edu.meowmap.data.Coordinates
import cn.edu.meowmap.data.RecordSummary
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.HeatmapLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.roundToInt

/** Positions shared by the editor, location services, and repositories are WGS84. */
data class MapPoint(val latitude: Double, val longitude: Double, val requestId: Long = 0)

// WGS84 equivalent of the initial SCUT University Town view in the desktop app.
val CampusCenter = MapPoint(23.05031752, 113.40189392)

internal fun MapPoint.toAmapLatLng(): LatLng {
    val (lat, lng) = Coordinates.toGcj(latitude, longitude)
    return LatLng(lat, lng)
}

internal fun MapPoint.toAmapGeometry(): Point = toAmapLatLng().let { Point.fromLngLat(it.longitude, it.latitude) }

internal fun LatLng.toWgsMapPoint(): MapPoint {
    val (lat, lng) = Coordinates.toWgs(latitude, longitude)
    return MapPoint(lat, lng)
}

@Composable
fun CampusMap(
    records: List<RecordSummary>,
    modifier: Modifier = Modifier,
    selection: MapPoint? = null,
    focus: MapPoint? = null,
    onPick: ((MapPoint) -> Unit)? = null,
    onRecord: (String) -> Unit = {},
    areas: List<CampusArea> = emptyList(),
    heatmap: Boolean = false,
    clustered: Boolean = true,
    showAreas: Boolean = true,
    track: List<RecordSummary> = emptyList(),
    recordAvatars: Map<String, String?> = emptyMap(),
    loadAvatar: (suspend (String) -> ByteArray?)? = null,
    onCampus: ((CampusArea) -> Unit)? = null,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val customAvatars = remember { mutableMapOf<String, Bitmap>() }
    val currentPick by rememberUpdatedState(onPick)
    val currentRecord by rememberUpdatedState(onRecord)
    val currentAreas by rememberUpdatedState(areas)
    val campusClick by rememberUpdatedState(onCampus.takeIf { showAreas })
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val view = remember { GestureMapView(context).apply { onCreate(null) } }
    val density = LocalDensity.current.density
    val markerSize = (24 * density).roundToInt().coerceAtLeast(1)
    val avatars = remember(context, markerSize, density) {
        avatarPresets.associate { preset ->
            val image = createBitmap(markerSize, markerSize)
            image.density = (density * DisplayMetrics.DENSITY_DEFAULT).roundToInt()
            val canvas = Canvas(image)
            val radius = markerSize / 2f
            canvas.clipPath(Path().apply { addCircle(radius, radius, radius, Path.Direction.CW) })
            ContextCompat.getDrawable(context, preset.resource)?.apply {
                setBounds(0, 0, markerSize, markerSize)
                draw(canvas)
            }
            preset.id to image
        }
    }

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
                // Amap's desktop raster endpoint returns blank tiles above level 18.
                m.setMaxZoomPreference(18.0)
                m.cameraPosition = CameraPosition.Builder()
                    .target((selection ?: focus ?: CampusCenter).toAmapLatLng())
                    .zoom(15.5).build()
                m.setStyle(Style.Builder().fromUri("asset://campus-map.json")) { s ->
                    val boundaries = currentAreas.flatMap { area -> area.rings.map { ring ->
                        val points = ring.map { (lat, lng) -> MapPoint(lat, lng).toAmapGeometry() }
                        Feature.fromGeometry(Polygon.fromLngLats(listOf(points)))
                    } }
                    s.addSource(GeoJsonSource("campuses", FeatureCollection.fromFeatures(boundaries)))
                    s.addLayer(FillLayer("campus-fill", "campuses").withProperties(fillColor("#286C70"), fillOpacity(.12f), fillOutlineColor("#286C70")))
                    s.addSource(GeoJsonSource("track", FeatureCollection.fromFeatures(emptyList<Feature>())))
                    s.addLayer(LineLayer("track-line", "track").withProperties(lineColor("#286C70"), lineWidth(4f), lineOpacity(.9f)))
                    s.addSource(GeoJsonSource("raw-cats", FeatureCollection.fromFeatures(emptyList<Feature>())))
                    s.addLayer(CircleLayer("raw-cat-points", "raw-cats").withProperties(circleRadius(12f), circleColor("#367F83"), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f)))
                    s.addSource(GeoJsonSource("cats", FeatureCollection.fromFeatures(emptyList<Feature>()), GeoJsonOptions().withCluster(true).withClusterRadius(45).withClusterMaxZoom(15)))
                    s.addLayer(CircleLayer("cat-points", "cats").withFilter(not(has("point_count"))).withProperties(circleRadius(12f), circleColor("#367F83"), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f)))
                    s.addLayer(CircleLayer("cat-clusters", "cats").withFilter(has("point_count")).withProperties(circleRadius(18f), circleColor("#286C70"), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f)))
                    s.addSource(GeoJsonSource("heat-data", FeatureCollection.fromFeatures(emptyList<Feature>())))
                    s.addLayer(HeatmapLayer("cat-heat", "heat-data").withProperties(heatmapRadius(32f), heatmapOpacity(.7f)))
                    avatars.forEach { (id, bitmap) -> s.addImage(id, bitmap) }
                    s.addLayer(SymbolLayer("raw-cat-avatars", "raw-cats")
                        .withProperties(iconImage(get("avatar")), iconAllowOverlap(true), iconIgnorePlacement(true)))
                    s.addLayer(SymbolLayer("cat-avatars", "cats").withFilter(not(has("point_count")))
                        .withProperties(iconImage(get("avatar")), iconAllowOverlap(true), iconIgnorePlacement(true)))
                    s.addSource(GeoJsonSource("selection", FeatureCollection.fromFeatures(emptyList<Feature>())))
                    s.addLayer(CircleLayer("selected-point", "selection").withProperties(circleRadius(16f), circleOpacity(0f), circleStrokeColor("#286C70"), circleStrokeWidth(3f)))
                    style = s
                }
                m.addOnMapClickListener { coordinate ->
                    val pick = currentPick
                    if (pick != null) pick(coordinate.toWgsMapPoint())
                    else {
                        val point = m.projection.toScreenLocation(coordinate)
                        val features = m.queryRenderedFeatures(android.graphics.RectF(point.x - 24, point.y - 24, point.x + 24, point.y + 24), "raw-cat-points", "cat-points", "cat-clusters")
                        if (features.isEmpty() && campusClick != null) {
                            val wgs = coordinate.toWgsMapPoint()
                            currentAreas.find { it.contains(wgs.latitude, wgs.longitude) }?.let { campusClick?.invoke(it) }
                        }
                        features.firstOrNull()?.let { feature ->
                            if (feature.hasProperty("point_count")) m.animateCamera(CameraUpdateFactory.newLatLngZoom(coordinate, m.cameraPosition.zoom + 2))
                            else feature.getStringProperty("id")?.let { currentRecord(it) }
                        }
                    }
                    true
                }
            }
        }
    }, modifier = modifier)
    LaunchedEffect(style, records, recordAvatars, loadAvatar) {
        val activeStyle = style ?: return@LaunchedEffect
        val paths = recordAvatars.values.filterNotNull().filter { !it.startsWith("builtin:") }.distinct()
        for (path in paths) {
            if (path !in customAvatars) {
                val bitmap = withContext(Dispatchers.IO) {
                    val original = if (path.startsWith("http")) {
                        loadAvatar?.invoke(path)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    } else BitmapFactory.decodeFile(path)
                    original?.let {
                        val result = createBitmap(markerSize, markerSize)
                        result.density = (density * DisplayMetrics.DENSITY_DEFAULT).roundToInt()
                        val canvas = Canvas(result)
                        val radius = markerSize / 2f
                        canvas.clipPath(Path().apply { addCircle(radius, radius, radius, Path.Direction.CW) })
                        val side = minOf(it.width, it.height)
                        val x = (it.width - side) / 2
                        val y = (it.height - side) / 2
                        canvas.drawBitmap(it, Rect(x, y, x + side, y + side), Rect(0, 0, markerSize, markerSize),
                            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                        it.recycle()
                        result
                    }
                }
                if (bitmap != null) customAvatars[path] = bitmap
            }
        }
        val features = records.map { row ->
            val record = row.record
            val avatar = avatarPreset(record.catId ?: record.id.substringAfter('~'), recordAvatars[record.id])
            Feature.fromGeometry(MapPoint(record.latitude, record.longitude).toAmapGeometry()).apply {
                addStringProperty("id", record.id)
                val custom = recordAvatars[record.id]?.let { customAvatars[it] }
                val imageId = if (custom != null) "custom:" + record.id else avatar.id
                if (custom != null) activeStyle.addImage(imageId, custom)
                addStringProperty("avatar", imageId)
            }
        }
        style?.getSourceAs<GeoJsonSource>("raw-cats")?.setGeoJson(FeatureCollection.fromFeatures(features))
        style?.getSourceAs<GeoJsonSource>("cats")?.setGeoJson(FeatureCollection.fromFeatures(features))
        style?.getSourceAs<GeoJsonSource>("heat-data")?.setGeoJson(FeatureCollection.fromFeatures(features))
    }
    LaunchedEffect(style, heatmap, clustered, showAreas) {
        style?.getLayer("cat-heat")?.setProperties(visibility(if (heatmap) "visible" else "none"))
        val clusters = visibility(if (!heatmap && clustered) "visible" else "none")
        style?.getLayer("cat-points")?.setProperties(clusters)
        style?.getLayer("cat-clusters")?.setProperties(clusters)
        style?.getLayer("cat-avatars")?.setProperties(clusters)
        style?.getLayer("raw-cat-avatars")?.setProperties(visibility(if (!heatmap && !clustered) "visible" else "none"))
        style?.getLayer("raw-cat-points")?.setProperties(visibility(if (!heatmap && !clustered) "visible" else "none"))
        style?.getLayer("campus-fill")?.setProperties(visibility(if (showAreas) "visible" else "none"))
    }
    LaunchedEffect(style, track) {
        val points = track.map { MapPoint(it.record.latitude, it.record.longitude).toAmapGeometry() }
        val features = if (points.size > 1) listOf(Feature.fromGeometry(LineString.fromLngLats(points))) else emptyList()
        style?.getSourceAs<GeoJsonSource>("track")?.setGeoJson(FeatureCollection.fromFeatures(features))
    }
    LaunchedEffect(style, selection) {
        val features = selection?.let { listOf(Feature.fromGeometry(it.toAmapGeometry())) }.orEmpty()
        val layer = style?.getLayer("selected-point")
        layer?.setProperties(circleOpacity(if (onPick == null) 0f else 1f), circleColor("#286C70"))
        style?.getSourceAs<GeoJsonSource>("selection")?.setGeoJson(FeatureCollection.fromFeatures(features))
    }
    LaunchedEffect(map, style, track, focus) {
        val active = map ?: return@LaunchedEffect
        if (style == null) return@LaunchedEffect
        val coordinates = track.map { MapPoint(it.record.latitude, it.record.longitude).toAmapLatLng() }.distinct()
        if (coordinates.size > 1) {
            val bounds = LatLngBounds.Builder().includes(coordinates).build()
            val padding = minOf(view.width, view.height).div(6).coerceAtMost(64)
            active.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
        } else {
            val target = coordinates.firstOrNull() ?: focus?.toAmapLatLng()
            if (target != null) active.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 16.5))
        }
    }
}

/** Keep map drags and multi-touch inside the map until the gesture has finished. */
internal class GestureMapView(context: Context) : MapView(context) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return try {
            super.dispatchTouchEvent(event)
        } finally {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
    }
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
