package com.routina.bite.model

import kotlinx.serialization.Serializable

/**
 * 整包備份。四個集合加目標，一個檔就能把 App 還原回來。
 * [schemaVersion] 只在格式不相容時才會往上加。
 */
@Serializable
data class BiteBackup(
    val schemaVersion: Int = 1,
    val app: String = "bite",
    val exportedAt: Long,
    val foods: List<Food>,
    val diary: List<DiaryEntry>,
    val weights: List<WeightEntry>,
    val dayNotes: List<DayNote>,
    val targets: Targets? = null
)
