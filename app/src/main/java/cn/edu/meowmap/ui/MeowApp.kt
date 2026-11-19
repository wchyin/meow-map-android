package cn.edu.meowmap.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.edu.meowmap.BuildConfig
import cn.edu.meowmap.R

private enum class Page(val label: String, val icon: ImageVector) {
    MAP("发现地图", Icons.Outlined.Map),
    PUBLIC("公开观察", Icons.Outlined.Public),
    MINE("我的观察记录", Icons.AutoMirrored.Outlined.ListAlt),
    CATS("猫咪档案", Icons.Outlined.Pets),
    STATS("统计面板", Icons.Outlined.BarChart),
    ASSISTANT("寻猫小助手", Icons.Outlined.Explore),
    PROFILE("个人档案", Icons.Outlined.PersonOutline),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeowApp(vm: MeowViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(Page.MAP) }
    var mapRecordId by rememberSaveable { mutableStateOf<String?>(null) }
    var mapRequest by rememberSaveable { mutableLongStateOf(0L) }
    var more by remember { mutableStateOf(false) }
    val screenState = rememberSaveableStateHolder()
    val primaryPages = listOf(Page.MAP, Page.MINE, Page.CATS)
    val morePages = if (BuildConfig.MEOW_LOCAL_MODE) listOf(Page.STATS)
        else listOf(Page.PUBLIC, Page.STATS, Page.ASSISTANT)
    fun locateRecord(id: String) {
        mapRecordId = id
        mapRequest++
        page = Page.MAP
    }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var catToOpen by rememberSaveable { mutableStateOf<String?>(null) }
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var auth by rememberSaveable { mutableStateOf(false) }
    var dates by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    fun withAccount(action: () -> Unit) { if (state.user == null) auth = true else action() }
    val detail = state.allRecords.find { it.record.id == detailId }
    BackHandler(enabled = editorId == null && (detailId != null || page != Page.MAP)) {
        if (detailId != null) detailId = null else page = Page.MAP
    }
    val showNavigation = detailId == null && editorId == null
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (showNavigation && !wide) Box(contentAlignment = Alignment.BottomEnd) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        primaryPages.forEach { destination ->
                            val label = if (destination == Page.MINE) "观察记录" else destination.label
                            NavigationBarItem(selected = page == destination, onClick = { page = destination }, modifier = Modifier.testTag("nav_" + destination.name.lowercase()),
                                icon = { Icon(destination.icon, null) }, label = { Text(label, maxLines = 1) })
                        }
                        NavigationBarItem(selected = page in morePages, onClick = { more = true },
                            icon = { Icon(Icons.Outlined.MoreHoriz, null) }, label = { Text("更多") })
                        NavigationBarItem(selected = page == Page.PROFILE, onClick = { page = Page.PROFILE },
                            modifier = Modifier.testTag("nav_profile"),
                            icon = { Icon(Page.PROFILE.icon, null) }, label = { Text("个人档案", maxLines = 1) })
                    }
                    DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        morePages.forEach { destination ->
                            DropdownMenuItem(text = { Text(destination.label) }, leadingIcon = { Icon(destination.icon, null) },
                                onClick = { page = destination; more = false })
                        }
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                if (wide && showNavigation) NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    header = { IconButton(onClick = { page = Page.MAP }) {
                        Image(painterResource(R.drawable.ic_launcher), "喵喵地图", Modifier.size(38.dp))
                    } },
                ) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        (primaryPages + morePages + Page.PROFILE).forEach { destination ->
                            NavigationRailItem(selected = page == destination, onClick = { page = destination }, modifier = Modifier.testTag("nav_" + destination.name.lowercase()),
                                icon = { Icon(destination.icon, null) },
                                label = { Text(if (destination == Page.MINE) "观察记录" else destination.label, maxLines = 1) })
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
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
                        detailId != null -> if (detail != null) DetailScreen(state, detail, vm, onBack = { detailId = null }, onEdit = { editorId = detail.record.id }, onAuth = { auth = true }, onDeleted = { detailId = null }, onCat = { id ->
                            detailId = null
                            catToOpen = id
                            page = Page.CATS
                        })
                            else Column { TextButton(onClick = { detailId = null }) { Text("返回") }; EmptyState("记录已不存在", "返回列表看看其他记录") }
                        else -> screenState.SaveableStateProvider(page.name) {
                            when (page) {
                                Page.MAP -> DiscoveryScreen(state, vm, onRecord = { detailId = it }, onLogin = { auth = true },
                                    onDates = { dates = true }, onNew = { withAccount { editorId = "new" } },
                                    targetId = mapRecordId, targetRequest = mapRequest)
                                Page.PUBLIC -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                                    SectionTitle("公开观察")
                                    Spacer(Modifier.height(12.dp))
                                    PublicObservations(state, vm, ::locateRecord)
                                }
                                Page.MINE -> key(state.user?.id) {
                                    ObservationScreen(state, vm, onLogin = { auth = true }, onMapRecord = { detailId = it })
                                }
                                Page.CATS -> key(state.user?.id) {
                                    CatsScreen(state, vm, onLogin = { auth = true }, onRecord = ::locateRecord, requestedCatId = catToOpen, onRequestHandled = { catToOpen = null })
                                }
                                Page.STATS -> {
                                    ObservationAnalytics(state)
                                }
                                Page.ASSISTANT -> AssistantScreen(state, vm, onBack = { page = Page.MAP },
                                    onLogin = { auth = true }, onRecord = ::locateRecord)
                                Page.PROFILE -> ProfileScreen(state, vm, onLogin = { auth = true }, onLogout = vm::logout,
                                    onRename = vm::rename, onMine = { vm.mine(true); mapRecordId = null; page = Page.MAP })
                            }
                        }
                    }
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthDialog(busy: Boolean, onDismiss: () -> Unit, onSubmit: (Boolean, String, String, String, (String) -> Unit) -> Unit) {
    var register by rememberSaveable { mutableStateOf(false) }
    val defaults = !BuildConfig.MEOW_LOCAL_MODE
    var email by rememberSaveable { mutableStateOf(if (defaults) BuildConfig.MEOW_DEFAULT_EMAIL else "") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf(if (!register && defaults) BuildConfig.MEOW_DEFAULT_PASSWORD else "") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, containerColor = MaterialTheme.colorScheme.surface, title = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.cat_avatar_mint), null, Modifier.size(44.dp).clip(MaterialTheme.shapes.small))
            Column {
                Text("喵喵地图")
                Text(if (BuildConfig.MEOW_LOCAL_MODE) "本机账号" else "登录后可发布和同步观察记录",
                    style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
    }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("登录", "注册").forEachIndexed { index, label ->
                    val registering = index == 1
                    SegmentedButton(selected = register == registering, enabled = !busy,
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                        modifier = if (registering) Modifier.testTag("auth_switch") else Modifier, onClick = {
                            if (register != registering) {
                                register = registering
                                email = if (!register && defaults) BuildConfig.MEOW_DEFAULT_EMAIL else ""
                                password = if (!register && defaults) BuildConfig.MEOW_DEFAULT_PASSWORD else ""
                                error = null
                            }
                        }) { Text(label) }
                }
            }
            OutlinedTextField(email, { email = it }, label = { Text("邮箱") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, modifier = Modifier.testTag("auth_email"))

            if (register && BuildConfig.MEOW_LOCAL_MODE) OutlinedTextField(nickname, { nickname = it }, label = { Text("昵称") }, singleLine = true, modifier = Modifier.testTag("auth_nickname"))
            OutlinedTextField(password, { password = it }, label = { Text(if (register) "密码（至少 8 位）" else "密码") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.testTag("auth_password"))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { FilledTonalButton(onClick = {
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
        SectionTitle("个人档案")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = .7f)), contentAlignment = Alignment.Center) { if (avatarBytes == null) Image(painterResource(R.drawable.cat_avatar_mint), null, Modifier.fillMaxSize())
                    else AsyncImage(avatarBytes, "个人头像", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(state.user?.nickname ?: "校园观察者", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(state.user?.email ?: "尚未登录", style = MaterialTheme.typography.bodySmall, color = Muted)
                    state.user?.let { Text("ID  ${it.id}", style = MaterialTheme.typography.labelSmall, color = Muted) }
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
