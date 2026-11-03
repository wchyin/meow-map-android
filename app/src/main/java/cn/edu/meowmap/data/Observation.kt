package cn.edu.meowmap.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Private observation journal, independent of public map records and their interactions. */
@Entity(tableName = "observations", indices = [Index("userId"), Index("observedAt")], foreignKeys = [
    ForeignKey(entity = LocalUser::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)
])
data class Observation(
    @PrimaryKey val id: String,
    val userId: String,
    val observedAt: Long,
    val place: String,
    val count: Int,
    val catName: String,
    val appearance: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class ObservationInput(
    val id: String? = null, val observedAt: Long, val place: String,
    val count: Int, val catName: String, val appearance: String,
) {
    fun validated(now: Long = System.currentTimeMillis()): ObservationInput {
        require(observedAt in 0..now) { "发现时间不能早于 1970 年或晚于现在" }
        require(place.trim().length in 1..80) { "地点需要 1～80 个字符" }
        require(count in 1..999) { "数量需要是 1～999 的整数" }
        require(catName.trim().length in 1..60) { "猫咪名称需要 1～60 个字符" }
        require(appearance.length <= 200) { "毛色和特征最多 200 个字符" }
        return copy(place = place.trim(), catName = catName.trim(), appearance = appearance.trim())
    }
}

fun List<Observation>.searchObservations(query: String): List<Observation> {
    val keyword = query.trim()
    return filter { keyword.isEmpty() || listOf(it.place, it.catName, it.appearance).any { text -> text.contains(keyword, ignoreCase = true) } }
}
