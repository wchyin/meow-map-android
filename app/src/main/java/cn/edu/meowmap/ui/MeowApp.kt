package cn.edu.meowmap.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.edu.meowmap.data.RecordSummary
import cn.edu.meowmap.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeowApp(vm: MeowViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var auth by rememberSaveable { mutableStateOf(false) }
    var assistant by rememberSaveable { mutableStateOf(false) }
    var dates by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    fun withAccount(action: () -> Unit) { if (state.user == null) auth = true else action() }
    val detail = state.allRecords.find { it.record.id == detailId }
    BackHandler(enabled = detailId != null && editorId == null) { detailId = null }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (detailId == null && editorId == null && !assistant) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                listOf("发现地图", "观察记录", "猫咪档案", "我的").forEachIndexed { index, title ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = {
                        Icon(when (index) { 0 -> Icons.Outlined.Explore; 1 -> Icons.Outlined.EditNote; 2 -> Icons.Outlined.Pets; else -> Icons.Outlined.PersonOutline }, title)
                    }, label = { Text(title) })
                }
            }
        },
        floatingActionButton = {
            if (state.ready && state.error == null && detailId == null && editorId == null && tab == 0 && !assistant) ExtendedFloatingActionButton(
                onClick = { withAccount { editorId = "new" } }, icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("发记录") }, containerColor = Coral, contentColor = Color.White,
                modifier = Modifier.padding(bottom = if (tab == 0) 150.dp else 0.dp).testTag("new_record"),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when {
                state.error != null -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState("数据暂时无法读取", state.error ?: "请重试")
                    Button(onClick = vm::retryInitialize) { Text("重新加载") }
                }
                !state.ready -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                editorId != null -> key(editorId) {
                    val existing = state.allRecords.find { it.record.id == editorId }
                    if (editorId == "new" || existing != null) EditorScreen(state, existing, vm, onBack = { editorId = null }, onSaved = { editorId = null })
                    else Column { TextButton(onClick = { editorId = null }) { Text("返回") }; EmptyState("记录已不存在", "无法继续编辑这条记录") }
                }
                detailId != null -> if (detail != null) DetailScreen(state, detail, vm, onBack = { detailId = null }, onEdit = { editorId = detail.record.id }, onAuth = { auth = true }, onDeleted = { detailId = null })
                    else Column { TextButton(onClick = { detailId = null }) { Text("返回") }; EmptyState("记录已不存在", "返回列表看看其他记录") }
                assistant -> AssistantScreen(state, vm, onBack = { assistant = false }, onRecord = {
                    assistant = false
                    detailId = it
                })
                tab == 1 -> key(state.user?.id) { ObservationScreen(state, vm, onLogin = { auth = true }, onRecord = { detailId = it }) }
                tab == 2 -> key(state.user?.id) { CatsScreen(state, vm, onLogin = { auth = true }, onRecord = { detailId = it }) }
                tab == 3 -> ProfileScreen(state, vm, onLogin = { auth = true }, onLogout = vm::logout, onRename = vm::rename, onMine = { vm.mine(true); tab = 0 })
                else -> DiscoveryScreen(state, vm, onRecord = { detailId = it }, onLogin = { auth = true }, onDates = { dates = true }, onAssistant = { withAccount { assistant = true } })
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
    if (auth) AuthDialog(state.busy, onDismiss = { auth = false }, onSubmit = { register, email, name, password, onError ->
        vm.auth(register, email, name, password, onError) { auth = false }
    })
    if (dates) DateFilterDialog("按发布时间筛选", state.filters.start, state.filters.end, onDismiss = { dates = false }) { from, to ->
        vm.dates(from, to)
        dates = false
    }
}

@Composable
fun RecordCard(record: RecordSummary, onClick: () -> Unit) {
    val r = record.record
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column {
            Box {
                CatCover(record.cover, r.title, Modifier.fillMaxWidth().height(160.dp))
                if (r.demo) Surface(Modifier.padding(12.dp), shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = .9f)) { Text("示例记录", Modifier.padding(6.dp), style = MaterialTheme.typography.labelSmall, color = Sage) }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(r.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (r.description.isNotBlank()) Text(r.description, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Outlined.Place, null, Modifier.size(15.dp), tint = Sage)
                    Text(r.place, Modifier.weight(1f), color = Sage, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Outlined.FavoriteBorder, null, Modifier.size(15.dp), tint = Muted); Text("${record.likeCount}", color = Muted, style = MaterialTheme.typography.labelMedium)
                    Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(15.dp), tint = Muted); Text("${record.commentCount}", color = Muted, style = MaterialTheme.typography.labelMedium)
                }
                Text("${record.author} · ${displayTime(r.createdAt)}", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun AuthDialog(busy: Boolean, onDismiss: () -> Unit, onSubmit: (Boolean, String, String, String, (String) -> Unit) -> Unit) {
    var register by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(if (BuildConfig.MEOW_LOCAL_MODE) { if (register) "创建本机账号" else "登录本机账号" } else { if (register) "创建云端账号" else "登录云端账号" }) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (BuildConfig.MEOW_LOCAL_MODE) "本机模式，账号只在当前设备生效。" else "与桌面版共用账号。当前演示服务使用 HTTP，请使用独立密码。", color = Muted, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(email, { email = it }, label = { Text("邮箱") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, modifier = Modifier.testTag("auth_email"))

            if (register && BuildConfig.MEOW_LOCAL_MODE) OutlinedTextField(nickname, { nickname = it }, label = { Text("昵称") }, singleLine = true, modifier = Modifier.testTag("auth_nickname"))
            OutlinedTextField(password, { password = it }, label = { Text(if (register) "密码（至少 8 位）" else "密码") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.testTag("auth_password"))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = { register = !register; error = null }, enabled = !busy, modifier = Modifier.testTag("auth_switch")) { Text(if (register) "已有账号？去登录" else "还没有账号？注册") }
        }
    }, confirmButton = { Button(onClick = {
        error = null
        onSubmit(register, email, nickname, password) { error = it }
    }, enabled = !busy, modifier = Modifier.testTag("auth_submit")) { Text(if (busy) "请稍候" else if (register) "注册并登录" else "登录") } }, dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } })
}

