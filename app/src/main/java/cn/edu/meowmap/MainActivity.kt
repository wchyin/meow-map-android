package cn.edu.meowmap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.edu.meowmap.ui.MeowApp
import cn.edu.meowmap.ui.MeowTheme
import cn.edu.meowmap.ui.MeowViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MeowTheme {
                val vm: MeowViewModel = viewModel(factory = MeowViewModel.factory((application as MeowApplication).repository))
                MeowApp(vm)
            }
        }
    }
}
