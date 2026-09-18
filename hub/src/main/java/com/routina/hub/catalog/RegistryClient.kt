package com.routina.hub.catalog

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 讀家族名冊，並問 GitHub 每個成員的最新版本。
 *
 * 遠端版本會快取（見 [HubConfig.REMOTE_CACHE_HOURS]）：匿名的 GitHub API
 * 每小時每 IP 只有 60 次額度，而每個成員要問一次，不能每次開 App 都查。
 */
class RegistryClient(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true   // GitHub 的回應欄位很多，只取我們要的
        isLenient = true
    }

    private val prefs by lazy {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /**
     * 取得名冊。[force] 為 false 時可以用快取的副本（離線也還有東西可顯示）。
     */
    suspend fun registry(force: Boolean): Result<Registry> = withContext(Dispatchers.IO) {
        val cached = prefs.getString(KEY_REGISTRY, null)
        if (!force && cached != null) {
            parseRegistry(cached)?.let { return@withContext Result.success(it) }
        }
        val fetched = runCatching { get(HubConfig.REGISTRY_URL) }
        fetched.fold(
            onSuccess = { body ->
                val parsed = parseRegistry(body)
                    ?: return@withContext Result.failure(IOException("名冊格式看不懂"))
                if (parsed.schemaVersion > Registry.SUPPORTED_SCHEMA) {
                    return@withContext Result.failure(
                        IOException("名冊是較新的格式（v${parsed.schemaVersion}），請先更新 Routina")
                    )
                }
                prefs.edit().putString(KEY_REGISTRY, body).apply()
                Result.success(parsed)
            },
            onFailure = { error ->
                // 抓不到就退回快取；完全沒有快取才算失敗
                val fallback = cached?.let { parseRegistry(it) }
                if (fallback != null) Result.success(fallback) else Result.failure(error)
            }
        )
    }

    /**
     * 問某個成員的最新版本。[force] 為 false 且快取還新鮮時直接用快取。
     *
     * 查不到（沒有任何 release、附件名稱對不上 pattern、額度用完、離線）
     * 一律回 failure，由畫面顯示「查不到最新版」而不是假裝沒有更新。
     */
    suspend fun latest(app: RegistryApp, force: Boolean): Result<RemoteVersion> =
        withContext(Dispatchers.IO) {
            if (app.source.type != RegistrySource.TYPE_GITHUB_RELEASE) {
                return@withContext Result.failure(
                    IOException("不支援的來源類型：${app.source.type}")
                )
            }

            if (!force) {
                cachedRemote(app.id)?.let { return@withContext Result.success(it) }
            }

            val url = "https://api.github.com/repos/${app.source.repo}/releases/latest"
            val body = runCatching { get(url, githubApi = true) }
                .getOrElse { error ->
                    // 查不到就退回快取（可能過期但比空白有用）
                    val stale = staleRemote(app.id)
                    return@withContext if (stale != null) Result.success(stale)
                    else Result.failure(error)
                }

            val release = runCatching {
                json.decodeFromString(GithubRelease.serializer(), body)
            }.getOrNull() ?: return@withContext Result.failure(IOException("release 格式看不懂"))

            val matcher = globToRegex(app.source.assetPattern)
            val asset = release.assets.firstOrNull { matcher.matches(it.name) }
                ?: return@withContext Result.failure(
                    IOException("最新的 release 裡沒有符合 ${app.source.assetPattern} 的檔案")
                )

            val remote = RemoteVersion(
                version = Version.normalize(release.tagName),
                downloadUrl = asset.downloadUrl,
                sizeBytes = asset.size
            )
            putRemote(app.id, remote)
            Result.success(remote)
        }

    // ---------- 快取 ----------

    private fun cachedRemote(id: String): RemoteVersion? {
        val savedAt = prefs.getLong(keyRemoteAt(id), 0L)
        val ageMs = System.currentTimeMillis() - savedAt
        if (savedAt <= 0L || ageMs > HubConfig.REMOTE_CACHE_HOURS * 60 * 60 * 1000) return null
        return staleRemote(id)
    }

    /** 不看時效的快取，用在網路失敗時的退路 */
    private fun staleRemote(id: String): RemoteVersion? {
        val raw = prefs.getString(keyRemote(id), null) ?: return null
        return runCatching { json.decodeFromString(RemoteVersion.serializer(), raw) }.getOrNull()
    }

    private fun putRemote(id: String, remote: RemoteVersion) {
        runCatching {
            prefs.edit()
                .putString(keyRemote(id), json.encodeToString(RemoteVersion.serializer(), remote))
                .putLong(keyRemoteAt(id), System.currentTimeMillis())
                .apply()
        }
    }

    private fun parseRegistry(body: String): Registry? =
        runCatching { json.decodeFromString(Registry.serializer(), body) }.getOrNull()

    // ---------- HTTP ----------

    /**
     * 單純的 GET。
     *
     * [githubApi] 會補上 GitHub API 要求的標頭——**匿名請求不帶 User-Agent 會被回 403**，
     * 這是最容易誤判成「網路壞了」的雷。
     */
    private fun get(url: String, githubApi: Boolean = false): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            if (githubApi) {
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException(describe(code, connection.getHeaderField("x-ratelimit-remaining")))
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** 把 HTTP 狀態碼翻成使用者看得懂、而且知道能怎麼辦的訊息 */
    private fun describe(code: Int, rateRemaining: String?): String = when {
        code == 404 -> "找不到（可能還沒發佈任何版本）"
        code == 403 && rateRemaining == "0" ->
            "GitHub 查詢額度暫時用完了（每小時 60 次），稍後再試"
        code == 403 -> "GitHub 拒絕了這次查詢（403）"
        code >= 500 -> "GitHub 伺服器暫時有問題（$code）"
        else -> "讀取失敗（HTTP $code）"
    }

    private companion object {
        const val PREFS = "routina_catalog"
        const val KEY_REGISTRY = "registry_json"
        const val USER_AGENT = "Routina-Hub"

        fun keyRemote(id: String) = "remote_$id"
        fun keyRemoteAt(id: String) = "remote_at_$id"
    }
}
