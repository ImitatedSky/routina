package com.routina.bite.data

import com.routina.bite.model.DiaryEntry
import com.routina.bite.model.Nutrients
import com.routina.bite.model.Targets
import com.routina.bite.model.WeightEntry
import com.routina.bite.model.WeightSource
import java.time.temporal.ChronoUnit

/**
 * 圖表用的統計。全部是純函式，資料由 repository 的 StateFlow 餵進來，與 Queries.kt 同一個角色。
 *
 * 貫串整份的一條原則：**沒有紀錄的日子不是 0**。沒記錄不代表沒吃，補 0 會畫出一根貼地的長條，
 * 也會把平均拉低、讓最低值永遠是 0、把沒記錄的日子算成達標。所以每日合計只回有紀錄的日子，
 * 所有摘要的分母都是有紀錄的天數。
 */

enum class ChartRange { DAYS_7, DAYS_30, DAYS_90, ALL }

/** 圖的 x 軸：起訖都含端點的日曆日 */
data class DateSpan(val start: String, val end: String) {

    /** 含兩端的天數 */
    val dayCount: Int
        get() = ChronoUnit.DAYS.between(parseDate(start), parseDate(end)).toInt() + 1

    /** 日期在軸上的位置，0 是 start */
    fun indexOf(date: String): Int =
        ChronoUnit.DAYS.between(parseDate(start), parseDate(date)).toInt()

    fun dateAt(index: Int): String = shiftDate(start, index.toLong())

    /** 日期落在區間內才回位置。換區間後殘留的選取就不會被畫到畫面外 */
    fun indexIn(date: String?): Int? = date?.takeIf { it in start..end }?.let { indexOf(it) }
}

/** 某一天的合計。只有真的有紀錄的日子才會出現 */
data class DayTotal(val date: String, val total: Nutrients)

data class DayValue(val date: String, val value: Double)

data class WeightPoint(
    val date: String,
    val kg: Double,
    val bodyFatPct: Double?,
    val source: WeightSource
)

data class KcalSummary(
    val loggedDays: Int,
    val average: Double,
    val highest: DayValue?,
    val lowest: DayValue?,
    val onTargetDays: Int
)

data class WeightSummary(
    val first: WeightPoint,
    val last: WeightPoint,
    val changeKg: Double
)

data class MacroSummary(
    val loggedDays: Int,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val proteinOnTargetDays: Int
)

/**
 * 區間的起訖。7／30／90 天以今天為結尾；「全部」是這張圖自己資料的最早到最晚
 * ——不是畫到今天，使用者的紀錄在一年前，畫到今天會多出十個月的空白。
 * [dates] 空的時候「全部」沒有範圍可畫，回 null。
 */
fun spanOf(range: ChartRange, dates: List<String>): DateSpan? {
    if (range == ChartRange.ALL) {
        val start = dates.minOrNull() ?: return null
        return DateSpan(start, dates.max())
    }
    val days = when (range) {
        ChartRange.DAYS_7 -> 7L
        ChartRange.DAYS_30 -> 30L
        else -> 90L
    }
    val end = todayDate()
    return DateSpan(shiftDate(end, -(days - 1)), end)
}

/**
 * 開畫面時用的區間。預設 30 天，但使用者匯入的紀錄可能整批都在一年前，
 * 那樣預設區間是空的；這時改用「全部」，畫面上會說明原因。
 */
fun initialRange(entries: List<DiaryEntry>, weights: List<WeightEntry>): ChartRange {
    val since = daysAgo(29)
    val hasRecent = entries.any { it.date >= since } || weights.any { it.date >= since }
    return if (hasRecent) ChartRange.DAYS_30 else ChartRange.ALL
}

/** 區間內每一天的合計，依日期排序。沒有紀錄的日子不在清單裡 */
fun dailyTotals(entries: List<DiaryEntry>, span: DateSpan): List<DayTotal> =
    entries.asSequence()
        .filter { it.date >= span.start && it.date <= span.end }
        .groupBy { it.date }
        .map { (date, dayEntries) -> DayTotal(date, totalOf(dayEntries)) }
        .sortedBy { it.date }

/**
 * 移動平均，回傳與 [days] 一一對應的清單，沒有值的位置是 null（折線在那裡斷開）。
 * 以當天為右端的 [window] 個日曆天，其中有紀錄的日子不足 [minDays] 就不給值——
 * 靠兩三筆資料撐起來的「週平均」比斷線更誤導。
 */
fun movingAverage(
    days: List<DayTotal>,
    window: Int = 7,
    minDays: Int = 4,
    value: (Nutrients) -> Double
): List<DayValue?> {
    // days 已依日期排序，用一個左指標滑過去就好，不要對每一天再掃一次整串
    var start = 0
    return days.mapIndexed { index, day ->
        val earliest = shiftDate(day.date, -(window - 1).toLong())
        while (days[start].date < earliest) start++
        val inWindow = days.subList(start, index + 1)
        if (inWindow.size < minDays) null
        else DayValue(day.date, inWindow.sumOf { value(it.total) } / inWindow.size)
    }
}

fun kcalSummary(days: List<DayTotal>, target: Int): KcalSummary {
    val values = days.map { DayValue(it.date, it.total.kcal) }
    return KcalSummary(
        loggedDays = values.size,
        average = if (values.isEmpty()) 0.0 else values.sumOf { it.value } / values.size,
        highest = values.maxByOrNull { it.value },
        lowest = values.minByOrNull { it.value },
        onTargetDays = values.count { it.value <= target }
    )
}

/** 區間內的體重點。同一天可能有兩筆（家用體重計與 InBody），兩筆都畫 */
fun weightSeries(weights: List<WeightEntry>, span: DateSpan): List<WeightPoint> =
    weights.asSequence()
        .filter { it.date >= span.start && it.date <= span.end }
        .map { WeightPoint(it.date, it.kg, it.bodyFatPct, it.source) }
        .sortedWith(compareBy({ it.date }, { it.source }))
        .toList()

fun weightSummary(points: List<WeightPoint>): WeightSummary? {
    if (points.isEmpty()) return null
    val first = points.first()
    val last = points.last()
    return WeightSummary(first, last, last.kg - first.kg)
}

fun macroSummary(days: List<DayTotal>, targets: Targets): MacroSummary {
    val count = days.size
    fun average(pick: (Nutrients) -> Double) =
        if (count == 0) 0.0 else days.sumOf { pick(it.total) } / count
    return MacroSummary(
        loggedDays = count,
        protein = average { it.protein },
        fat = average { it.fat },
        carbs = average { it.carbs },
        proteinOnTargetDays = days.count { it.total.protein >= targets.protein }
    )
}
