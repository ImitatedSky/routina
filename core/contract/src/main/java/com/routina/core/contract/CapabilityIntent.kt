package com.routina.core.contract

import android.content.Context
import android.content.Intent

/**
 * 組出／拆解「呼叫某個能力」的 Intent。呼叫方與被呼叫方共用同一份規則，
 * 兩邊都只依賴 [Family] 裡的字串常數。
 */
object CapabilityIntent {

    /**
     * 組一個呼叫 [packageName] 的 [capabilityId] 的 Intent。
     *
     * 帶 setPackage：只會解析到那一個 App，不會跳出選擇器、也不會被別人攔截。
     * 帶 NEW_TASK：呼叫方可能是背景服務（Flow 的執行器就是），沒有 Activity 堆疊可用。
     */
    fun build(
        packageName: String,
        capabilityId: String,
        params: Map<String, String> = emptyMap()
    ): Intent = Intent(Family.ACTION_RUN_CAPABILITY).apply {
        setPackage(packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(Family.EXTRA_CAPABILITY_ID, capabilityId)
        params.forEach { (name, value) ->
            putExtra(Family.EXTRA_PARAM_PREFIX + name, value)
        }
    }

    /** 對方有沒有人接這個 Intent。用來在送出前給誠實的提示，而不是送出後靜靜失敗 */
    fun canRun(context: Context, packageName: String, capabilityId: String): Boolean =
        runCatching {
            val intent = build(packageName, capabilityId)
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
        }.getOrDefault(false)

    /** 被呼叫方用：這次要執行哪個能力 */
    fun capabilityId(intent: Intent?): String? =
        intent?.getStringExtra(Family.EXTRA_CAPABILITY_ID)?.takeIf { it.isNotBlank() }

    /** 被呼叫方用：取出參數。只認前綴相符的 extras，其他一概忽略 */
    fun params(intent: Intent?): Map<String, String> {
        val extras = intent?.extras ?: return emptyMap()
        return extras.keySet()
            .filter { it.startsWith(Family.EXTRA_PARAM_PREFIX) }
            .mapNotNull { key ->
                val name = key.removePrefix(Family.EXTRA_PARAM_PREFIX)
                    .takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val value = runCatching { extras.getString(key) }.getOrNull()
                    ?: return@mapNotNull null
                name to value
            }
            .toMap()
    }
}
