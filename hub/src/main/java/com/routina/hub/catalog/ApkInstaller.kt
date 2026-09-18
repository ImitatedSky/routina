package com.routina.hub.catalog

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * 把驗過簽章的 APK 交給系統安裝器。
 *
 * 用 ACTION_VIEW 叫出系統的安裝對話框，而不是自己跑 PackageInstaller session：
 * 兩者都一樣需要使用者按下確認、也都需要 REQUEST_INSTALL_PACKAGES 權限，
 * 但前者少一大段 session 管理的程式碼。代價是拿不到安裝結果——
 * 這裡不成問題，因為 Hub 回到前景時本來就會重掃清單，狀態會自己更新。
 */
object ApkInstaller {

    /** 使用者有沒有授權 Hub 安裝 App。沒有的話安裝一定失敗，要先引導去開 */
    fun canInstall(context: Context): Boolean =
        runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)

    /** 跳到「允許這個來源安裝應用程式」的系統設定頁 */
    fun openInstallPermission(context: Context): Boolean = runCatching {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    /**
     * 開啟安裝畫面。回 false 表示連對話框都叫不出來（極少見，通常是 ROM 閹割）。
     *
     * 檔案在 Hub 的私有快取裡，安裝器讀不到，所以透過 FileProvider 發一個
     * 臨時可讀的 content URI 過去。
     */
    fun install(context: Context, file: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
