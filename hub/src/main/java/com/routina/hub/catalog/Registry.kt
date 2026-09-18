package com.routina.hub.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 家族名冊：`apps.json` 的格式。
 *
 * 這份檔案住在 Hub 自己的 repo（見 [HubConfig.REGISTRY_URL]），所以加一個新成員
 * 只要改一行 JSON、推一個 commit，Hub 不必改版。
 *
 * [schemaVersion] 讓日後改格式時分得出「舊但可讀」與「讀不懂」——
 * 讀到比 [SUPPORTED_SCHEMA] 新的版本就誠實說「請先更新 Routina」，
 * 而不是硬解成半殘的資料。
 */
@Serializable
data class Registry(
    val schemaVersion: Int = 1,
    val apps: List<RegistryApp> = emptyList()
) {
    companion object {
        const val SUPPORTED_SCHEMA = 1
    }
}

@Serializable
data class RegistryApp(
    val id: String,
    val name: String,
    val summary: String = "",
    /** 用來判斷裝了沒、以及比對下載回來的 APK 是不是同一支 App */
    val packageName: String,
    /** 圖示路徑，相對於 registry。未安裝的成員拿不到系統圖示，只能靠這個 */
    val icon: String = "",
    val source: RegistrySource
)

/**
 * 成員的發佈來源。
 *
 * 刻意只記 [repo] 而不寫死 APK 的網址：Hub 去問 GitHub 要「最新的 release」，
 * 所以發新版不用回頭改 registry。
 */
@Serializable
data class RegistrySource(
    val type: String = TYPE_GITHUB_RELEASE,
    /** `owner/repo` */
    val repo: String,
    /** 從 release 的附件裡挑檔案用的 glob，例 `routina-flow-*.apk` */
    val assetPattern: String
) {
    companion object {
        const val TYPE_GITHUB_RELEASE = "github-release"
    }
}

/** GitHub Releases API 的回應。只宣告我們要的欄位，其餘靠 ignoreUnknownKeys 忽略 */
@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList()
)

@Serializable
data class GithubAsset(
    val name: String = "",
    val size: Long = 0,
    @SerialName("browser_download_url") val downloadUrl: String = ""
)

/** 查詢過 GitHub 之後得到的「遠端最新版」。存進快取的也是這個 */
@Serializable
data class RemoteVersion(
    val version: String,
    val downloadUrl: String,
    val sizeBytes: Long
)

/**
 * 版本比較。
 *
 * 家族的慣例是 tag `vX.Y.Z` 等於 APK 的 versionName `X.Y.Z`，所以直接比數字段落即可
 * （CI 會擋下兩者不一致的發版，見 build.yml 的 tag 檢查）。
 * 比不出來時一律當成「不確定」，由呼叫端決定要不要提示更新——
 * 寧可少提示一次，也不要誤報一個不存在的新版。
 */
object Version {

    /** 去掉開頭的 v 與前後空白 */
    fun normalize(raw: String): String = raw.trim().removePrefix("v").removePrefix("V")

    /**
     * @return 正數表示 [a] 較新、負數表示 [b] 較新、0 表示相同；
     *         任一邊解析不出數字時回 null（不確定）
     */
    fun compare(a: String, b: String): Int? {
        val left = parts(a) ?: return null
        val right = parts(b) ?: return null
        val size = maxOf(left.size, right.size)
        for (i in 0 until size) {
            val l = left.getOrElse(i) { 0 }
            val r = right.getOrElse(i) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    private fun parts(raw: String): List<Int>? {
        val numeric = normalize(raw)
            // 1.0.4-beta2 → 只看 1.0.4；預發佈後綴不參與比較
            .substringBefore('-')
            .substringBefore('+')
            .split('.')
        if (numeric.isEmpty()) return null
        val parsed = numeric.map { it.trim().toIntOrNull() ?: return null }
        return parsed
    }
}

/** 把 `routina-flow-*.apk` 這種 glob 轉成比對用的正則 */
internal fun globToRegex(pattern: String): Regex {
    val escaped = pattern.split('*').joinToString(".*") { Regex.escape(it) }
    return Regex("^$escaped$", RegexOption.IGNORE_CASE)
}
