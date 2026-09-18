package com.routina.bite.ui

import com.routina.bite.data.parseDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 顯示用的格式：熱量取整數，其餘一位小數（D5）。
 */

fun formatKcal(value: Double): Int = value.roundToInt()

fun formatGrams(value: Double): String = String.format(Locale.TAIWAN, "%.1f", value)

/** 份數：整數就不顯示小數點，1.5 份仍然顯示 1.5 */
fun formatAmount(value: Double): String =
    if (abs(value - value.roundToInt()) < 0.001) value.roundToInt().toString()
    else String.format(Locale.TAIWAN, "%.2f", value).trimEnd('0').trimEnd('.')

private val dayLabel = DateTimeFormatter.ofPattern("M月d日（E）", Locale.TAIWAN)
private val dayLabelWithYear = DateTimeFormatter.ofPattern("yyyy年M月d日（E）", Locale.TAIWAN)

/** 不是今年的日期才帶年份，歷史列表裡跨年的日子才分得出來 */
fun formatDayLabel(date: String): String {
    val parsed = parseDate(date)
    val pattern = if (parsed.year == LocalDate.now().year) dayLabel else dayLabelWithYear
    return parsed.format(pattern)
}