@Composable
private fun ProfileScreen(state: MeowState, vm: MeowViewModel, onLogin: () -> Unit, onLogout: () -> Unit, onRename: (String, () -> Unit) -> Unit, onMine: () -> Unit) {
    var rename by remember { mutableStateOf(false) }
    var avatar by remember { mutableStateOf<android.net.Uri?>(null) }
    var resetAvatar by remember { mutableStateOf(false) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        if (it != null) { avatar = it; resetAvatar = false }
    }
    val avatarBytes = remember(state.profileAvatar) {
        state.profileAvatar?.substringAfter(',')?.let { runCatching { android.util.Base64.decode(it, android.util.Base64.DEFAULT) }.getOrNull() }
    }
    var nickname by remember(state.user?.nickname) { mutableStateOf(state.user?.nickname ?: "") }
    val own = state.allRecords.count { it.record.userId == state.user?.id }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        SectionTitle("个人中心")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = .7f)), contentAlignment = Alignment.Center) { if (avatarBytes == null) Icon(Icons.Outlined.Pets, null, tint = Coral, modifier = Modifier.size(30.dp))
                    else AsyncImage(avatarBytes, "个人头像", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(state.user?.nickname ?: "校园观察者", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(state.user?.email ?: "尚未登录", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }
        if (state.user == null) Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().testTag("profile_login")) { Text("登录 / 注册") }
        else {
            OutlinedButton(onClick = onMine, modifier = Modifier.fillMaxWidth()) { Text("我发布的记录 · $own 条") }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { avatar = null; resetAvatar = false; rename = true }) { Text("编辑资料") }
                TextButton(onClick = onLogout, modifier = Modifier.weight(1f), enabled = !state.busy) { Text("退出登录") }
            }
        }
        SectionTitle("观察数据", "当前已加载的地图数据统计")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("地图记录", state.allRecords.size.toString(), Modifier.weight(1f))
            StatTile("我的发布", own.toString(), Modifier.weight(1f))
            StatTile("已载评论", state.comments.size.toString(), Modifier.weight(1f))
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("地点分布", fontWeight = FontWeight.SemiBold)
                state.allRecords.groupingBy { it.record.place }.eachCount().entries.sortedByDescending { it.value }.take(6).forEach { (name, count) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row { Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); Text("$count 条", style = MaterialTheme.typography.bodySmall, color = Muted) }
                        LinearProgressIndicator(progress = { count.toFloat() / maxOf(1, state.allRecords.size) }, modifier = Modifier.fillMaxWidth(), color = Sage, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
        if (!BuildConfig.MEOW_LOCAL_MODE) DataTools(state, vm)
        Text("版本 ${BuildConfig.VERSION_NAME}\n与桌面喵喵地图共享云端数据；离线可查看缓存。", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
    if (rename) AlertDialog(onDismissRequest = { if (!state.busy) rename = false }, title = { Text("编辑个人资料") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(nickname, { nickname = it }, label = { Text("昵称") }, singleLine = true, enabled = !state.busy)
                if (!BuildConfig.MEOW_LOCAL_MODE) {
                    TextButton(enabled = !state.busy, onClick = {
                        avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text(if (avatar == null) "选择头像" else "已选新头像，点击更换") }
                    if (avatarBytes != null || resetAvatar) TextButton(enabled = !state.busy, onClick = {
                        avatar = null; resetAvatar = !resetAvatar
                    }) { Text(if (resetAvatar) "撤销移除头像" else "移除头像") }
                }
            }
        }, confirmButton = { TextButton(enabled = !state.busy && nickname.isNotBlank(), onClick = {
            if (BuildConfig.MEOW_LOCAL_MODE) onRename(nickname) { rename = false }
            else vm.updateProfile(nickname, avatar, resetAvatar) { rename = false }
        }) { Text("保存") } }, dismissButton = { TextButton(enabled = !state.busy, onClick = { rename = false }) { Text("取消") } })
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = Coral, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}
