# bite-charts

## ADDED Requirements

### Requirement: Charts screen and entry points
The system SHALL provide a Charts screen at route `charts?tab={tab}` with three in-page tabs —
熱量, 體重 and 營養素 — where `tab` accepts `kcal`, `weight` and `macros` and defaults to `kcal`.
The History screen and the Weight screen SHALL each offer a chart icon in their top app bar that opens it;
the Weight screen's icon SHALL land on the 體重 tab. The Today screen SHALL NOT gain an entry point.

#### Scenario: Open from history
- **WHEN** the user taps the chart icon on the History screen
- **THEN** the Charts screen opens on the 熱量 tab

#### Scenario: Open from weight
- **WHEN** the user taps the chart icon on the Weight screen
- **THEN** the Charts screen opens on the 體重 tab

#### Scenario: Back
- **WHEN** the user taps back on the Charts screen
- **THEN** the previous screen is restored

### Requirement: Range selection
The Charts screen SHALL offer four ranges — 7 天, 30 天, 90 天 and 全部 — as a single selector
shared by all three tabs. 7/30/90 SHALL end at today and cover that many calendar days including today.
全部 SHALL span the earliest to the latest date that the chart in view has data for, not to today.
Switching tabs SHALL NOT change the selected range.

#### Scenario: Range applies to every tab
- **WHEN** the user selects 90 天 on the 熱量 tab and switches to the 營養素 tab
- **THEN** the 營養素 tab shows the same 90 天 range and the selector still reads 90 天

#### Scenario: 全部 follows the chart in view
- **WHEN** the user selects 全部 and diary entries end on 2025-10-13 while weight readings run to 2026-09-02
- **THEN** the 熱量 tab stops at 2025-10-13 and the 體重 tab runs to 2026-09-02

### Requirement: Empty default range falls back to 全部
The Charts screen SHALL open on 30 天. When the screen opens and neither diary entries nor weight
entries exist within that range, it SHALL select 全部 instead and display a notice naming the reason.
The fallback SHALL happen only when the screen opens; a range the user picks afterwards SHALL be honoured
even when it is empty.

#### Scenario: All data is older than 30 days
- **WHEN** the user opens Charts and the newest record is 2025-10-13 while today is 2026-09-19
- **THEN** the selected range is 全部 and a notice reads 「最近 30 天沒有紀錄，顯示全部」

#### Scenario: Recent data exists
- **WHEN** the user opens Charts and at least one diary or weight entry falls in the last 30 days
- **THEN** the selected range is 30 天 and no notice is shown

#### Scenario: User picks an empty range
- **WHEN** the user selects 7 天 and that range holds no records
- **THEN** the range stays 7 天 and the tab shows its empty state

### Requirement: Empty state instead of an empty chart
When the selected range holds no data for a chart, the system SHALL replace that chart with explanatory
text and SHALL NOT draw axes, gridlines or an empty plot area.

#### Scenario: No diary entries in range
- **WHEN** the 熱量 tab's range holds no diary entries
- **THEN** the tab shows text saying there is nothing to chart and draws no axes

#### Scenario: No body-fat readings in range
- **WHEN** the 體重 tab's range holds weight entries but none carries a body-fat percentage
- **THEN** the weight line is drawn and neither the body-fat line nor the right-hand scale appears

### Requirement: Calories tab
The 熱量 tab SHALL draw one column per logged day of total kilocalories on a y axis starting at 0,
overlaid with a dashed target line labelled with the target value and with a 7-day moving average line.
Below the chart it SHALL summarise the range average, maximum, minimum and the number of days at or under
target expressed as a count over the number of logged days.

#### Scenario: Target line
- **WHEN** the target is 2000 kcal
- **THEN** a dashed horizontal line sits at 2000 on the y axis and carries the label 2000

#### Scenario: Moving average needs enough days
- **WHEN** a day's preceding 7 calendar days contain fewer than 4 logged days
- **THEN** no moving-average point is drawn for that day and the line breaks there

#### Scenario: On-target count
- **WHEN** the range holds 23 logged days of which 18 are at or under target
- **THEN** the summary reads 18 / 23

