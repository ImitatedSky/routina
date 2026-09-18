package com.routina.hub.catalog

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 把 APK 下載到 App 私有快取。
 *
 * 存在私有目錄（不是公開的下載資料夾）有兩個理由：不需要儲存權限，
 * 而且別的 App 動不到那個檔案——安裝前被替換掉就白驗簽章了。
 *
 * 下載途中使用者切出去做別的事，Hub 的行程可能整個被系統回收，協程跟著消失。
 * 所以 `.part` 一律保留、不在失敗時刪掉，下次用 HTTP Range 從斷點接續；
 * 在 BlueStacks 上一包 3 MB 的 APK 要跑好幾分鐘，從 0 重來對使用者是真的痛。
 */
object ApkDownloader {

    /** 下載用的暫存目錄 */
    private fun dir(context: Context): File =
        File(context.cacheDir, "apk").apply { mkdirs() }

    private fun partialFile(context: Context, fileName: String): File =
        File(dir(context), "$fileName.part")

    /**
     * 已經下載到一半的位元組數，沒有殘檔時回 0。
     *
     * 給畫面用：行程被回收後 ViewModel 的狀態沒了，但檔案還在，
     * 按鈕要講得出「繼續下載」而不是假裝什麼都沒發生過。
     */
    fun resumableBytes(context: Context, fileName: String): Long =
        runCatching { partialFile(context, fileName).length() }.getOrDefault(0L)

    /**
     * 下載 [url] 到私有快取，有殘檔就接續。
     *
     * @param expectedSize 名冊上記的檔案大小，0 表示不知道。用來判斷「已經下載完了」
     *   與「殘檔比整包還大＝壞了」，也用來補上伺服器沒給的總長度。
     * @param onProgress 累計已下載位元組數（含這次之前下載的）、總位元組數（未知時為 -1）
     */
    suspend fun download(
        context: Context,
        url: String,
        fileName: String,
        expectedSize: Long,
        onProgress: (Long, Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val target = File(dir(context), fileName)
        // 寫到 .part 再改名：中途失敗或被砍時不會留下半個檔案被當成完整 APK
        val partial = partialFile(context, fileName)

        runCatching {
            // 下載完卻在系統安裝畫面按了取消，檔案還在。重按不該再下載一次
            if (expectedSize > 0 && target.length() == expectedSize) {
                onProgress(expectedSize, expectedSize)
                return@runCatching target
            }
            // 大小對不上的成品是殘缺的，留著也只會擋住等一下的改名
            target.delete()

            // 檔名帶了版本，不同版本不會互相污染；但同版本的殘檔仍可能寫壞，
            // 比整包還大就是壞了
            if (expectedSize > 0 && partial.length() > expectedSize) partial.delete()

            var start = partial.length()
            var connection = open(url, start)
            try {
                var code = connection.responseCode
                // 416：伺服器認為這個起點超出檔案範圍，代表殘檔跟遠端對不上，丟掉重來
                if (code == 416 && start > 0) {
                    connection.disconnect()
                    partial.delete()
                    start = 0L
                    connection = open(url, 0L)
                    code = connection.responseCode
                }
                if (code !in 200..299) throw IOException("下載失敗（HTTP $code）")

                // 只有 206 才是真的接續；回 200 表示伺服器不理會 Range，
                // 送來的是整包，只能把殘檔清空從頭寫
                val resuming = code == HttpURLConnection.HTTP_PARTIAL && start > 0
                val offset = if (resuming) start else 0L
                val total = totalBytes(connection, offset, expectedSize)

                connection.inputStream.use { input ->
                    FileOutputStream(partial, resuming).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = offset
                        onProgress(done, total)
                        // 這個讀取迴圈是阻塞的，協程被取消時不會自己停；不看 isActive 的話，
                        // ViewModel 被清掉後它還在寫同一個 .part，會跟下一次的續傳互相蓋掉
                        while (isActive) {
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

            val got = partial.length()
            if (got <= 0L) throw IOException("下載到的檔案是空的")
            // 連線斷在半路時 read 只會回 -1、不會丟例外，靠大小才看得出來沒下載完。
            // 這裡不刪 .part，使用者再按一次就會從這裡接下去
            if (expectedSize > 0 && got != expectedSize) {
                throw IOException(
                    "只下載到 $got / $expectedSize 位元組，連線可能中斷了。再按一次會從這裡繼續"
                )
            }
            if (!partial.renameTo(target)) throw IOException("無法寫入快取")
            target
        }
    }

    private fun open(url: String, start: Long): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            // GitHub 的附件網址會轉到 objects.githubusercontent.com
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Routina-Hub")
            // 預設會帶 Accept-Encoding: gzip 並在背後解壓，那樣收到的位元組數
            // 就對不上檔案的位移，Range 會接錯地方
            setRequestProperty("Accept-Encoding", "identity")
            if (start > 0) setRequestProperty("Range", "bytes=$start-")
        }

    /**
     * 整包有多大。
     *
     * 206 的 Content-Length 只算「這次要傳的那一段」，不是整包，所以優先看
     * Content-Range 尾巴的 `/總長度`，其次用名冊上記的大小，最後才用起點加長度推。
     */
    private fun totalBytes(
        connection: HttpURLConnection,
        offset: Long,
        expectedSize: Long
    ): Long {
        val fromRange = connection.getHeaderField("Content-Range")
            ?.substringAfter('/', "")
            ?.trim()
            ?.toLongOrNull()
        val length = connection.contentLengthLong
        return when {
            fromRange != null && fromRange > 0 -> fromRange
            expectedSize > 0 -> expectedSize
            length > 0 -> offset + length
            else -> -1L
        }
    }

    /** 清掉暫存的 APK。安裝流程走完就不需要留著它們 */
    fun clear(context: Context) {
        runCatching { dir(context).listFiles()?.forEach { it.delete() } }
    }
}
