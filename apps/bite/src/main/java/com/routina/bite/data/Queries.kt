package com.routina.bite.data

import com.routina.bite.model.DayNote
import com.routina.bite.model.DiaryEntry
import com.routina.bite.model.Food
import com.routina.bite.model.Meal
import com.routina.bite.model.Nutrients
import java.time.LocalTime

/**
 * 從紀錄與食物庫算出來的東西。全部是純函式，資料由 repository 的 StateFlow 餵進來。
 */

fun entriesOn(entries: List<DiaryEntry>, date: String): List<DiaryEntry> =
    entries.filter { it.date == date }.sortedBy { it.createdAt }

fun totalOf(entries: List<DiaryEntry>): Nutrients =
    entries.fold(Nutrients.EMPTY) { sum, entry -> sum + entry.total }

/** 有紀錄或有備註的日期，新的在前 */
fun loggedDates(entries: List<DiaryEntry>, dayNotes: List<DayNote>): List<String> =
    (entries.map { it.date } + dayNotes.map { it.date }).distinct().sortedDescending()

/**
 * 常吃：最近 90 天內被記錄的次數最多的食物。
 * 快速輸入（foodId 為 null）與已經被刪掉的食物都不算。
 */
fun frequentFoods(
    entries: List<DiaryEntry>,
    foods: List<Food>,
    limit: Int = SUGGESTION_LIMIT
): List<Food> {
    val since = daysAgo(90)
    val byId = foods.associateBy { it.id }
    return entries.asSequence()
        .filter { it.date >= since }
        .mapNotNull { it.foodId }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .mapNotNull { byId[it.key] }
        .take(limit)
}

/** 最近：依記錄時間由新到舊，不重複的食物 */
fun recentFoods(
    entries: List<DiaryEntry>,
    foods: List<Food>,
    limit: Int = SUGGESTION_LIMIT
): List<Food> {
    val byId = foods.associateBy { it.id }
    return entries.asSequence()
        .sortedByDescending { it.createdAt }
        .mapNotNull { it.foodId }
        .distinct()
        .mapNotNull { byId[it] }
        .take(limit)
        .toList()
}

/**
 * 搜尋名稱與備註（兩者都轉小寫後 contains），常用的排前面，再依名稱。
 * 備註裡帶了品牌（Subway、麥當勞），所以品牌也搜得到。
 */
fun searchFoods(foods: List<Food>, query: String): List<Food> {
    val q = query.trim().lowercase()
    val matched = if (q.isEmpty()) {
        foods
    } else {
        foods.filter { it.name.lowercase().contains(q) || it.note.lowercase().contains(q) }
    }
    return matched.sortedWith(compareByDescending<Food> { it.favorite }.thenBy { it.name })
}

/** 一個分類與它底下的食物。[category] 是空字串就是未分類 */
data class FoodGroup(val category: String, val foods: List<Food>)

/** 食物庫裡用過的分類，去重去空、依名稱排序。編輯食物時拿來當建議 */
fun allCategories(foods: List<Food>): List<String> =
    foods.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()

/**
 * 依分類分組。未分類永遠排最後，其餘依分類名稱；組內沿用搜尋的排序（常用優先再依名稱）。
 */
fun groupByCategory(foods: List<Food>): List<FoodGroup> {
    val groups = foods.groupBy { it.category }
        .map { (category, list) -> FoodGroup(category, searchFoods(list, "")) }
    return groups.sortedWith(
        compareBy<FoodGroup> { it.category.isEmpty() }.thenBy { it.category }
    )
}

/** 新紀錄的預設餐別，依當下時間帶入（D4） */
fun defaultMeal(time: LocalTime = nowTime()): Meal {
    val minutes = time.hour * 60 + time.minute
    return when (minutes) {
        in 240..629 -> Meal.BREAKFAST   // 04:00–10:29
        in 630..869 -> Meal.LUNCH       // 10:30–14:29
        in 870..1019 -> Meal.SNACK      // 14:30–16:59
        in 1020..1289 -> Meal.DINNER    // 17:00–21:29
        else -> Meal.SNACK
    }
}

const val SUGGESTION_LIMIT = 12
