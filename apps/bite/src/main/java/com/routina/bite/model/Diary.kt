package com.routina.bite.model

import kotlinx.serialization.Serializable

@Serializable
enum class Meal { BREAKFAST, LUNCH, DINNER, SNACK }

/**
 * 一筆飲食紀錄。
 *
 * [perServing]、[name]、[servingGrams] 是記錄當下的快照：之後改食物的數值或把食物刪掉，
 * 都不會改寫已經記下的天。[foodId] 為 null 代表「快速輸入」，沒有對應的食物。
 */
@Serializable
data class DiaryEntry(
    val id: String,
    val date: String,
    val meal: Meal? = null,
    val foodId: String? = null,
    val name: String,
    val servings: Double,
    val servingGrams: Double? = null,
    val perServing: Nutrients,
    val createdAt: Long
) {
    /** 這一筆實際吃進去的量 */
    val total: Nutrients get() = perServing * servings
}

@Serializable
data class DayNote(val date: String, val note: String)

@Serializable
data class DiaryFile(
    val entries: List<DiaryEntry> = emptyList(),
    val dayNotes: List<DayNote> = emptyList()
)
