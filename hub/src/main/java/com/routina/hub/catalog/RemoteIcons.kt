package com.routina.hub.catalog

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * 抓名冊上的圖示。未安裝的成員拿不到系統圖示，只有這條路。
 *
 * 圖示很小又不會變，記在記憶體就夠，不值得為它引入圖片載入函式庫。
 * 抓失敗時記成 null 並且不再重試——一個缺圖不該讓畫面每次重繪都再打一次網路。
 */
object RemoteIcons {

    private val cache = ConcurrentHashMap<String, Holder>()

    private class Holder(val bitmap: ImageBitmap?)

    suspend fun load(url: String): ImageBitmap? {
        cache[url]?.let { return it.bitmap }
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Routina-Hub")
                }
                try {
                    if (connection.responseCode !in 200..299) return@runCatching null
                    val bytes = connection.inputStream.use { it.readBytes() }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
        cache[url] = Holder(loaded)
        return loaded
    }
}
