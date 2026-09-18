package com.routina.bite.data

import com.routina.bite.model.BiteBackup
import kotlinx.serialization.json.Json

/**
 * 備份檔的編碼與驗證。合併寫在 [BiteRepository.applyBackup]，這裡只負責「讀得懂嗎、收不收」。
 */
object BackupCodec {

    /** 目前支援的備份格式版本。比這個新就不收，免得靜靜漏掉不認得的欄位 */
    const val SCHEMA_VERSION = 1

    private const val APP_ID = "bite"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    enum class Reject { UNREADABLE, NOT_BITE, NEWER_SCHEMA }

    sealed interface Parsed {
        data class Ok(val backup: BiteBackup) : Parsed
        data class Failed(val reason: Reject) : Parsed
    }

    fun encode(backup: BiteBackup): String = json.encodeToString(BiteBackup.serializer(), backup)

    fun parse(text: String): Parsed {
        val backup = try {
            json.decodeFromString(BiteBackup.serializer(), text)
        } catch (t: Throwable) {
            return Parsed.Failed(Reject.UNREADABLE)
        }
        if (backup.app != APP_ID) return Parsed.Failed(Reject.NOT_BITE)
        if (backup.schemaVersion > SCHEMA_VERSION) return Parsed.Failed(Reject.NEWER_SCHEMA)
        return Parsed.Ok(backup)
    }

    fun defaultFileName(): String = "bite-backup-" + compactToday() + ".json"
}
