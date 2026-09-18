# bite-diary

## ADDED Requirements

### Requirement: Diary entry snapshot
The system SHALL store each diary entry with the local date, an optional meal, an optional food id,
the food name, servings, optional grams per serving and a copy of the per-serving nutrients taken at logging time.

#### Scenario: Food edited after logging
- **WHEN** the user changes a food's kcal after it was logged yesterday
- **THEN** yesterday's entry still shows the kcal it had when logged

### Requirement: Today screen totals
The Today screen SHALL show, for the selected date, consumed kcal, the kcal target, the remaining kcal
(target minus consumed, may be negative) and protein, fat and carbohydrate totals each against its target when set.

#### Scenario: Over target
- **WHEN** consumed kcal exceeds the target
- **THEN** remaining is shown as a negative number, not clamped to zero

#### Scenario: Macro target not set
- **WHEN** the fat target is null
- **THEN** the fat row shows the consumed grams without a target or progress

### Requirement: Meal sections
The Today screen SHALL group entries into 早餐, 午餐, 晚餐, 點心 in that order, and SHALL show a 未分餐 section
only when entries with a null meal exist for that date. Each section SHALL show its kcal subtotal.

#### Scenario: Imported day
- **WHEN** the selected date only has entries with `meal = null`
- **THEN** only the 未分餐 section is shown

### Requirement: Default meal by time of day
When adding an entry the system SHALL preselect the meal from the current time:
04:00–10:29 早餐, 10:30–14:29 午餐, 14:30–16:59 點心, 17:00–21:29 晚餐, otherwise 點心. The user MAY change it.

#### Scenario: Lunch time
- **WHEN** the user opens the add screen at 12:15
- **THEN** 午餐 is preselected

### Requirement: Log by servings or grams
The system SHALL accept a positive servings value, or a positive grams value when the food has grams per serving,
converting grams to servings as `grams / servingGrams`. The system SHALL store servings only.

#### Scenario: Grams input
- **WHEN** the user logs 150 g of a food with 100 g per serving
- **THEN** the entry is stored with servings 1.5

#### Scenario: Grams unavailable
- **WHEN** the selected food has no grams per serving
- **THEN** the grams input is not offered

#### Scenario: Non-positive amount
- **WHEN** the user enters 0 or a negative amount for a new entry
- **THEN** the add button is disabled

### Requirement: Quick add without a food
The system SHALL let the user add an entry with a name (default 快速輸入), kcal and optional protein, fat and carbs
without creating a food. Such entries have `foodId = null`.

#### Scenario: Quick add kcal only
- **WHEN** the user quick-adds 500 kcal
- **THEN** an entry named 快速輸入 with 500 kcal and zero macros is added to the selected meal

### Requirement: Add screen ordering
The add screen SHALL show 常吃 (top 12 food ids by entry count in the last 90 days) and 最近 (12 most recently logged
distinct food ids) above the search field, excluding quick-add entries and deleted foods.

#### Scenario: Frequently eaten food
- **WHEN** a food was logged 13 times in the last 90 days and others fewer
- **THEN** it appears first in 常吃

### Requirement: Edit and delete entries
The system SHALL let the user change an entry's servings and meal, and delete an entry via an overflow menu with confirmation.

#### Scenario: Move entry to another meal
- **WHEN** the user changes an entry's meal from 點心 to 晚餐
- **THEN** it appears under 晚餐 and the section subtotals update

### Requirement: Copy yesterday
The system SHALL offer 複製昨天 on the Today screen, copying every entry from the previous calendar day into the selected
date as new entries with fresh ids and the current timestamp.

#### Scenario: Yesterday empty
- **WHEN** the previous day has no entries
- **THEN** a message says 昨天沒有紀錄 and nothing is added

### Requirement: Day note
The system SHALL let the user attach one free-text note per date, shown on the Today screen and in history.

#### Scenario: Note saved
- **WHEN** the user enters "感冒日" for a date
- **THEN** the note shows on that date's Today screen and on its history row

### Requirement: Date navigation
The Today screen SHALL show the selected date with previous/next controls and a way to jump back to today.

#### Scenario: Browse backward
- **WHEN** the user taps previous twice
- **THEN** the screen shows the date two days earlier with that day's entries and totals

### Requirement: History list
The History screen SHALL list every date that has entries or a note, newest first, with total kcal, the target,
and the day note; tapping a row SHALL open that date on the Today screen.

#### Scenario: Open a past day
- **WHEN** the user taps 2025-09-15 in history
- **THEN** the Today screen shows 2025-09-15 with its entries and the note 感冒日
