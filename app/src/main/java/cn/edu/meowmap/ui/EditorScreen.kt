package cn.edu.meowmap.ui

import android.Manifest
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.edu.meowmap.data.RecordInput
import cn.edu.meowmap.data.RecordSummary
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(state: MeowState, existing: RecordSummary?, vm: MeowViewModel, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val old = existing?.record
    val initialPhotos = remember(old?.id) { state.photos.filter { it.recordId == old?.id }.map { it.path } }
    var title by rememberSaveable { mutableStateOf(old?.title ?: "") }
    var description by rememberSaveable { mutableStateOf(old?.description ?: "") }
    var place by rememberSaveable { mutableStateOf(old?.place ?: "") }
    var latitude by rememberSaveable { mutableDoubleStateOf(old?.latitude ?: CampusCenter.latitude) }
    var longitude by rememberSaveable { mutableDoubleStateOf(old?.longitude ?: CampusCenter.longitude) }
    var selected by rememberSaveable { mutableStateOf(old != null) }
    var photos by rememberSaveable(stateSaver = listSaver<List<String>, String>(save = { it }, restore = { it.toList() })) { mutableStateOf(initialPhotos) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var focus by remember { mutableStateOf<MapPoint?>(null) }
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var cancelLocation by remember { mutableStateOf<(() -> Unit)?>(null) }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var restoredDraft by rememberSaveable { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(loadAttempt) {
        if (!loaded) {
            loadError = null
            try {
                vm.loadDraft(old?.id)?.let { draft ->
                    title = draft.title; description = draft.description; place = draft.place
                    latitude = draft.latitude; longitude = draft.longitude; selected = draft.locationSelected
                    photos = draft.photos; restoredDraft = true
                    focus = MapPoint(latitude, longitude)
                }
                loaded = true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { loadError = e.message ?: "草稿读取失败" }
        }
    }
    fun input() = RecordInput(old?.id, title, description, place, latitude, longitude, photos.toList())
    val editable = loaded && !state.busy
    val dirty = restoredDraft || title != (old?.title ?: "") || description != (old?.description ?: "") || place != (old?.place ?: "") || photos != initialPhotos || (selected && (old == null || latitude != old.latitude || longitude != old.longitude))
    fun leave() { if (state.busy) vm.notify("正在保存，请稍候") else if (dirty) confirmDiscard = true else onBack() }
    BackHandler { leave() }
    DisposableEffect(Unit) { onDispose { cancelLocation?.invoke() } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
        val remaining = 6 - photos.size
        if (uris.isNotEmpty()) vm.importPhotos(uris.take(remaining)) { photos = photos + it }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = cameraPath
        if (success && path != null) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(path))
            vm.importPhotos(listOf(uri), finished = { File(path).delete(); cameraPath = null }) { paths -> photos = photos + paths }
        } else { path?.let { File(it).delete() }; cameraPath = null }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) {
            cancelLocation?.invoke()
            cancelLocation = locate(context) { point, error ->
                if (point != null) { latitude = point.latitude; longitude = point.longitude; selected = true; focus = point }
                error?.let(vm::notify)
            }
        } else vm.notify("未授权定位，你仍可以点击地图选点")
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(if (old == null) "发布记录" else "编辑记录") }, navigationIcon = { IconButton(onClick = { leave() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } }, actions = {
            TextButton(onClick = { vm.saveDraft(input(), selected, onBack) }, enabled = editable, modifier = Modifier.testTag("save_draft")) { Text("存草稿") }
            TextButton(onClick = {
                if (!selected) vm.notify("请先在地图上选择位置")
                else vm.save(input(), onSaved)
            }, enabled = editable, modifier = Modifier.testTag("save_record")) { Text(if (old == null) "发布" else "保存", fontWeight = FontWeight.Bold) }
        })
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (loadError != null) {
                Text("草稿读取失败：$loadError", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { loadAttempt++ }) { Text("重试读取") }
            } else if (!loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (restoredDraft) Text("已恢复草稿", color = Sage, style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(title, { if (it.length <= 60) title = it }, enabled = editable, label = { Text("标题 *") }, placeholder = { Text("例如：图书馆旁晒太阳的橘猫") }, modifier = Modifier.fillMaxWidth().testTag("record_title"), singleLine = true)
            OutlinedTextField(description, { if (it.length <= 2000) description = it }, enabled = editable, label = { Text("描述") }, placeholder = { Text("写下看到的情况") }, modifier = Modifier.fillMaxWidth().testTag("record_description"), minLines = 3, maxLines = 6)
            Text("猫咪照片 · ${photos.size}/6", fontWeight = FontWeight.SemiBold)
            if (photos.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(photos, key = { it }) { path ->
                    Box {
                        CatCover(path, "已选择的猫咪照片", Modifier.size(100.dp))
                        FilledTonalIconButton(onClick = { photos = photos - path; vm.discardPhotos(listOf(path)) }, enabled = editable, modifier = Modifier.align(Alignment.TopEnd).size(30.dp)) { Icon(Icons.Outlined.Close, "移除照片", Modifier.size(18.dp)) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = editable && photos.size < 6, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("相册") }
                OutlinedButton(onClick = {
                    val directory = File(context.cacheDir, "camera").apply { mkdirs() }
                    val file = File(directory, "${UUID.randomUUID()}.jpg")
                    cameraPath = file.path
                    runCatching { camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file)) }
                        .onFailure { vm.notify("没有可用的相机应用，请从相册选择照片"); cameraPath = null }
                }, enabled = editable && photos.size < 6, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("拍照") }
            }
            OutlinedTextField(place, { if (it.length <= 80) place = it }, enabled = editable, label = { Text("地点名称 *") }, placeholder = { Text("例如：图书馆东门花坛") }, modifier = Modifier.fillMaxWidth().testTag("record_place"), singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("地图位置 *", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }, enabled = editable) { Icon(Icons.Outlined.MyLocation, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("当前位置") }
            }
            CampusMap(emptyList(), Modifier.fillMaxWidth().height(240.dp).testTag("pick_map"), selection = if (selected) MapPoint(latitude, longitude) else null, focus = focus,
                onPick = { if (editable) { latitude = it.latitude; longitude = it.longitude; selected = true } })
            Text(if (selected) "已选：%.5f, %.5f".format(latitude, longitude) else "点击地图选点", color = if (selected) Sage else Muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(18.dp))
        }
    }
    if (confirmDiscard) AlertDialog(onDismissRequest = { if (!state.busy) confirmDiscard = false }, title = { Text("保存这次编辑？") }, text = { Text("放弃修改会清除这条草稿。") }, confirmButton = {
        TextButton(onClick = { vm.saveDraft(input(), selected, onBack) }, enabled = editable) { Text("存草稿并离开") }
    }, dismissButton = {
        Row {
            TextButton(onClick = { vm.discardDraft(old?.id, photos, onBack) }, enabled = editable) { Text("放弃修改") }
            TextButton(onClick = { confirmDiscard = false }, enabled = !state.busy) { Text("继续编辑") }
        }
    })
}
