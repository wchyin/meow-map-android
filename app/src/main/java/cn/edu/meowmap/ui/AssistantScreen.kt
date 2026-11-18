package cn.edu.meowmap.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.edu.meowmap.data.AssistantAnswer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(state: MeowState, vm: MeowViewModel, onBack: () -> Unit, onRecord: (String) -> Unit) {
    var question by rememberSaveable { mutableStateOf("") }
    var answer by remember { mutableStateOf<AssistantAnswer?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(title = { Text("寻猫小助手") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
        })
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.user == null) {
                EmptyState("请重新登录", "登录后可以查询公开观察")
            } else {
                OutlinedTextField(question, { question = it.take(300) }, Modifier.fillMaxWidth(),
                    label = { Text("想找哪只猫？") }, placeholder = { Text("例如：大学城校区哪里见过橘猫？") },
                    minLines = 2, maxLines = 4, enabled = !state.busy)
                Button(enabled = !state.busy && question.isNotBlank(), onClick = {
                    error = null
                    answer = null
                    vm.askAssistant(question, { answer = it }, { error = it })
                }) { Text(if (state.busy) "查询中…" else "查询公开观察") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                answer?.let { result ->
                    val mapIds = remember(state.allRecords) { state.allRecords.map { it.record.id }.toSet() }
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item { Text(result.text, style = MaterialTheme.typography.bodyLarge) }
                        if (result.sources.isNotEmpty()) item { Text("引用的公开观察", style = MaterialTheme.typography.titleMedium) }
                        items(result.sources) { source ->
                            Column(Modifier.fillMaxWidth()) {
                                Text("[${source.reference}] ${source.label}", style = MaterialTheme.typography.titleSmall)
                                if (source.location.isNotBlank()) Text(source.location, color = Muted)
                                val mapId = source.mapId
                                if (mapId != null && mapId in mapIds) TextButton(onClick = { onRecord(mapId) }) { Text("查看地图打卡") }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
