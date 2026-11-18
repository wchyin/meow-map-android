package cn.edu.meowmap.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Portable archives contain data and image bytes, never desktop absolute paths or credentials. */
object PortableBackup {
    const val MAX_BYTES = 40 * 1024 * 1024
    fun encode(payload: JsonObject, photos: Map<String, ByteArray>): ByteArray {
        val json = payload.toString().toByteArray()
        require(json.size.toLong() + photos.values.sumOf { it.size.toLong() } <= MAX_BYTES) { "备份内容超过 40 MiB，请分批导出" }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("data.json")); zip.write(json); zip.closeEntry()
            photos.forEach { (name, bytes) ->
                require(name.matches(Regex("photos/[a-f0-9-]{36}")))
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
        }
        require(output.size() <= MAX_BYTES) { "备份超过 40 MiB，请减少照片后重试" }
        return output.toByteArray()
    }
    fun decode(bytes: ByteArray): Pair<JsonObject, Map<String, ByteArray>> {
        require(bytes.size <= MAX_BYTES) { "备份超过 40 MiB" }
        if (bytes.firstOrNull()?.toInt() != 0x50) return JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject to emptyMap()
        val entries = mutableMapOf<String, ByteArray>(); var total = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(entry.name == "data.json" || entry.name.matches(Regex("photos/[a-f0-9-]{36}"))) { "备份包含无效文件" }
                require(!entries.containsKey(entry.name)) { "备份包含重复文件" }
                val content = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) {
                    val size = zip.read(buffer); if (size < 0) break
                    total += size; require(total <= MAX_BYTES) { "备份解压后超过 40 MiB" }; content.write(buffer, 0, size)
                }
                entries[entry.name] = content.toByteArray()
            }
        }
        val payload = entries.remove("data.json") ?: error("备份缺少 data.json")
        return JsonParser.parseString(payload.toString(Charsets.UTF_8)).asJsonObject to entries
    }
}
