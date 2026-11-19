package cn.edu.meowmap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val Coral = Color(0xFF367F83)
val Ink = Color(0xFF304B55)
val Muted = Color(0xFF657D86)
val Cream = Color(0xFFF4F8FA)
val Sage = Color(0xFF286C70)

@Composable
fun MeowTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Coral, onPrimary = Color.White,
        secondary = Sage, onSecondary = Color.White,
        secondaryContainer = Color(0xFFEAF6F4), onSecondaryContainer = Color(0xFF286C70),
        background = Cream, onBackground = Ink,
        surface = Color(0xFFFFFFFF), onSurface = Ink,
        surfaceVariant = Color(0xFFEDF3F5), onSurfaceVariant = Muted,
        primaryContainer = Color(0xFFD5EEEB), onPrimaryContainer = Color(0xFF286469),
        outlineVariant = Color(0xFFDCE7EB),
    ), shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp)), content = content)
}

fun displayTime(timestamp: Long): String = DateTimeFormatter.ofPattern("MM月dd日 HH:mm").format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

@Composable
fun CatCover(path: String?, title: String, modifier: Modifier = Modifier) {
    Box(modifier.background(Color(0xFFEAF6F4)), contentAlignment = Alignment.Center) {
        if (path != null) SubcomposeAsyncImage(
            model = if (path.startsWith("http://") || path.startsWith("https://")) path else File(path), contentDescription = title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) } },
            error = { CoverPlaceholder("照片暂不可用") },
        ) else CoverPlaceholder("校园偶遇")
    }
}

@Composable
private fun CoverPlaceholder(label: String) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val showLabel = maxHeight >= 80.dp && maxWidth >= 80.dp
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.Pets, null, tint = Coral.copy(alpha = .65f), modifier = Modifier.size(if (showLabel) 36.dp else 28.dp))
            if (showLabel) {
                Spacer(Modifier.height(6.dp))
                Text(label, color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
fun EmptyState(title: String, hint: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.Pets, null, Modifier.size(48.dp), tint = Coral.copy(alpha = .5f))
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(hint, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}
