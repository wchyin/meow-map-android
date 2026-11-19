package cn.edu.meowmap.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import cn.edu.meowmap.R
import coil.compose.AsyncImage
import java.io.File
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.edu.meowmap.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal data class AvatarPreset(val id: String, val label: String, val resource: Int)
internal val avatarPresets = listOf(
    AvatarPreset("builtin:cat", "浅杏", R.drawable.cat_avatar_apricot),
    AvatarPreset("builtin:cat2", "雾蓝", R.drawable.cat_avatar_blue),
    AvatarPreset("builtin:mint", "薄荷", R.drawable.cat_avatar_mint),
    AvatarPreset("builtin:lilac", "丁香", R.drawable.cat_avatar_lilac),
    AvatarPreset("builtin:sand", "暖沙", R.drawable.cat_avatar_sand),
    AvatarPreset("builtin:rose", "浅玫", R.drawable.cat_avatar_rose),
)

internal fun avatarPreset(key: String, preferred: String? = null): AvatarPreset =
    avatarPresets.find { it.id == preferred }
        ?: avatarPresets[Integer.remainderUnsigned(key.hashCode(), avatarPresets.size)]

@Composable
internal fun CatAvatar(path: String?, name: String, vm: MeowViewModel) {
    val preset = avatarPresets.find { it.id == path }
    val bytes by produceState<ByteArray?>(null, path) {
        value = null
        if (path?.startsWith("http") == true) value = vm.catAvatar(path)
    }
    val modifier = Modifier.size(44.dp).clip(CircleShape)
    val model = bytes ?: path?.takeIf { !it.startsWith("http") && !it.startsWith("builtin:") }?.let(::File)
    when {
        preset != null -> Image(painterResource(preset.resource), null, modifier)
        model != null -> AsyncImage(model, null, modifier, contentScale = ContentScale.Crop)
        else -> Surface(modifier, color = MaterialTheme.colorScheme.secondaryContainer) {
            Box(contentAlignment = Alignment.Center) { Text(name.take(1).ifBlank { "猫" }) }
        }
    }
}

