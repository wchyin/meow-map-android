package cn.edu.meowmap.data

import android.content.Context
import com.google.gson.JsonParser

data class CampusArea(val university: String, val campus: String, val rings: List<List<Pair<Double, Double>>>) {
    val label get() = "$university · $campus"
    fun contains(lat: Double, lng: Double): Boolean = rings.any { ring ->
        var inside = false
        if (ring.size >= 3) for (i in ring.indices) {
            val a = ring[i]; val b = ring[(i+1)%ring.size]
            if ((a.second > lng) != (b.second > lng) && lat < (b.first-a.first)*(lng-a.second)/(b.second-a.second)+a.first) inside = !inside
        }
        inside
    }
}

object CampusAreas {
    fun load(context: Context): List<CampusArea> = context.assets.open("areas.json").bufferedReader().use { reader ->
        JsonParser.parseReader(reader).asJsonObject.getAsJsonArray("universities").flatMap { university ->
            val uni = university.asJsonObject
            uni.getAsJsonArray("campuses").map { campus ->
                val c = campus.asJsonObject
                val rings = c.getAsJsonArray("polygons") ?: com.google.gson.JsonArray().apply { c.getAsJsonArray("polygon")?.let { add(it) } }
                CampusArea(uni.text("name"), c.text("name"), rings.map { ring -> ring.asJsonArray.map {
                    val point = it.asJsonArray; point[0].asDouble to point[1].asDouble
                } })
            }
        }
    }
}
