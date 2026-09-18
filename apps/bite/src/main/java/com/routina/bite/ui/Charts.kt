package com.routina.bite.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import com.routina.bite.data.DateSpan
import com.routina.bite.data.parseDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * 三張圖共用的 Canvas 繪圖基礎。這裡只管畫，不認識 DiaryEntry 也不認識體重——
 * 畫面先把資料換算成 [Mark]（日曆位置 + 值）再丟進來。
 *
 * 配色由呼叫端帶入（都是 MaterialTheme 的角色色）：長條用容器色、飽和色只給細線與點，
 * 選取態只把填色加深，不加邊框也不加陰影。
 */

/** 軸上的一個值。[index] 是日曆位置，0 是區間第一天 */
data class Mark(val index: Int, val value: Double)

/** y 軸的範圍與刻度 */
data class Axis(val min: Double, val max: Double, val ticks: List<Double>)

/** 資料區的座標換算 */
class Plot(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val axis: Axis,
    val dayCount: Int
) {
    /** 一天佔的寬度 */
    val slot: Float get() = (right - left) / dayCount.coerceAtLeast(1)

    fun cx(index: Int): Float = left + slot * (index + 0.5f)

    fun y(value: Double): Float {
        val height = (axis.max - axis.min).takeIf { it > 0.0 } ?: 1.0
        return bottom - ((value - axis.min) / height).toFloat() * (bottom - top)
    }

    fun indexAt(x: Float): Int = floor((x - left) / slot).toInt()

    /** 用另一個軸的範圍換算 y，給體脂那條走右側刻度的線用 */
    fun yOn(other: Axis, value: Double): Float {
        val height = (other.max - other.min).takeIf { it > 0.0 } ?: 1.0
        return bottom - ((value - other.min) / height).toFloat() * (bottom - top)
    }
}

/**
 * 讓點按拿得到繪製時才算出來的座標換算（軸標籤要量過字才知道資料區從哪開始）。
 * 刻意不是 Compose state：在 draw 裡寫入不會觸發重組。
 */
class PlotHolder {
    var value: Plot? = null
}

private const val LABEL_GAP_DP = 6
private const val MIN_DATE_LABEL_DP = 40

private val shortDateFormat = DateTimeFormatter.ofPattern("M/d", Locale.TAIWAN)

fun formatShortDate(date: String): String = parseDate(date).format(shortDateFormat)

/** 刻度間隔取 1／2／5 × 10ⁿ，讀起來才是整數 */
private fun niceStep(raw: Double): Double {
    if (raw <= 0.0) return 1.0
    val magnitude = 10.0.pow(floor(log10(raw)))
    val normalized = raw / magnitude
    val factor = when {
        normalized <= 1.0 -> 1.0
        normalized <= 2.0 -> 2.0
        normalized <= 5.0 -> 5.0
        else -> 10.0
    }
    return factor * magnitude
}

/**
 * 依資料算出 y 軸。[zeroBased] 為 true 時一定從 0 起（熱量、營養素）；
 * 為 false 時依資料範圍上下各留一成（體重），這樣點不會貼著邊。
 */
fun axisFor(values: List<Double>, zeroBased: Boolean, tickCount: Int = 4): Axis {
    if (values.isEmpty()) return Axis(0.0, 1.0, listOf(0.0, 1.0))
    var low = if (zeroBased) 0.0 else values.min()
    var high = values.max().coerceAtLeast(low)
    if (!zeroBased) {
        val padding = (high - low) * 0.1
        low -= padding
        high += padding
    }
    // 只有一筆資料或全部同值時沒有高度可畫，撐開一點
    if (high - low < 1e-6) {
        high += 1.0
        if (!zeroBased) low -= 1.0
    }
    var step = niceStep((high - low) / tickCount)
    var min = floor(low / step) * step
    var max = ceil(high / step) * step
    // 外擴到整數刻度之後可能多出幾條，格線太密就換大一級的間隔
    while ((max - min) / step > 5.0 + 1e-6) {
        step = niceStep(step * 1.5)
        min = floor(low / step) * step
        max = ceil(high / step) * step
    }
    val ticks = generateSequence(min) { it + step }
        .takeWhile { it <= max + step / 100 }
        .toList()
    return Axis(min, max, ticks)
}

/** 日期標籤最少要 [minLabelPx] 寬，排不下就抽稀，寧可少標也不疊字 */
private fun labelStep(dayCount: Int, widthPx: Float, minLabelPx: Float): Int {
    val fits = (widthPx / minLabelPx).toInt().coerceAtLeast(1)
    return ceil(dayCount.toFloat() / fits).toInt().coerceAtLeast(1)
}

/**
 * 畫格線與兩軸標籤，回傳資料區的座標換算。
 * [rightAxis] 只有體重分頁（體脂）會用到，其餘傳 null 就不留右邊的空間。
 */
