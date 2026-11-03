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

val Coral = Color(0xFFD56647)
val Ink = Color(0xFF362F2A)
val Muted = Color(0xFF81766D)
val Cream = Color(0xFFFAF7F2)
val Sage = Color(0xFF537867)

@Composable
fun MeowTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Coral, onPrimary = Color.White,
        secondary = Sage, onSecondary = Color.White,
        secondaryContainer = Color(0xFFE4EEE6), onSecondaryContainer = Color(0xFF2C5141),
        background = Cream, onBackground = Ink,
        surface = Color(0xFFFFFCF8), onSurface = Ink,
        surfaceVariant = Color(0xFFF0EAE2), onSurfaceVariant = Muted,
        primaryContainer = Color(0xFFFFE7DC), onPrimaryContainer = Color(0xFF793B29),
        outlineVariant = Color(0xFFE5DCD1),
    ), shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)), content = content)
}

fun displayTime(timestamp: Long): String = DateTimeFormatter.ofPattern("MM月dd日 HH:mm").format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

@Composable
fun CatCover(path: String?, title: String, modifier: Modifier = Modifier) {
    Box(modifier.background(Color(0xFFEFE7DB)), contentAlignment = Alignment.Center) {
        if (path != null) SubcomposeAsyncImage(
            model = File(path), contentDescription = title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
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
