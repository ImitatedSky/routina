# Tasks: add-bite-charts

## 1. 繪圖方式評估

- [ ] 1.1 把 `com.patrykandpatrick.vico:compose-m3:3.3.1` 加進 `libs.versions.toml` 與 `:apps:bite`，確認相依解析與 `compileDebugKotlin` 是否通過
- [ ] 1.2 用 Vico 寫一個接近熱量分頁的試作（自訂長條色、目標線 decoration、日期 formatter），記錄需要覆寫多少預設值
- [ ] 1.3 `./gradlew :apps:bite:dependencies --configuration releaseRuntimeClasspath` 看它帶進來的相依圖
- [ ] 1.4 依 1.1–1.3 的結果決定，把理由寫進 design D1，還原評估用的改動

## 2. 統計純函式 `data/Trends.kt`

- [ ] 2.1 `Range` enum（`DAYS_7`／`DAYS_30`／`DAYS_90`／`ALL`）與 `rangeOf()`：算出區間的起訖日期；`ALL` 取所有紀錄的最早到最晚（D3）
- [ ] 2.2 `dailyKcal()`／`dailyMacros()`：把 `List<DiaryEntry>` 依日期合計，**只回有紀錄的日子**，依日期排序（D2）
- [ ] 2.3 `movingAverage()`：以當天為右端的 7 個日曆天，區間內有紀錄的日子 ≥ 4 天才給值，否則 null（D2、Risks）
- [ ] 2.4 `kcalSummary()`：平均、最高（含日期）、最低（含日期）、達標天數／有紀錄天數
- [ ] 2.5 `weightSeries()`／`weightSummary()`：區間內的體重點（含來源與體脂）、起訖值與日期、變化量
- [ ] 2.6 `macroSummary()`：各營養素平均每日公克、蛋白質達標天數／有紀錄天數
- [ ] 2.7 `hasDataIn()`：判斷某區間內飲食或體重任一有資料，給 D3 的退回用

## 3. Canvas 繪圖基礎 `ui/Charts.kt`

- [ ] 3.1 `niceStep()` 與 y 軸刻度產生：1／2／5 × 10ⁿ、最多 5 條；`max == min` 與全 0 的分支（D9）
- [ ] 3.2 x 軸日期抽稀：依可用寬度與 40dp 標籤寬算 step，`M/d` 格式（D9）
- [ ] 3.3 座標換算與畫布內距（左邊留 y 軸標籤、下方留 x 軸標籤）、格線（`outlineVariant` 1dp）
- [ ] 3.4 長條繪製：容器色 `surfaceVariant`、選取態 `onSurfaceVariant`、圓角、無邊框無陰影（D4）
- [ ] 3.5 折線繪製：實線與虛線兩種、斷點不連線（值為 null 就斷開）
- [ ] 3.6 點繪製：實心圓與空心圓（D8）
- [ ] 3.7 目標線：`outline` 1dp 虛線加右端數值標籤
- [ ] 3.8 選取命中測試：`detectTapGestures` 把 x 換算回日曆 index，取最近的有紀錄日（容差 1 天）（D10）
- [ ] 3.9 文字繪製一律走 `rememberTextMeasurer()` + MaterialTheme typography，不自訂 sp

## 4. 畫面 `ui/ChartsScreen.kt`

- [ ] 4.1 Scaffold + TopAppBar（返回）+ TabRow 三個分頁；`initialTab` 參數決定進來落在哪一頁
- [ ] 4.2 區間 FilterChip 列（三分頁共用）、開畫面時的空區間退回與說明文字（D3）
- [ ] 4.3 選取態：固定高度的一行文字，沒選取時顯示提示；換分頁或換區間清掉（D10）
- [ ] 4.4 熱量分頁：長條圖 + 目標線 + 7 日移動平均；摘要卡（平均／最高／最低／達標天數）
- [ ] 4.5 體重分頁：折線（y 軸不從 0）、兩種來源的點形狀與圖例、體脂虛線與右側刻度（全無體脂時不畫）；摘要卡（變化量／起訖值與日期）
- [ ] 4.6 營養素分頁：三張 small multiples 折線（`MacroColors`），有目標才畫目標線；摘要卡（三個平均公克／蛋白質達標天數）
- [ ] 4.7 各分頁無資料時的空狀態文字，**不畫空座標軸**
- [ ] 4.8 間距套 D6（組內 4／8dp、組間 24dp、卡片內距 16／12dp、圓角 12dp）；中文不加字重、數字才 SemiBold（D5）

## 5. 接線

- [ ] 5.1 `MainActivity.kt` 加 `charts?tab={tab}` route（`defaultValue = "kcal"`），只加自己這一段
- [ ] 5.2 `ui/HistoryScreen.kt` TopAppBar 加圖表圖示 → `charts`
- [ ] 5.3 `ui/WeightScreen.kt` TopAppBar 加圖表圖示 → `charts?tab=weight`
- [ ] 5.4 `res/values/strings.xml` **檔尾**附加本次的字串，不改既有的；不動 `versionCode`／`versionName`

## 6. 驗證與提交

- [ ] 6.1 `./gradlew :apps:bite:assembleDebug` 綠燈
- [ ] 6.2 `./gradlew :apps:bite:assembleRelease` 綠燈（R8 minify 下）
- [ ] 6.3 用 `@Preview` 確認三個分頁的版面與空狀態，確認後移除 Preview，不留在正式程式碼
- [ ] 6.4 確認沒有動到 `TodayScreen.kt`、`Queries.kt`、`model/`、`BiteRepository.kt`、`AddFoodScreen.kt`、`FoodEditScreen.kt`、版號
- [ ] 6.5 刪掉 `visual-research-reference.md`；commit（英文、無 co-author），不 push、不 tag
