package com.routina.core.contract

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings

/**
 * 找出裝置上已安裝的家族成員。
 *
 * 做法是「列出有啟動圖示的 App，再問誰帶著家族標記」。不用 QUERY_ALL_PACKAGES，
 * 也不用把成員的套件名寫死在 Hub 裡 —— 新成員只要自己宣告 meta-data 就會被看到，
 * Hub 不必跟著改版。
 *
 * 這裡每一個對外查詢都包在 runCatching 裡：讀的是別的 App 的資料，
 * 對方可能正在被更新、被停用、或 manifest 寫錯，任何一個成員壞掉
 * 都不該讓整份清單讀不出來。
 */
object FamilyScanner {

    /**
     * 掃出所有家族成員，依顯示名稱排序。
     *
     * 一律排除呼叫方自己 —— Hub 的目錄不該列出 Hub，子 App 反查家族時也不該列出自己。
     */
    fun scan(context: Context): List<FamilyApp> {
        val pm = context.packageManager
        val self = context.packageName

        val launcherPackages = runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .distinct()
        }.getOrDefault(emptyList())

        return launcherPackages
            .filter { it != self }
            .mapNotNull { read(pm, it) }
            .sortedBy { it.name }
    }

    /** 讀單一套件；不是家族成員（或讀不到）時回 null */
    fun find(context: Context, packageName: String): FamilyApp? =
        read(context.packageManager, packageName)

    /** 成員的 App 圖示。讀不到時回 null，由畫面決定要顯示什麼替代 */
    fun icon(context: Context, packageName: String): Drawable? = runCatching {
        context.packageManager.getApplicationIcon(packageName)
    }.getOrNull()

    /** 開啟成員的主畫面。對方沒有可啟動的入口時回 false */
    fun launch(context: Context, packageName: String): Boolean = runCatching {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    /** 跳到系統的「App 資訊」頁：權限、儲存空間、解除安裝都在那裡 */
    fun openAppInfo(context: Context, packageName: String): Boolean = runCatching {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    private fun read(pm: PackageManager, packageName: String): FamilyApp? = runCatching {
        @Suppress("DEPRECATION")
        val appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        val meta = appInfo.metaData ?: return null

        // 家族標記是唯一的入場條件；沒有它就只是裝置上一個普通 App
        if (!meta.getBoolean(Family.META_MEMBER, false)) return null

        val label = runCatching {
            pm.getApplicationLabel(appInfo).toString()
        }.getOrDefault(packageName)

        @Suppress("DEPRECATION")
        val versionName = runCatching {
            pm.getPackageInfo(packageName, 0).versionName
        }.getOrNull().orEmpty()

        FamilyApp(
            packageName = packageName,
            familyId = meta.getString(Family.META_ID)?.takeIf { it.isNotBlank() } ?: packageName,
            name = meta.getString(Family.META_NAME)?.takeIf { it.isNotBlank() } ?: label,
            summary = meta.getString(Family.META_SUMMARY).orEmpty(),
            versionName = versionName,
            capabilities = readCapabilities(pm, appInfo, meta)
        )
    }.getOrNull()

    private fun readCapabilities(
        pm: PackageManager,
        appInfo: ApplicationInfo,
        meta: Bundle
    ): List<Capability> {
        // 能力清單是選用的：只想被「開啟」的成員不必宣告任何能力
        val resId = meta.getInt(Family.META_CAPABILITIES, 0)
        if (resId == 0) return emptyList()
        return CapabilityReader.read(pm, appInfo, resId)
    }
}
