package com.routina.bite.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 日期一律用本地日期的 yyyy-MM-dd 字串。這個格式可以直接比大小與排序，
 * 也是備份檔裡的格式，省掉一層轉換。
 */
private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

fun todayDate(): String = LocalDate.now().format(ISO)

fun shiftDate(date: String, days: Long): String = parseDate(date).plusDays(days).format(ISO)

fun parseDate(date: String): LocalDate = try {
    LocalDate.parse(date, ISO)
} catch (t: Throwable) {
    LocalDate.now()
}

fun isValidDate(date: String): Boolean = try {
    LocalDate.parse(date, ISO)
    true
} catch (t: Throwable) {
    false
}

/** 匯出檔名用的 yyyyMMdd */
fun compactToday(): String = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))

/** 幾天前（含今天）的日期，用來畫「最近 90 天」這種區間 */
fun daysAgo(days: Long): String = LocalDate.now().minusDays(days).format(ISO)

fun nowTime(): LocalTime = LocalTime.now()