### Requirement: Weight tab
The 體重 tab SHALL draw a weight line whose y axis is scaled to the data rather than starting at 0,
with the lowest and highest tick labelled. Each reading SHALL be marked with a point whose shape encodes
its source — filled for 家用體重計, hollow for InBody — and a legend SHALL explain both shapes.
Readings that carry a body-fat percentage SHALL additionally form a dashed body-fat line on a right-hand
scale. Below the chart it SHALL summarise the change over the range and the first and last value with
their dates.

#### Scenario: Axis is not zero based
- **WHEN** readings span 81.2 kg to 84.6 kg
- **THEN** the y axis covers roughly that band and its lowest and highest ticks are labelled

#### Scenario: Sources are distinguishable
- **WHEN** the range holds both 家用體重計 and InBody readings
- **THEN** the two appear as filled and hollow points and the legend names which is which

#### Scenario: Change over range
- **WHEN** the first reading in range is 84.6 kg and the last is 81.4 kg
- **THEN** the summary reads -3.2 公斤 together with both values and dates

### Requirement: Macros tab
The 營養素 tab SHALL draw three separate small line charts — 蛋白質, 脂肪 and 碳水 — using `MacroColors`,
each with its own y axis starting at 0 and a target line only when that macro has a target.
Below them it SHALL summarise the range's average daily grams for each macro and the number of days
that reached the protein target over the number of logged days.

#### Scenario: Macro without a target
- **WHEN** the fat target is unset
- **THEN** the 脂肪 chart is drawn with no target line

#### Scenario: Protein on-target count
- **WHEN** the range holds 23 logged days of which 9 reached the protein target
- **THEN** the summary reads 9 / 23

### Requirement: Axes stay legible
Every chart SHALL label its y axis with at most six ticks spaced at 1, 2 or 5 times a power of ten, and SHALL
label its x axis with dates thinned to the available width so that labels never overlap.

#### Scenario: Long range
- **WHEN** the range covers 56 calendar days
- **THEN** only a subset of dates is labelled and no two labels overlap

### Requirement: Tap a day to read its value
Tapping a column or a data point SHALL select that day and show its date and value as a line of text below
the chart. Selection SHALL be shown by deepening the selected mark within the neutral colour ramp, without
a floating tooltip. Changing tab or range SHALL clear the selection.

#### Scenario: Select a day
- **WHEN** the user taps the column for 2025-10-13 on the 熱量 tab
- **THEN** the text below reads 「10月13日：2193 大卡」 and that column is drawn deeper

#### Scenario: Selection is cleared
- **WHEN** the user changes the range after selecting a day
- **THEN** no day is selected and the text returns to its prompt

### Requirement: Days without records are absent, not zero
Charts SHALL place days on a real calendar axis and SHALL draw nothing for a day that has no record.
No statistic SHALL treat a missing day as a zero: averages, extremes and on-target counts SHALL be computed
over logged days only. A weight line MAY connect two readings across a gap.

#### Scenario: Gap in the diary
- **WHEN** the range covers 56 calendar days of which 31 hold entries
- **THEN** 31 columns are drawn, the gaps stay blank, and the average divides by 31

### Requirement: Trend statistics are pure functions
Range filtering, per-day totals, moving averages, extremes and on-target counts SHALL live in
`data/Trends.kt` as pure functions taking `List<DiaryEntry>`, `List<WeightEntry>` and `Targets`
and returning plain data, with no dependency on Compose or on the repository.

#### Scenario: Same input, same output
- **WHEN** a trend function is called twice with the same lists
- **THEN** it returns equal results and touches no other state

### Requirement: Visual rules
Charts SHALL keep saturated colour to thin lines, points and legend swatches, drawing columns in the
surface-variant container tone. Traditional Chinese text SHALL NOT use `FontWeight.SemiBold` or heavier
and SHALL use `letterSpacing = 0.sp`; numerals MAY use SemiBold. Cards SHALL use a 12dp corner radius
and SHALL NOT combine fill, a darkened border and a resting shadow. Spacing SHALL come from the
4/8/12/16/24/32 dp scale with at least a 1:2 ratio between within-group and between-group spacing.
Categorical colour SHALL be limited to the theme primary and the three `MacroColors`.

#### Scenario: Column colour
- **WHEN** a calories column is drawn at rest
- **THEN** it is filled with the surface-variant tone, not with a saturated colour

#### Scenario: Selected column
- **WHEN** a column is selected
- **THEN** only its fill deepens; no border and no shadow are added
