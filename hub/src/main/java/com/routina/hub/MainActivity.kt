package com.routina.hub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.routina.hub.ui.DirectoryScreen
import com.routina.hub.ui.theme.RoutinaTheme

/**
 * Hub 的唯一畫面：家族目錄。
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
                DirectoryScreen()
            }
        }
    }
}
