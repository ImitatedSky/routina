package com.routina.hub.catalog

/** Hub 對外的幾個固定值，集中一處方便查對 */
object HubConfig {

    /** 家族名冊。repo 是 public，所以匿名讀取即可、不需要任何 token */
    const val REGISTRY_URL =
        "https://raw.githubusercontent.com/ImitatedSky/routina/main/apps.json"

    /** 圖示等相對路徑以這裡為基準 */
    const val REGISTRY_BASE =
        "https://raw.githubusercontent.com/ImitatedSky/routina/main/"

    /**
     * 家族簽章憑證的 SHA-256 指紋（小寫十六進位，無分隔）。
     *
     * **刻意寫死在程式碼裡，而不是放在 registry。** registry 是從網路讀的，
     * 內容有被替換的可能；釘在 APK 裡的這個值不會。下載回來的檔案要簽章對得上
     * 才允許安裝（見 [ApkVerifier]）。
     *
     * 換金鑰＝所有成員都得重新簽章，屆時這裡也要跟著改版。
     */
    const val FAMILY_SIGNER_SHA256 =
        "563e24f9c6796daacb8cc6ddc2d8f3b3075762cf7f2a4d7ff1c37174ed7f74b5"

    /**
     * 遠端版本資訊的快取時效。
     *
     * 匿名的 GitHub API 每小時每 IP 只有 60 次額度，而 Hub 每個成員要問一次，
     * 所以不能每次開 App 都查。使用者按重新掃描時會強制更新，不受這個時效限制。
     */
    const val REMOTE_CACHE_HOURS = 6L
}
