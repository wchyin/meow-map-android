package cn.edu.meowmap.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal data class AvatarCropRect(val left: Int, val top: Int, val side: Int)

internal fun avatarCropRect(width: Int, height: Int, zoom: Float, x: Float, y: Float): AvatarCropRect {
    require(width > 0 && height > 0)
    val side = (minOf(width, height) / zoom.coerceIn(1f, 4f)).roundToInt().coerceIn(1, minOf(width, height))
    return AvatarCropRect(((width - side) * x.coerceIn(0f, 1f)).roundToInt(),
        ((height - side) * y.coerceIn(0f, 1f)).roundToInt(), side)
}

@Composable
internal fun AvatarCropDialog(path: String, busy: Boolean, onCancel: () -> Unit,
    onConfirm: (Float, Float, Float) -> Unit) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) }
    }
    var zoom by rememberSaveable(path) { mutableFloatStateOf(1f) }
    var x by rememberSaveable(path) { mutableFloatStateOf(.5f) }
    var y by rememberSaveable(path) { mutableFloatStateOf(.5f) }
    AlertDialog(onDismissRequest = { if (!busy) onCancel() }, title = { Text("裁剪猫咪头像") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("调整大小和位置，让猫咪出现在圆框中央。")
                bitmap?.let { image ->
                    val rect = avatarCropRect(image.width, image.height, zoom, x, y)
                    Canvas(Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape)) {
                        drawImage(image.asImageBitmap(), srcOffset = IntOffset(rect.left, rect.top),
                            srcSize = IntSize(rect.side, rect.side),
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
                    }
                } ?: Text("正在读取图片…")
                Text("缩放")
                Slider(zoom, { zoom = it }, valueRange = 1f..4f, enabled = !busy)
                Text("左右位置")
                Slider(x, { x = it }, enabled = !busy)
                Text("上下位置")
                Slider(y, { y = it }, enabled = !busy)
            }
        },
        confirmButton = { TextButton(enabled = !busy && bitmap != null,
            onClick = { onConfirm(zoom, x, y) }) { Text("使用此头像") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onCancel) { Text("取消") } })
}
