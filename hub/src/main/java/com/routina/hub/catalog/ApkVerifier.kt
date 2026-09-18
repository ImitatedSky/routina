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
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
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

    private fun signaturesOf(info: android.content.pm.PackageInfo): List<Signature> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return emptyList()
            // 多簽章者時看 apkContentsSigners；單一簽章者時 history 才有內容
            val list = if (signing.hasMultipleSigners()) {
                signing.apkContentsSigners
            } else {
                signing.signingCertificateHistory
            }
            return list?.toList().orEmpty()
        }
        @Suppress("DEPRECATION")
        return info.signatures?.toList().orEmpty()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