@Composable
fun CatPicker(cats: List<CatProfile>, selected: String?, enabled: Boolean, vm: MeowViewModel, onProfileSelected: (CatProfile) -> Unit = {}, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var offset by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<CatProfile>>(emptyList()) }
    var next by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var chosen by remember { mutableStateOf<CatProfile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (enabled) saving = false }
    LaunchedEffect(expanded, query, offset, retry) {
        if (!expanded || query.isBlank() || cn.edu.meowmap.BuildConfig.MEOW_LOCAL_MODE) return@LaunchedEffect
        loading = true; error = null
        try {
            delay(300)
            val page = vm.searchCats(query, offset)
            results = (if (offset == 0) page.items else results + page.items).distinctBy { it.id }
            next = page.nextOffset
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "搜索失败，请重试" }
        finally { loading = false }
    }
    if (creating) AlertDialog(onDismissRequest = { if (!saving) creating = false }, title = { Text("新建猫咪档案") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("确认是未建档的猫咪后再创建。昵称相同不代表同一只猫。")
            OutlinedTextField(name, { name = it.take(80) }, label = { Text("猫咪昵称") }, singleLine = true, enabled = !saving)
        } }, confirmButton = { TextButton(enabled = !saving && name.isNotBlank(), onClick = {
            saving = true
            vm.createCat(name.trim()) { id -> chosen = CatProfile(id = id, name = name.trim()); onProfileSelected(requireNotNull(chosen)); onSelect(id); creating = false; expanded = false }
        }) { Text("创建并选中") } },
        dismissButton = { TextButton(enabled = !saving, onClick = { creating = false }) { Text("取消") } })
    val current = cats.find { it.id == selected } ?: chosen?.takeIf { it.id == selected }
    OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text("关联猫咪：" + (current?.name ?: selected?.let { "ID ${it.takeLast(8)}" } ?: "搜索名字或 ID"))
    }
    if (expanded && !creating) AlertDialog(onDismissRequest = { expanded = false }, title = { Text("选择已有猫咪") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("先按名字或完整 ID 搜索。保存观察后，这只猫会加入你的猫咪档案。")
            OutlinedTextField(query, {
                query = it.take(170); offset = 0; results = emptyList(); next = null; error = null
            }, label = { Text("猫咪名字或 ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { retry++ }) { Text("重试") } }
            val visible = if (query.isBlank() || cn.edu.meowmap.BuildConfig.MEOW_LOCAL_MODE)
                cats.filter { query.isBlank() || it.name.contains(query, true) || it.id.contains(query, true) } else results
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(visible, key = { it.id }) { cat ->
                    Column(Modifier.fillMaxWidth().clickable(enabled = enabled) {
                        chosen = cat; onProfileSelected(cat); onSelect(cat.id); expanded = false
                    }.padding(vertical = 10.dp)) {
                        Text(cat.name, style = MaterialTheme.typography.titleSmall)
                        Text("ID：${cat.id}", style = MaterialTheme.typography.bodySmall)
                        val details = listOf(cat.species, cat.features).filter(String::isNotBlank).joinToString(" · ")
                        if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (!loading && error == null && query.isNotBlank() && visible.isEmpty()) Text("没有找到匹配的猫咪，可换名字或 ID 再搜索。")
            if (next != null) TextButton(enabled = !loading, onClick = { offset = next!! }) { Text("加载更多") }
            if (!cn.edu.meowmap.BuildConfig.MEOW_LOCAL_MODE && query.isNotBlank() && !loading && error == null)
                TextButton(onClick = { name = query.take(80); creating = true }) { Text("确认不是已有猫咪，新建档案") }
        }
    }, confirmButton = { TextButton(onClick = { expanded = false }) { Text("完成") } },
        dismissButton = { TextButton(onClick = { onSelect(null); chosen = null; expanded = false }) { Text("暂不关联") } })
}

@Composable
fun CatsScreen(state: MeowState, vm: MeowViewModel, onLogin: () -> Unit, onRecord: (String) -> Unit, requestedCatId: String? = null, onRequestHandled: () -> Unit = {}) {
    var query by rememberSaveable { mutableStateOf("") }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(requestedCatId) {
        if (requestedCatId != null) {
            detailId = requestedCatId
            onRequestHandled()
        }
    }
    var editing by remember { mutableStateOf<CatProfile?>(null) }
    var deleting by remember { mutableStateOf<CatProfile?>(null) }
    val selected = state.cats.find { it.id == detailId }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(8.dp))
        SectionTitle("猫咪档案", "给每次相遇一个熟悉的名字")
        if (state.user == null) {
            EmptyState("登录后建立猫咪档案", "登录后记录猫咪，已记录的猫咪会收录到你的档案列表")
            Button(onClick = onLogin) { Text("登录 / 注册") }
        } else {
            Text("新增观察时可搜索全站猫咪；记录后自动收录。基础资料由创建者维护。", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(query, { query = it }, placeholder = { Text("搜索昵称、ID、品种、特征或备注") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val filtered = state.cats.filter { cat -> listOf(cat.name, cat.id, cat.species, cat.features, cat.notes).any { it.contains(query, true) } }
                if (filtered.isEmpty()) item { EmptyState("还没有匹配的猫咪", "新增观察时搜索并选择已有猫咪，保存后会出现在这里") }
                items(filtered, key = { it.id }) { cat ->
                    OutlinedCard(Modifier.fillMaxWidth().clickable { detailId = cat.id }) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CatAvatar(cat.avatar, cat.name, vm)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(cat.name, style = MaterialTheme.typography.titleMedium)
                                Text(listOf(cat.species, cat.features).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "查看相遇时间线" }, color = Muted)
                            }
                        }
                    }
                }
            }
        }
    }
    if (selected != null) AlertDialog(onDismissRequest = { detailId = null }, title = { Text(selected.name) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CatAvatar(selected.avatar, selected.name, vm)
            Text("ID：${selected.id}", style = MaterialTheme.typography.bodySmall)
            Text(if (selected.isMine) "由你创建" else "共享猫咪 · 基础资料由创建者维护", style = MaterialTheme.typography.bodySmall)
            Text(listOf(selected.species, selected.features, selected.notes).filter { it.isNotBlank() }.joinToString("\n"))
            Text("相遇时间线", style = MaterialTheme.typography.titleMedium)
            val encounters = state.allRecords.filter { it.record.userId == state.user?.id && it.record.catId == selected.id }
                .map { Triple(it.record.createdAt, it.record.title, it.record.id as String?) } +
                state.observations.filter { it.catId == selected.id }.map { Triple(it.observedAt, "${it.place} · ${it.count} 只", null) }
            if (encounters.isEmpty()) Text("还没有关联的观察。发布记录或编辑手记时选择这只猫咪。", color = Muted)
            encounters.sortedByDescending { it.first }.forEach { (time, title, recordId) ->
                Text("${displayTime(time)}\n$title", Modifier.clickable(enabled = recordId != null) {
                    detailId = null; recordId?.let(onRecord)
                }.padding(vertical = 6.dp))
            }
        }
    }, confirmButton = { if (selected.isMine) TextButton(onClick = { editing = selected; detailId = null }) { Text("编辑档案") }
        else TextButton(onClick = { detailId = null }) { Text("关闭") } },
        dismissButton = { if (selected.isMine) TextButton(onClick = { deleting = selected; detailId = null }) { Text("删除档案") } })
    editing?.let { cat -> key(cat.id) {
        var name by rememberSaveable { mutableStateOf(cat.name) }
        var species by rememberSaveable { mutableStateOf(cat.species) }
        var features by rememberSaveable { mutableStateOf(cat.features) }
        var notes by rememberSaveable { mutableStateOf(cat.notes) }
        var avatar by rememberSaveable { mutableStateOf(cat.avatar) }
        var avatarMenu by remember { mutableStateOf(false) }
        var cropPath by rememberSaveable { mutableStateOf<String?>(null) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { vm.importPhotos(listOf(it)) { paths -> cropPath = paths.singleOrNull() } }
        }
        cropPath?.let { path -> AvatarCropDialog(path, state.busy,
            onCancel = { cropPath = null; vm.discardPhotos(listOf(path)) },
            onConfirm = { zoom, x, y -> vm.cropAvatar(path, zoom, x, y) { avatar = it; cropPath = null } }) }
        if (cropPath == null) AlertDialog(onDismissRequest = { if (!state.busy) editing = null }, title = { Text(if (cat.id.isBlank()) "新建猫咪档案" else "编辑猫咪档案") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CatAvatar(avatar, name, vm)
                    Box {
                        TextButton(onClick = { avatarMenu = true }, enabled = !state.busy) { Text("更换头像") }
                        DropdownMenu(avatarMenu, { avatarMenu = false }) {
                            avatarPresets.forEach { preset ->
                                DropdownMenuItem(text = { Text(preset.label) }, onClick = { avatar = preset.id; avatarMenu = false })
                            }
                            DropdownMenuItem(text = { Text("从相册选择") }, onClick = {
                                avatarMenu = false
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            })
                            DropdownMenuItem(text = { Text("恢复首字头像") }, onClick = { avatar = null; avatarMenu = false })
                        }
                    }
                }
                OutlinedTextField(name, { if (it.length <= 80) name = it }, label = { Text("昵称 *") }, enabled = !state.busy)
                OutlinedTextField(species, { if (it.length <= 200) species = it }, label = { Text("品种 / 花色") }, enabled = !state.busy)
                OutlinedTextField(features, { if (it.length <= 1000) features = it }, label = { Text("识别特征") }, enabled = !state.busy)
                OutlinedTextField(notes, { if (it.length <= 1000) notes = it }, label = { Text("私人备注") }, enabled = !state.busy)
            }
        }, confirmButton = { Button(enabled = !state.busy && name.isNotBlank(), onClick = {
            vm.saveCat(cat.copy(name = name.trim(), species = species, features = features, notes = notes, avatar = avatar)) { editing = null }
        }) { Text("保存") } }, dismissButton = { TextButton(enabled = !state.busy, onClick = { editing = null }) { Text("取消") } })
    } }
    deleting?.let { cat -> AlertDialog(onDismissRequest = { if (!state.busy) deleting = null }, title = { Text("删除 ${cat.name}？") },
        text = { Text("观察记录仍会保留，与这份档案的关联会解除。") },
        confirmButton = { TextButton(enabled = !state.busy, onClick = { vm.deleteCat(cat.id) { deleting = null } }) { Text("删除") } },
        dismissButton = { TextButton(enabled = !state.busy, onClick = { deleting = null }) { Text("取消") } }) }
}
