package com.routina.hub.catalog

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 把 APK 下載到 App 私有快取。
 *
 * 存在私有目錄（不是公開的下載資料夾）有兩個理由：不需要儲存權限，
 * 而且別的 App 動不到那個檔案——安裝前被替換掉就白驗簽章了。
 */
object ApkDownloader {

    /** 下載用的暫存目錄。安裝完或下次下載前會清掉 */
    private fun dir(context: Context): File =
        File(context.cacheDir, "apk").apply { mkdirs() }

    /**
     * 下載 [url] 到私有快取。
     *
     * @param onProgress 已下載位元組數、總位元組數（總數未知時為 -1）
     */
    suspend fun download(
        context: Context,
        url: String,
        fileName: String,
        onProgress: (Long, Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val target = File(dir(context), fileName)
        // 寫到 .part 再改名：中途失敗或被砍時不會留下半個檔案被當成完整 APK
        val partial = File(dir(context), "$fileName.part")
        runCatching {
            partial.delete()
            target.delete()

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 30_000
                // GitHub 的附件網址會轉到 objects.githubusercontent.com
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Routina-Hub")
            }

            try {
                val code = connection.responseCode
                if (code !in 200..299) throw IOException("下載失敗（HTTP $code）")
                val total = connection.contentLengthLong

                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            onProgress(done, total)
                        }
                        output.flush()
                    }
                }
            } finally {
                connection.disconnect()
            }

            if (partial.length() <= 0L) throw IOException("下載到的檔案是空的")
            if (!partial.renameTo(target)) throw IOException("無法寫入快取")
            target
        }.onFailure { partial.delete() }
    }

    /** 清掉暫存的 APK。安裝流程走完就不需要留著它們 */
    fun clear(context: Context) {
        runCatching { dir(context).listFiles()?.forEach { it.delete() } }
    }
}
