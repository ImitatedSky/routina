package com.routina.hub.catalog

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File
import java.security.MessageDigest

/**
 * 安裝前的把關：這個 APK 是不是家族的東西。
 *
 * 檢查兩件事，任何一項不符就拒絕安裝：
 * 1. **簽章憑證**要等於 [HubConfig.FAMILY_SIGNER_SHA256]（釘在程式碼裡，不是從網路讀的）
 * 2. **套件名稱**要等於 registry 上說的那一個
 *
 * 只驗第 1 項不夠：簽章對的 APK 也可能是家族裡的另一支 App，裝錯了不是使用者要的。
 * 只驗第 2 項更不夠：套件名稱誰都能宣告。
 */
object ApkVerifier {

    sealed interface Result {
        data object Ok : Result
        data class Rejected(val reason: String) : Result
    }

    fun verify(context: Context, file: File, expectedPackage: String): Result {
        // 同時要求兩種 flag。對 APK *檔案*（而不是已安裝的套件）來說，
        // getPackageArchiveInfo 會填哪個欄位在各 API 版本上並不一致：
        // V2/V3 簽章常常只有 apkContentsSigners 有內容，signingInfo 本身也可能是 null。
        // 兩個都要、三個來源都看，才不會把有簽章的檔案誤判成沒簽章。
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        } else {
            PackageManager.GET_SIGNATURES
        }

        val info = runCatching {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
        }.getOrNull() ?: return Result.Rejected("這個檔案不是可安裝的 APK")

        if (info.packageName != expectedPackage) {
            return Result.Rejected(
                "套件名稱不符：預期 $expectedPackage，檔案是 ${info.packageName}"
            )
        }

        val signatures = signaturesOf(info)
        if (signatures.isEmpty()) return Result.Rejected("這個 APK 沒有簽章")

        val expected = HubConfig.FAMILY_SIGNER_SHA256.lowercase()
        val matched = signatures.any { sha256(it.toByteArray()) == expected }
        if (!matched) {
            return Result.Rejected("簽章不是家族的金鑰，已拒絕安裝")
        }
        return Result.Ok
    }

    /**
     * 把所有可能的來源都收進來：實測（BlueStacks／API 28）發現只讀
     * `signingCertificateHistory` 會拿到空清單，把有簽章的 APK 誤判成沒簽章。
     */
    private fun signaturesOf(info: android.content.pm.PackageInfo): List<Signature> {
        val found = mutableListOf<Signature>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.let { signing ->
                signing.apkContentsSigners?.let { found += it }
                // 有輪替過金鑰時，歷史裡才會有多筆
                runCatching { signing.signingCertificateHistory }.getOrNull()?.let { found += it }
            }
        }
        @Suppress("DEPRECATION")
        info.signatures?.let { found += it }
        return found.distinctBy { it.toCharsString() }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
