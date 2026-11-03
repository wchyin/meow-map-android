package cn.edu.meowmap.data

import java.io.File

/** Only app-owned JPEGs can be removed. References from records AND drafts protect a file. */
class PhotoFiles(private val directory: File) {
    fun deleteUnreferenced(candidates: Collection<String>, referenced: Collection<String>): Int {
        val protected = referenced.mapNotNull { runCatching { File(it).canonicalPath }.getOrNull() }.toSet()
        val root = directory.canonicalFile
        return candidates.distinct().count { path ->
            runCatching {
                val file = File(path).canonicalFile
                file.parentFile == root && file.extension == "jpg" && file.path !in protected && file.isFile && file.delete()
            }.getOrDefault(false)
        }
    }
}
