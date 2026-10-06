package com.routina.hub.catalog

import android.content.Context

/**
 * 使用者自訂的「已安裝」排列順序，存成成員 id 的清單。
 *
 * 沒出現在清單裡的成員（剛裝好的）排在最後面。
 */
class AppOrder(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): List<String> =
        prefs.getString(KEY_ORDER, null)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            .orEmpty()

    /**
     * 存下 [visible] 的順序。之前存過、但現在沒顯示的成員（暫時移除了）接在後面，
     * 重新安裝時才會回到原本的位置附近。
     */
    fun save(visible: List<String>) {
        val kept = load().filterNot { it in visible }
        prefs.edit().putString(KEY_ORDER, (visible + kept).joinToString(",")).apply()
    }

    /** 忘掉使用者排過的順序，已安裝的成員回到名冊上的順序 */
    fun clear() {
        prefs.edit().remove(KEY_ORDER).apply()
    }

    private companion object {
        const val PREFS = "hub_order"
        const val KEY_ORDER = "installed_order"
    }
}
