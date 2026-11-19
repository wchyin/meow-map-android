package cn.edu.meowmap.data

import com.google.gson.JsonObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.*

data class CatProfile(val id: String = "", val name: String = "", val species: String = "", val features: String = "", val notes: String = "", val avatar: String? = null)

data class PublicObservation(
    val key: String,
    val kind: String,
    val mapId: String?,
    val label: String,
    val place: String,
    val species: String,
    val author: String,
    val time: Long,
    val demo: Boolean,
    val university: String,
    val catName: String = "",
    val catAvatar: String? = null,
    val description: String = "",
    val count: Int = 1,
    val hasSpecificLocation: Boolean = place.isNotBlank(),
)
data class AssistantSource(val reference: Int, val mapId: String?, val label: String, val location: String)
data class AssistantAnswer(val text: String, val sources: List<AssistantSource>)

internal fun JsonObject.text(key: String, fallback: String = ""): String = get(key)?.takeUnless { it.isJsonNull }?.asString ?: fallback
internal fun JsonObject.number(key: String): Double = text(key, "0").toDoubleOrNull() ?: 0.0
internal fun originalTime(value: String): Long = runCatching { Instant.parse(value).toEpochMilli() }.getOrElse {
    // Desktop datetime-local values have no offset; the original deployment is in China.
    runCatching { LocalDateTime.parse(value).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli() }.getOrDefault(0)
}

/** The desktop protocol and Amap tiles use GCJ-02. Android storage and GPS use WGS84. */
object Coordinates {
    fun toGcj(lat: Double, lng: Double): Pair<Double, Double> {
        if (lng !in 72.004..137.8347 || lat !in 0.8293..55.8271) return lat to lng
        val x = lng - 105; val y = lat - 35
        var a = -100 + 2*x + 3*y + .2*y*y + .1*x*y + .2*sqrt(abs(x))
        a += (20*sin(6*x*PI) + 20*sin(2*x*PI))*2/3
        a += (20*sin(y*PI) + 40*sin(y/3*PI))*2/3
        a += (160*sin(y/12*PI) + 320*sin(y*PI/30))*2/3
        var b = 300 + x + 2*y + .1*x*x + .1*x*y + .1*sqrt(abs(x))
        b += (20*sin(6*x*PI) + 20*sin(2*x*PI))*2/3
        b += (20*sin(x*PI) + 40*sin(x/3*PI))*2/3
        b += (150*sin(x/12*PI) + 300*sin(x/30*PI))*2/3
        val r = lat/180*PI; val magic = 1 - .00669342162296594323*sin(r).pow(2)
        return lat + a*180/(6378245*(1-.00669342162296594323)/(magic*sqrt(magic))*PI) to
            lng + b*180/(6378245/sqrt(magic)*cos(r)*PI)
    }
    fun toWgs(lat: Double, lng: Double): Pair<Double, Double> {
        var a = lat; var b = lng
        repeat(6) { val shifted = toGcj(a, b); a -= shifted.first-lat; b -= shifted.second-lng }
        return a to b
    }
    fun meters(a: Double, b: Double, c: Double, d: Double): Double {
        val p = PI/180
        val h = sin((c-a)*p/2).pow(2) + cos(a*p)*cos(c*p)*sin((d-b)*p/2).pow(2)
        return 6371000*2*asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}

internal fun cloudRecord(row: JsonObject): CatRecord {
    val owner = row.text("user_id"); val id = row.text("id")
    require(owner.matches(Regex("[\\w-]+")) && id.matches(Regex("[\\w-]+"))) { "云端记录 ID 无效" }
    val lat = row.number("lat"); val lng = row.number("lng")
    require(lat.isFinite() && lng.isFinite() && lat in -85.0..85.0 && lng in -180.0..180.0) { "云端坐标无效" }
    val point = if (row.text("coordinateSystem") == "WGS84") lat to lng else Coordinates.toWgs(lat, lng)
    return CatRecord("$owner~$id", owner, row.text("title"), row.text("desc"), row.text("area").ifBlank { "校园观察" },
        point.first, point.second, originalTime(row.text("created_at")), originalTime(row.text("created_at")), demo = row.text("is_demo") in setOf("true", "1"), catId = row.text("cat_id").ifBlank { null })
}
