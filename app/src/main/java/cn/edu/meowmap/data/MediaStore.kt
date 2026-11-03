package cn.edu.meowmap.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Copy selected images into private storage so they survive picker grants and restarts. */
class MediaStore(private val context: Context) {
    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "photos").apply { mkdirs() }
        val original = File(directory, "${UUID.randomUUID()}.upload")
        val output = File(directory, "${UUID.randomUUID()}.jpg")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取照片" }
                original.outputStream().use { dest ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 25L * 1024 * 1024) { "原图不能超过 25 MB" }
                        dest.write(buffer, 0, count)
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(original.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "照片格式无法读取" }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
            val bitmap = requireNotNull(BitmapFactory.decodeFile(original.path, BitmapFactory.Options().apply { inSampleSize = sample })) { "照片读取失败" }
            val exif = runCatching { ExifInterface(original) }.getOrNull()
            val matrix = Matrix().apply {
                postRotate(exif?.rotationDegrees?.toFloat() ?: 0f)
                if (exif?.isFlipped == true) postScale(-1f, 1f)
            }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            try { output.outputStream().use { check(rotated.compress(Bitmap.CompressFormat.JPEG, 85, it)) { "照片保存失败" } } }
            finally { if (rotated !== bitmap) rotated.recycle(); bitmap.recycle() }
            output.path
        } catch (e: Exception) { output.delete(); throw e }
        finally { original.delete() }
    }
}
