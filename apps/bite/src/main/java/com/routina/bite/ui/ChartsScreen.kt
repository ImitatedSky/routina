package com.routina.bite.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.routina.bite.R
import com.routina.bite.data.ChartRange
import com.routina.bite.data.DateSpan
import com.routina.bite.data.DayTotal
import com.routina.bite.data.WeightPoint
import com.routina.bite.data.dailyTotals
import com.routina.bite.data.initialRange
import com.routina.bite.data.kcalSummary
import com.routina.bite.data.macroSummary
import com.routina.bite.data.movingAverage
import com.routina.bite.data.spanOf
import com.routina.bite.data.weightSeries
import com.routina.bite.data.weightSummary
import com.routina.bite.model.DiaryEntry
import com.routina.bite.model.Nutrients
import com.routina.bite.model.Targets
import com.routina.bite.model.WeightEntry
import com.routina.bite.model.WeightSource
import com.routina.bite.ui.theme.MacroColors
import java.util.Locale

enum class ChartTab { KCAL, WEIGHT, MACROS }

private val CHART_HEIGHT = 200.dp
private val MINI_CHART_HEIGHT = 110.dp

/** 繁中不留字距：M3 的預設 letterSpacing 是給拉丁字母的，中文會看起來鬆散 */
private fun TextStyle.zh(): TextStyle = copy(letterSpacing = 0.sp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(
    viewModel: BiteViewModel,
    initialTab: ChartTab,
    onBack: () -> Unit
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val weights by viewModel.weights.collectAsStateWithLifecycle()
    val targets by viewModel.targets.collectAsStateWithLifecycle()

    var tab by remember { mutableStateOf(initialTab) }
    // null = 還沒選過，用 initialRange 算出來的預設值（區間是空的就退回全部）
    var picked by remember { mutableStateOf<ChartRange?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }

    val range = picked ?: initialRange(entries, weights)
    val fellBack = picked == null && range == ChartRange.ALL

    LaunchedEffect(tab, range) { selected = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.charts_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                ChartTab.entries.forEach { entry ->
                    Tab(
                        selected = entry == tab,
                        onClick = { tab = entry },
                        text = {
                            Text(
                                text = stringResource(tabTitle(entry)),
                                style = MaterialTheme.typography.titleSmall.zh()
                            )
                        }
                    )
                }
            }
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                RangeRow(
                    range = range,
                    fellBack = fellBack,
                    onPick = { picked = it }
                )
                Spacer(modifier = Modifier.height(24.dp))
                when (tab) {
                    ChartTab.KCAL -> KcalTab(entries, targets, range, selected) { selected = it }
                    ChartTab.WEIGHT -> WeightTab(weights, range, selected) { selected = it }
                    ChartTab.MACROS -> MacroTab(entries, targets, range, selected) { selected = it }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

private fun tabTitle(tab: ChartTab): Int = when (tab) {
    ChartTab.KCAL -> R.string.charts_tab_kcal
    ChartTab.WEIGHT -> R.string.charts_tab_weight
    ChartTab.MACROS -> R.string.charts_tab_macros
}

@Composable
private fun RangeRow(range: ChartRange, fellBack: Boolean, onPick: (ChartRange) -> Unit) {
    val labels = listOf(
        ChartRange.DAYS_7 to R.string.charts_range_7,
        ChartRange.DAYS_30 to R.string.charts_range_30,
        ChartRange.DAYS_90 to R.string.charts_range_90,
        ChartRange.ALL to R.string.charts_range_all
    )
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEach { (value, label) ->
                FilterChip(
                    selected = value == range,
                    onClick = { onPick(value) },
                    label = {
                        Text(
                            text = stringResource(label),
                            style = MaterialTheme.typography.labelLarge.zh()
                        )
                    }
                )
            }
        }
        if (fellBack) {
            Text(
                text = stringResource(R.string.charts_range_fallback),
                style = MaterialTheme.typography.bodySmall.zh(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

// ---------- 熱量 ----------

@Composable
private fun KcalTab(
    entries: List<DiaryEntry>,
    targets: Targets,
    range: ChartRange,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    val span = spanOf(range, entries.map { it.date })
    val days = span?.let { dailyTotals(entries, it) }.orEmpty()
    if (span == null || days.isEmpty()) {
        EmptyChart(R.string.charts_empty_diary)
        return
    }

    val marks = days.map { Mark(span.indexOf(it.date), it.total.kcal) }
    val average = movingAverage(days) { it.kcal }
        .map { value -> value?.let { Mark(span.indexOf(it.date), it.value) } }
    val axis = axisFor(marks.map { it.value } + targets.kcal.toDouble(), zeroBased = true)

    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
        .copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val columnFill = MaterialTheme.colorScheme.surfaceVariant
    val selectedFill = MaterialTheme.colorScheme.onSurfaceVariant
    val averageColor = MaterialTheme.colorScheme.primary
    val targetColor = MaterialTheme.colorScheme.outline
    val targetLabel = stringResource(R.string.charts_target_line, targets.kcal)
    val selectedIndex = span.indexIn(selected)

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ChartCanvas(
            height = CHART_HEIGHT,
            onTapIndex = { index ->
                onSelect(nearestIndex(marks, index)?.let { span.dateAt(it) })
            }
        ) { holder ->
            val plot = drawChartFrame(holder, axis, span, measurer, labelStyle, grid) {
                formatKcal(it).toString()
            }
            drawColumns(plot, marks, columnFill, selectedFill, selectedIndex)
            drawTargetLine(
                plot, targets.kcal.toDouble(), targetColor, measurer,
                labelStyle.zh(), targetLabel
            )
            drawSeriesLine(plot, average, averageColor, strokeDp = 2f)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendItem(stringResource(R.string.charts_legend_average), averageColor, LegendMark.LINE)
            LegendItem(stringResource(R.string.charts_legend_target), targetColor, LegendMark.DASHED)
        }
        Spacer(modifier = Modifier.height(8.dp))
        SelectionRow(
            text = days.firstOrNull { it.date == selected }?.let {
                stringResource(R.string.charts_pick_kcal, formatDayLabel(it.date), formatKcal(it.total.kcal))
            }
        )
        Spacer(modifier = Modifier.height(24.dp))

        val summary = kcalSummary(days, targets.kcal)
        SummaryCard(
            listOf(
                SummaryRow(stringResource(R.string.charts_kcal_average), formatKcal(summary.average).toString()),
                SummaryRow(
                    label = stringResource(R.string.charts_kcal_highest),
                    value = summary.highest?.let { formatKcal(it.value).toString() }.orEmpty(),
                    note = summary.highest?.let { formatShortDate(it.date) }
                ),
                SummaryRow(
                    label = stringResource(R.string.charts_kcal_lowest),
                    value = summary.lowest?.let { formatKcal(it.value).toString() }.orEmpty(),
                    note = summary.lowest?.let { formatShortDate(it.date) }
                ),
                SummaryRow(
                    label = stringResource(R.string.charts_on_target),
                    value = stringResource(
                        R.string.charts_days_ratio,
                        summary.onTargetDays,
                        summary.loggedDays
                    )
                )
            )
        )
    }
}

// ---------- 體重 ----------

@Composable
private fun WeightTab(
    weights: List<WeightEntry>,
    range: ChartRange,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    val span = spanOf(range, weights.map { it.date })
    val points = span?.let { weightSeries(weights, it) }.orEmpty()
    if (span == null || points.isEmpty()) {
        EmptyChart(R.string.charts_empty_weight)
        return
    }

    val marks = points.map { Mark(span.indexOf(it.date), it.kg) }
    val axis = axisFor(points.map { it.kg }, zeroBased = false)
    val fatPoints = points.mapNotNull { point ->
        point.bodyFatPct?.let { Mark(span.indexOf(point.date), it) }
    }
    val fatAxis = if (fatPoints.isEmpty()) null
    else axisFor(fatPoints.map { it.value }, zeroBased = false)

    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
        .copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val lineColor = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant
    val fatColor = MacroColors.Fat
    val selectedIndex = span.indexIn(selected)

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ChartCanvas(
            height = CHART_HEIGHT,
            onTapIndex = { index ->
                onSelect(nearestIndex(marks, index)?.let { span.dateAt(it) })
            }
        ) { holder ->
            val plot = drawChartFrame(
                holder = holder,
                axis = axis,
                span = span,
                measurer = measurer,
                labelStyle = labelStyle,
                gridColor = grid,
                rightAxis = fatAxis,
                rightLabel = { formatGrams(it) },
                leftLabel = { formatGrams(it) }
            )
            if (selectedIndex != null) drawSelectionGuide(plot, selectedIndex, guideColor)
            drawSeriesLine(plot, marks, lineColor, strokeDp = 2f)
            if (fatAxis != null) {
                drawSeriesLine(
                    plot = plot,
                    marks = fatPoints,
                    color = fatColor,
                    strokeDp = 1.5f,
                    dashed = true,
                    yOf = { plot.yOn(fatAxis, it) }
                )
            }
            // 點畫在線之後，兩種來源用實心／空心區分（不是用顏色，色盲條件下也讀得出來）
            points.forEach { point ->
                drawDataPoint(
                    center = Offset(plot.cx(span.indexOf(point.date)), plot.y(point.kg)),
                    color = lineColor,
                    surface = surface,
                    radiusPx = 4.dp.toPx(),
                    hollow = point.source == WeightSource.INBODY
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendItem(stringResource(R.string.source_home_scale), lineColor, LegendMark.DOT)
            LegendItem(stringResource(R.string.source_inbody), lineColor, LegendMark.HOLLOW_DOT)
            if (fatAxis != null) {
                LegendItem(stringResource(R.string.charts_legend_body_fat), fatColor, LegendMark.DASHED)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        SelectionRow(text = points.firstOrNull { it.date == selected }?.let { pickedWeightLabel(it) })
        Spacer(modifier = Modifier.height(24.dp))

        val summary = weightSummary(points)
        if (summary != null) {
            SummaryCard(
                listOf(
                    SummaryRow(
                        stringResource(R.string.charts_weight_change),
                        String.format(Locale.TAIWAN, "%+.1f", summary.changeKg)
                    ),
                    SummaryRow(
                        label = stringResource(R.string.charts_weight_first),
                        value = formatGrams(summary.first.kg),
                        note = formatShortDate(summary.first.date)
                    ),
                    SummaryRow(
                        label = stringResource(R.string.charts_weight_last),
                        value = formatGrams(summary.last.kg),
                        note = formatShortDate(summary.last.date)
                    )
                )
            )
        }
    }
}

@Composable
private fun pickedWeightLabel(point: WeightPoint): String {
    val day = formatDayLabel(point.date)
    val fat = point.bodyFatPct
    return if (fat == null) {
        stringResource(R.string.charts_pick_weight, day, formatGrams(point.kg))
    } else {
        stringResource(R.string.charts_pick_weight_fat, day, formatGrams(point.kg), formatGrams(fat))
    }
}

// ---------- 營養素 ----------

@Composable
private fun MacroTab(
    entries: List<DiaryEntry>,
    targets: Targets,
    range: ChartRange,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    val span = spanOf(range, entries.map { it.date })
    val days = span?.let { dailyTotals(entries, it) }.orEmpty()
    if (span == null || days.isEmpty()) {
        EmptyChart(R.string.charts_empty_diary)
        return
    }

    val picked = days.firstOrNull { it.date == selected }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        MacroMiniChart(
            title = stringResource(R.string.macro_protein),
            color = MacroColors.Protein,
            span = span,
            days = days,
            target = targets.protein,
            selected = selected,
            onSelect = onSelect
        ) { it.protein }
        Spacer(modifier = Modifier.height(12.dp))
        MacroMiniChart(
            title = stringResource(R.string.macro_fat),
            color = MacroColors.Fat,
            span = span,
            days = days,
            target = targets.fat,
            selected = selected,
            onSelect = onSelect
        ) { it.fat }
        Spacer(modifier = Modifier.height(12.dp))
        MacroMiniChart(
            title = stringResource(R.string.macro_carbs),
            color = MacroColors.Carbs,
            span = span,
            days = days,
            target = targets.carbs,
            selected = selected,
            onSelect = onSelect
        ) { it.carbs }

        Spacer(modifier = Modifier.height(8.dp))
        SelectionRow(
            text = picked?.let {
                stringResource(
                    R.string.charts_pick_macros,
                    formatDayLabel(it.date),
                    formatGrams(it.total.protein),
                    formatGrams(it.total.fat),
                    formatGrams(it.total.carbs)
                )
            }
        )
        Spacer(modifier = Modifier.height(24.dp))

        val summary = macroSummary(days, targets)
        SummaryCard(
            listOf(
                SummaryRow(
                    stringResource(R.string.charts_macro_average, stringResource(R.string.macro_protein)),
                    formatGrams(summary.protein)
                ),
                SummaryRow(
                    stringResource(R.string.charts_macro_average, stringResource(R.string.macro_fat)),
                    formatGrams(summary.fat)
                ),
                SummaryRow(
                    stringResource(R.string.charts_macro_average, stringResource(R.string.macro_carbs)),
                    formatGrams(summary.carbs)
                ),
                SummaryRow(
                    stringResource(R.string.charts_protein_on_target),
                    stringResource(
                        R.string.charts_days_ratio,
                        summary.proteinOnTargetDays,
                        summary.loggedDays
                    )
                )
            )
        )
    }
}

/** 三個營養素各一張小圖（small multiples）。堆疊長條的中間段讀不出來，而且要三個飽和色鋪滿整張圖 */
@Composable
private fun MacroMiniChart(
    title: String,
    color: Color,
    span: DateSpan,
    days: List<DayTotal>,
    target: Int?,
    selected: String?,
    onSelect: (String?) -> Unit,
    value: (Nutrients) -> Double
) {
    val marks = days.map { Mark(span.indexOf(it.date), value(it.total)) }
    val axis = axisFor(
        marks.map { it.value } + listOfNotNull(target?.toDouble()),
        zeroBased = true
    )

    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
        .copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant
    val targetColor = MaterialTheme.colorScheme.outline
    val targetLabel = target?.let { stringResource(R.string.charts_target_line, it) }
    val selectedIndex = span.indexIn(selected)

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        LegendSwatch(color, LegendMark.LINE)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.zh(),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
    ChartCanvas(
        height = MINI_CHART_HEIGHT,
        onTapIndex = { index -> onSelect(nearestIndex(marks, index)?.let { span.dateAt(it) }) }
    ) { holder ->
        val plot = drawChartFrame(holder, axis, span, measurer, labelStyle, grid) {
            formatKcal(it).toString()
        }
        if (selectedIndex != null) drawSelectionGuide(plot, selectedIndex, guideColor)
        if (target != null && targetLabel != null) {
            drawTargetLine(plot, target.toDouble(), targetColor, measurer, labelStyle.zh(), targetLabel)
        }
        drawSeriesLine(plot, marks, color, strokeDp = 2f)
    }
}

// ---------- 共用零件 ----------

/** [draw] 收到的 holder 要交給 drawChartFrame，點按時才換算得回日期 */
@Composable
private fun ChartCanvas(
    height: Dp,
    onTapIndex: (Int) -> Unit,
    draw: DrawScope.(PlotHolder) -> Unit
) {
    val holder = remember { PlotHolder() }
    val currentTap by rememberUpdatedState(onTapIndex)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    holder.value?.let { currentTap(it.indexAt(offset.x)) }
                }
            }
    ) {
        draw(holder)
    }
}

@Composable
private fun EmptyChart(textId: Int) {
    Text(
        text = stringResource(textId),
        style = MaterialTheme.typography.bodyMedium.zh(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

/** 保底一行的高度，沒選取時版面不會塌下去 */
@Composable
private fun SelectionRow(text: String?) {
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp), contentAlignment = Alignment.CenterStart) {
        Text(
            text = text ?: stringResource(R.string.charts_tap_hint),
            style = MaterialTheme.typography.bodyMedium.zh(),
            color = if (text == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

private data class SummaryRow(val label: String, val value: String, val note: String? = null)

@Composable
private fun SummaryCard(rows: List<SummaryRow>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            rows.forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.bodyMedium.zh(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        // 數字走 Roboto，有真字重可以用 SemiBold；中文不行（會被合成假粗）
                        Text(
                            text = row.value,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        row.note?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class LegendMark { LINE, DASHED, DOT, HOLLOW_DOT }

@Composable
private fun LegendItem(label: String, color: Color, mark: LegendMark) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        LegendSwatch(color, mark)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.zh(),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LegendSwatch(color: Color, mark: LegendMark) {
    val surface = MaterialTheme.colorScheme.surface
    Canvas(modifier = Modifier.size(12.dp)) {
        val middle = size.height / 2f
        when (mark) {
            LegendMark.LINE -> drawLine(
                color, Offset(0f, middle), Offset(size.width, middle), strokeWidth = 2.dp.toPx()
            )

            LegendMark.DASHED -> drawLine(
                color, Offset(0f, middle), Offset(size.width, middle),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
            )

            LegendMark.DOT -> drawDataPoint(
                Offset(size.width / 2f, middle), color, surface, 4.dp.toPx(), hollow = false
            )

            LegendMark.HOLLOW_DOT -> drawDataPoint(
                Offset(size.width / 2f, middle), color, surface, 4.dp.toPx(), hollow = true
            )
        }
    }
}
