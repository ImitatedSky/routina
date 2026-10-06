package com.routina.hub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.routina.hub.catalog.CatalogViewModel
import com.routina.hub.ui.DirectoryScreen
import com.routina.hub.ui.SettingsScreen
import com.routina.hub.ui.theme.RoutinaTheme

/**
 * Hub 的主畫面：家族目錄，另外從右上角進設定頁。
 *
 * targetSdk 35 起系統一律強制 edge-to-edge，所以明確開啟並讓 Scaffold
 * 用 innerPadding 處理系統列，內容才不會被狀態列或導覽列蓋住。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RoutinaTheme {
                HubNavHost()
            }
        }
    }
}

private object Routes {
    const val DIRECTORY = "directory"
    const val SETTINGS = "settings"
}

@Composable
private fun HubNavHost() {
    val navController = rememberNavController()
    // 在 NavHost 外面取，兩個畫面才會拿到同一個（Activity 範圍的）ViewModel：
    // 設定頁清快取要看得到目錄的下載狀態，重設排序後目錄也要跟著變
    val viewModel: CatalogViewModel = viewModel()

    NavHost(navController = navController, startDestination = Routes.DIRECTORY) {

        composable(Routes.DIRECTORY) {
            DirectoryScreen(
                viewModel = viewModel,
                // 連點齒輪不要疊出兩層設定頁
                onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                // 指定退回目錄而不是單純 pop：連點返回鍵時才不會把目錄也 pop 掉、剩一片空白
                onBack = { navController.popBackStack(Routes.DIRECTORY, inclusive = false) }
            )
        }
    }
}
