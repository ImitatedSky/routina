# Change: add-food-categories

## Why

使用者實際用了 v0.0.1 之後的回饋：「食物庫還可以有更多方便的分類，像是我原本有一部分很多都是 subway 來的，
這樣應該放在同一區，或者同一個可以摺疊的部分。然後有一頁是食物區可以處理的，目前好像沒有看到這一頁。」

兩個缺口：

1. **食物庫是一條 78 筆的平坦清單**。其中 36 筆是 Subway，佔了快一半；要找一個非 Subway 的食物得先捲過整片 Subway。
   來源（Subway、麥當勞、好事多、池上便當…）本來就寫在 `note` 裡，但只有搜尋吃得到，畫面上看不出結構。
2. **沒有管理食物庫的地方**。目前唯一的入口是「新增紀錄」頁清單右側的鉛筆圖示——那是為了「記一餐」設計的頁面，
   要整理食物庫（改名、改分類、刪掉不吃的、標常用）得先假裝自己要記一筆才進得去。

## What Changes

- **`Food` 加 `category: String = ""`**（空字串＝未分類）。有預設值，既有的 `foods.json` 與既有備份檔照樣讀得進來，
  備份格式與 `schemaVersion` 都不變（`category` 是 `Food` 的一部分，自動跟著備份走）。
- **新的 seed**：`assets/seed_foods.json` 78 種食物全部帶上 `category`
  （Subway 36、未分類 20、麥當勞 8、好事多 5、參考值 3、蛋白粉 3、池上便當 2、超商 1）。
- **新畫面「食物庫」（路由 `library`）**：搜尋欄 + 依分類分組的可摺疊清單。每組標頭顯示「分類名稱（數量）」，
  點標頭展開／收合，摺疊狀態存進既有的 SharedPreferences `routina_bite`，重開 App 保留。
  每列可點進編輯，列右側 ⋯ 提供「編輯／切換常用／刪除」；FAB 新增食物；空狀態一句話。
- **分類改名**：分組標頭的 ⋯ 提供「重新命名分類」，改名會一次更新該分類底下所有食物；改成空白＝整組變未分類。
  「未分類」不是真的分類，不提供改名。
- **編輯食物頁加「分類」欄**：可自由輸入，同時列出現有分類當建議；空白＝未分類。
- **新增紀錄頁的食物庫清單**改成同樣的分組可摺疊呈現（與食物庫頁共用摺疊狀態與 composable）。
  「常吃」「最近」兩排 chip 不變；有搜尋字串時兩個畫面都不分組，直接列結果。
- **今日頁 TopAppBar 加食物庫入口**（歷史、體重、設定之外的第四個圖示，放在歷史之前）。
- **版本**：versionCode 1 → 2、versionName `0.0.1` → `0.1.0`（CI 的 `release-bite` job 會拿 tag `bite-v0.1.0` 與 versionName 比對）。

## Capabilities

### New Capabilities
（無。這一波沒有引入新的能力，只是把既有的食物庫能力補上分類與專屬頁面）

### Modified Capabilities
- `bite-food-library`: 食物多一個 `category` 欄位；食物庫有自己的畫面（分組、可摺疊、記住摺疊狀態）；
  分類可整組改名；新增紀錄頁的食物清單改成同樣的分組呈現。既有的 seed 需求改為「seed 帶分類」。

## Impact

- `apps/bite/src/main/java/com/routina/bite/`：
  `model/Food.kt`（加 `category`）、`data/BiteRepository.kt`（`renameCategory`、摺疊狀態的讀寫）、
  `data/Queries.kt`（`allCategories`、`groupByCategory`）、
  新檔 `ui/FoodLibraryScreen.kt` 與 `ui/FoodGroups.kt`（共用的分組清單）、
  `ui/AddFoodScreen.kt`、`ui/FoodEditScreen.kt`、`ui/TodayScreen.kt`、`ui/BiteViewModel.kt`、`MainActivity.kt`（`library` 路由）
- `apps/bite/src/main/assets/seed_foods.json`（整份換成帶分類的版本）
- `apps/bite/src/main/res/values/strings.xml`（分類相關的繁中字串）
- `apps/bite/build.gradle.kts`（versionCode／versionName）
- 不動 Hub、不動 `:core:contract`、不動 CI workflow、不加任何依賴、不加權限
- **既有使用者拿不到 seed 的分類**：seed 只在 `foods.json` 不存在時載入（D8）。已經在用的人（包含使用者本人）
  要重新匯入帶分類的備份檔才會有分類，或自己在編輯頁一個個填。這是刻意的取捨，見 design D13。