fun DrawScope.drawChartFrame(
    holder: PlotHolder,
    axis: Axis,
    span: DateSpan,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
    gridColor: Color,
    rightAxis: Axis? = null,
    rightLabel: (Double) -> String = { "" },
    leftLabel: (Double) -> String
): Plot {
    val gap = LABEL_GAP_DP.dp.toPx()
    val leftTexts = axis.ticks.map { measurer.measure(leftLabel(it), labelStyle) }
    val rightTexts = rightAxis?.ticks?.map { measurer.measure(rightLabel(it), labelStyle) }
    val dateHeight = measurer.measure("9/9", labelStyle).size.height.toFloat()

    val plot = Plot(
        left = leftTexts.maxOf { it.size.width }.toFloat() + gap,
        top = 4.dp.toPx(),
        right = size.width - (rightTexts?.maxOf { it.size.width }?.toFloat()?.plus(gap) ?: 0f),
        bottom = size.height - dateHeight - gap,
        axis = axis,
        dayCount = span.dayCount
    )
    holder.value = plot

    axis.ticks.forEachIndexed { index, tick ->
        val y = plot.y(tick)
        drawLine(gridColor, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1.dp.toPx())
        val text = leftTexts[index]
        drawText(text, topLeft = Offset(plot.left - gap - text.size.width, y - text.size.height / 2f))
    }

    if (rightAxis != null && rightTexts != null) {
        rightAxis.ticks.forEachIndexed { index, tick ->
            val text = rightTexts[index]
            // 右軸用自己的範圍換算，不能借用 plot.y
            val ratio = (tick - rightAxis.min) / (rightAxis.max - rightAxis.min).coerceAtLeast(1e-6)
            val y = plot.bottom - ratio.toFloat() * (plot.bottom - plot.top)
            drawText(text, topLeft = Offset(plot.right + gap, y - text.size.height / 2f))
        }
    }

    val step = labelStep(span.dayCount, plot.right - plot.left, MIN_DATE_LABEL_DP.dp.toPx())
    var day = 0
    while (day < span.dayCount) {
        val text = measurer.measure(formatShortDate(span.dateAt(day)), labelStyle)
        val x = (plot.cx(day) - text.size.width / 2f)
            .coerceIn(0f, (size.width - text.size.width).coerceAtLeast(0f))
        drawText(text, topLeft = Offset(x, plot.bottom + gap))
        day += step
    }
    return plot
}

/** 長條。靜止用容器色，選取的那一根只把填色加深 */
fun DrawScope.drawColumns(
    plot: Plot,
    marks: List<Mark>,
    fill: Color,
    selectedFill: Color,
    selectedIndex: Int?
) {
    val width = (plot.slot * 0.7f).coerceIn(1f, 14.dp.toPx())
    val radius = CornerRadius((width / 2f).coerceAtMost(2.dp.toPx()))
    marks.forEach { mark ->
        val top = plot.y(mark.value)
        drawRoundRect(
            color = if (mark.index == selectedIndex) selectedFill else fill,
            topLeft = Offset(plot.cx(mark.index) - width / 2f, top),
            size = Size(width, (plot.bottom - top).coerceAtLeast(1f)),
            cornerRadius = radius
        )
    }
}

/** 折線。[marks] 裡的 null 代表那個位置沒有值，線在那裡斷開 */
fun DrawScope.drawSeriesLine(
    plot: Plot,
    marks: List<Mark?>,
    color: Color,
    strokeDp: Float,
    dashed: Boolean = false,
    yOf: (Double) -> Float = plot::y
) {
    val stroke = strokeDp.dp.toPx()
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(stroke * 3, stroke * 3)) else null
    // 只有一個值時沒有線可畫，畫成一個點，不然那張圖看起來像沒資料
    val only = marks.singleOrNull { it != null }
    if (only != null) {
        drawCircle(color, stroke, Offset(plot.cx(only.index), yOf(only.value)))
        return
    }
    var previous: Mark? = null
    marks.forEach { mark ->
        val from = previous
        if (mark != null && from != null) {
            drawLine(
                color = color,
                start = Offset(plot.cx(from.index), yOf(from.value)),
                end = Offset(plot.cx(mark.index), yOf(mark.value)),
                strokeWidth = stroke,
                pathEffect = effect,
                cap = StrokeCap.Round
            )
        }
        previous = mark
    }
}

/** [hollow] 的點是空心圓（用 surface 填再描邊），拿來區分體重的兩種來源 */
fun DrawScope.drawDataPoint(
    center: Offset,
    color: Color,
    surface: Color,
    radiusPx: Float,
    hollow: Boolean
) {
    if (hollow) {
        drawCircle(surface, radiusPx, center)
        drawCircle(color, radiusPx, center, style = Stroke(1.5.dp.toPx()))
    } else {
        drawCircle(color, radiusPx, center)
    }
}

/** 目標線：中性色虛線，數值標在右端上方 */
fun DrawScope.drawTargetLine(
    plot: Plot,
    value: Double,
    color: Color,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
    label: String
) {
    if (value < plot.axis.min || value > plot.axis.max) return
    val y = plot.y(value)
    val stroke = 1.dp.toPx()
    drawLine(
        color = color,
        start = Offset(plot.left, y),
        end = Offset(plot.right, y),
        strokeWidth = stroke,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 4, stroke * 4))
    )
    val text = measurer.measure(label, labelStyle)
    drawText(
        text,
        topLeft = Offset(plot.right - text.size.width, y - text.size.height - 2.dp.toPx())
    )
}

/** 折線圖的選取指示：一條中性色的垂直參考線，不用顏色也不加陰影 */
fun DrawScope.drawSelectionGuide(plot: Plot, index: Int, color: Color) {
    val x = plot.cx(index)
    drawLine(color, Offset(x, plot.top), Offset(x, plot.bottom), strokeWidth = 1.dp.toPx())
}

/** 點下去之後找最接近的那一天。差超過 [tolerance] 天就當作沒點到 */
fun nearestIndex(marks: List<Mark?>, index: Int, tolerance: Int = 1): Int? =
    marks.filterNotNull()
        .minByOrNull { abs(it.index - index) }
        ?.takeIf { abs(it.index - index) <= tolerance }
        ?.index
