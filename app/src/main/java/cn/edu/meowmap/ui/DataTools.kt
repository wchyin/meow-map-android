package cn.edu.meowmap.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun DataTools(state: MeowState, vm: MeowViewModel) {
    val context = LocalContext.current
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let { vm.exportBackup(context, it) } }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.importBackup(context, it) } }
    var confirmImport by remember { mutableStateOf(false) }
    SectionTitle("数据备份", "本人档案和观察，照片随备份保存")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { export.launch("meow-backup.zip") }, enabled = state.user != null && !state.busy, modifier = Modifier.weight(1f)) { Text("导出备份") }
        OutlinedButton(onClick = { confirmImport = true }, enabled = state.user != null && !state.busy, modifier = Modifier.weight(1f)) { Text("导入备份") }
    }
    if (confirmImport) AlertDialog(onDismissRequest = { confirmImport = false }, title = { Text("导入到当前云端账号") },
        text = { Text("支持便携 ZIP，以及不含电脑照片路径的原版 JSON。重复 ID 跳过，中断可重试。导入的地图观察及照片公开可见。") },
        confirmButton = { TextButton(onClick = { confirmImport = false; import.launch(arrayOf("application/zip", "application/json", "application/octet-stream")) }) { Text("选择备份") } },
        dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("取消") } })
}
