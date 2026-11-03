package cn.edu.meowmap.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cn.edu.meowmap.data.RecordSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(state: MeowState, entry: RecordSummary, vm: MeowViewModel, onBack: () -> Unit, onEdit: () -> Unit, onAuth: () -> Unit, onDeleted: () -> Unit) {
    val record = entry.record
    val photos = state.photos.filter { it.recordId == record.id }
    val comments = state.comments.filter { it.comment.recordId == record.id }
    var text by rememberSaveable(record.id) { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<String?>(null) }
    var map by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var scrollAfterComment by remember { mutableStateOf<Int?>(null) }
    val cover = entry.cover
    val photoRows = (if (cover != null) 1 else 0) + (if (photos.size > 1) 1 else 0)
    LaunchedEffect(comments.size, scrollAfterComment, photoRows) {
        val expected = scrollAfterComment
        if (expected != null && comments.size >= expected) {
            listState.animateScrollToItem(photoRows + comments.size)
            scrollAfterComment = null
        }
    }
    val mine = state.user?.id == record.userId
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("记录详情") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } }, actions = {
            if (mine) {
                IconButton(onClick = onEdit, enabled = !state.busy, modifier = Modifier.testTag("edit_record")) { Icon(Icons.Outlined.Edit, "编辑记录") }
                IconButton(onClick = { confirmDelete = true }, enabled = !state.busy) { Icon(Icons.Outlined.DeleteOutline, "删除记录") }
            }
        })
        LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (cover != null) item {
                CatCover(cover, record.title, Modifier.fillMaxWidth().height(220.dp).clickable { preview = cover })
            }
            if (photos.size > 1) item { LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(photos, key = { it.id }) { photo -> CatCover(photo.path, record.title, Modifier.size(80.dp).clickable { preview = photo.path }) } } }
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (record.demo) Text("示例记录", color = Sage, style = MaterialTheme.typography.labelMedium)
                    Text(record.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${entry.author}  ·  ${displayTime(record.createdAt)}", color = Muted, style = MaterialTheme.typography.bodySmall)
                    if (record.description.isNotBlank()) Text(record.description, style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = { map = !map }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Place, null); Spacer(Modifier.width(6.dp)); Text(record.place) }
                    if (map) {
                        CampusMap(listOf(entry), Modifier.fillMaxWidth().height(220.dp), focus = MapPoint(record.latitude, record.longitude))
                        Text("坐标：%.5f, %.5f".format(record.latitude, record.longitude), color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(onClick = { if (state.user == null) onAuth() else vm.like(record.id) }, enabled = !state.busy, modifier = Modifier.testTag("like_record")) {
                            Icon(if (entry.liked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, null)
                            Spacer(Modifier.width(6.dp)); Text("${if (entry.liked) "已赞" else "点赞"} · ${entry.likeCount}")
                        }
                        Text("${comments.size} 条评论", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider()
                    Text("评论", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            if (comments.isEmpty()) item { Text("暂无评论", Modifier.padding(horizontal = 20.dp, vertical = 10.dp), color = Muted) }
            items(comments, key = { it.comment.id }) { c ->
                Surface(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(c.author, fontWeight = FontWeight.SemiBold); Text(displayTime(c.comment.createdAt), color = Muted, style = MaterialTheme.typography.labelSmall) }
                            if (c.comment.userId == state.user?.id) TextButton(onClick = { vm.deleteComment(c.comment.id) }, enabled = !state.busy) { Text("删除") }
                        }
                        Text(c.comment.content)
                    }
                }
            }
        }
        Surface(shadowElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().imePadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { if (it.length <= 500) text = it }, modifier = Modifier.weight(1f).testTag("comment_input"), placeholder = { Text("写评论") }, maxLines = 3)
                Button(onClick = {
                    if (state.user == null) onAuth()
                    else {
                        val expected = comments.size + 1
                        vm.comment(record.id, text) {
                            text = ""
                            focusManager.clearFocus()
                            keyboard?.hide()
                            scrollAfterComment = expected
                        }
                    }
                }, enabled = text.isNotBlank() && !state.busy, modifier = Modifier.testTag("comment_send")) { Text("发送") }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("删除这条记录？") }, text = { Text("关联的点赞和评论也会一并删除。") }, confirmButton = { TextButton(onClick = { vm.delete(record.id, onDeleted); confirmDelete = false }, enabled = !state.busy, modifier = Modifier.testTag("confirm_delete_record")) { Text("删除") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } })
    if (preview != null) Dialog(onDismissRequest = { preview = null }) { Card { Column { CatCover(preview, record.title, Modifier.fillMaxWidth().height(400.dp)); TextButton(onClick = { preview = null }) { Text("关闭图片") } } } }
}
