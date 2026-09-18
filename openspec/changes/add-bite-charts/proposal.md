# Change: add-bite-charts

## Why

Bite v0.1 把「記一餐」做完了，但記完之後看不到任何趨勢：歷史頁是一列一列的日期與總熱量，
體重頁是一列一列的公斤數。使用者在減脂，他真正要回答的問題是「這兩週平均吃多少」「體重是不是在往下」
「蛋白質有沒有吃夠」——這三個問題都不是單日數字答得出來的，需要把時間軸畫出來。

`add-bite-app` 的 Non-goals 明確把圖表留給 v0.2，這個 change 就是那一波。

## What Changes

- **新畫面 `ChartsScreen`（路由 `charts?tab=`）**，頁內三個分頁（TabRow）：熱量、體重、營養素。
- **區間選擇**：7 天／30 天／90 天／全部，預設 30 天。
  **若預設區間內沒有任何資料，自動改用「全部」並在畫面上說明原因**——
  使用者匯入的紀錄是 2025-08-19 到 2025-10-13，而現在是 2026 年 9 月，
  「最近 30 天」對他是空的；預設值直接給空畫面等於把 App 做壞。
- **熱量分頁**：每日總熱量長條圖（y 軸從 0 起）＋目標線（標數值）＋7 日移動平均線；
  下方摘要為區間平均、最高、最低、達標天數（≤ 目標的天數／有紀錄天數）。
- **體重分頁**：體重折線圖（y 軸依資料範圍，不從 0 起，但刻度要標清楚）；
  家用體重計與 InBody 用實心／空心圓點區分並附圖例；有體脂率的日子另外疊一條體脂折線（右側刻度）；
  下方摘要為區間變化量、起訖值與日期。
- **營養素分頁**：蛋白質／脂肪／碳水三條小型折線（small multiples），沿用 `MacroColors`；
  下方摘要為區間平均每日各營養素公克數與蛋白質達標天數。
- **點選互動**：點長條／資料點會選取該日，在圖下方以一行文字顯示該日數值（不做浮動 tooltip）。
- **新檔 `data/Trends.kt`**：區間過濾、每日合計、移動平均、極值、達標天數，全部純函式。
- **圖表自己用 Compose `Canvas` 畫，不引入圖表相依**（理由見 design D1）。
- **入口**：`HistoryScreen` 與 `WeightScreen` 的 TopAppBar 各加一個圖表圖示；
  體重頁的入口直接落在體重分頁。**今日頁不動**——今日頁是「記錄」的地方，
  趨勢屬於「回顧」，掛在既有的兩個回顧頁才是對的位置。

## Non-goals（留給之後的版本）

- 週／月彙總視圖（把 30 天壓成 4 個長條）、自訂日期區間
- 匯出圖片、分享
- 營養素佔比圓餅（熱量三分法）、鈉／糖／膽固醇的趨勢
- 體重目標線與預測（使用者沒有設體重目標欄位）

## Capabilities

### New Capabilities
- `bite-charts`: 趨勢圖表頁——區間選擇與空區間退回、熱量／體重／營養素三個分頁的繪圖與摘要、
  點選看單日數值、趨勢統計純函式、兩個入口

### Modified Capabilities
（`openspec/specs/` 目前是空的，`add-bite-app` 尚未 archive，無既有 spec 可修改。
`bite-diary` 與 `bite-weight-log` 的既有需求都沒有改，只是多了兩個 TopAppBar 圖示入口。）

## Impact

- 新增 `apps/bite/src/main/java/com/routina/bite/data/Trends.kt`（統計純函式）
- 新增 `apps/bite/src/main/java/com/routina/bite/ui/ChartsScreen.kt`（畫面與三個分頁）
- 新增 `apps/bite/src/main/java/com/routina/bite/ui/Charts.kt`（Canvas 繪圖基礎：座標換算、軸、長條、折線）
- 修改 `MainActivity.kt`（加 `charts?tab=` 路由）、`ui/HistoryScreen.kt`／`ui/WeightScreen.kt`（TopAppBar 入口）
- 修改 `res/values/strings.xml`（新字串附加在檔尾）
- **無新相依、無新權限、無資料模型變更、無檔案格式變更**（純讀既有的 `entries`／`weights`／`targets`）
