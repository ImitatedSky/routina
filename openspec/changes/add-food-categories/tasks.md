# Tasks: add-food-categories

## 1. 資料模型與 seed

- [x] 1.1 `model/Food.kt`：加 `val category: String = ""`（放在 `note` 之後、`favorite` 之前，與 seed 的欄位順序一致）
- [x] 1.2 `apps/bite/src/main/assets/seed_foods.json` 換成帶 `category` 的新版（78 種），並刪掉暫存的 `seed_foods_with_categories.json`（不進 repo）
- [x] 1.3 確認備份不用改：`BiteBackup` 序列化的是整個 `Food`，`schemaVersion` 維持 1，`BackupCodec` 不動

## 2. 查詢與儲存

- [x] 2.1 `data/Queries.kt`：`allCategories(foods)`（去重、去空、依名稱排序）
- [x] 2.2 `data/Queries.kt`：`FoodGroup(category, foods)` 與 `groupByCategory(foods)`——未分類固定最後，其餘依分類名稱；組內 favorite 優先再依名稱（D17）
- [x] 2.3 `data/BiteRepository.kt`：`renameCategory(from, to)` 一次更新整組食物再寫一次檔；`to` 空白＝變未分類（D15）
- [x] 2.4 `data/BiteRepository.kt`：`expandedCategories: StateFlow<Set<String>>` 與 `setCategoryExpanded(category, expanded)`，存 SharedPreferences `routina_bite` 的 `expanded_categories`（`\n` 串接，未分類用 `__none__`），預設全部收合（D14）
- [x] 2.5 `renameCategory` 一併把展開狀態從舊名搬到新名（D15）
- [x] 2.6 `ui/BiteViewModel.kt`：把 `expandedCategories`、`setCategoryExpanded`、`renameCategory` 轉出去給畫面用

## 3. 共用的分組清單

- [x] 3.1 `ui/FoodGroups.kt`：`LazyListScope.foodGroups(...)`——分組標頭（分類名稱＋數量、展開／收合、可選的 ⋯ 選單）與食物列（名稱、熱量、每份公克、常用星號、點擊、可選的 ⋯ 選單）（D18）
- [x] 3.2 `ui/FoodGroups.kt`：`RenameCategoryDialog`（預填目前名稱、空白＝未分類、說明一句）

## 4. 畫面

- [x] 4.1 新檔 `ui/FoodLibraryScreen.kt`：TopAppBar（標題、返回）、搜尋欄、有查詢就平鋪結果／沒查詢就分組、FAB 新增食物、空狀態文字
- [x] 4.2 `FoodLibraryScreen` 的列動作：點一下進編輯；⋯ 選單「編輯／切換常用／刪除」，刪除走 `ConfirmDialog`
- [x] 4.3 `FoodLibraryScreen` 的標頭動作：⋯ 選單「重新命名分類」；未分類那組不給 ⋯
- [x] 4.4 `MainActivity.kt`：加路由 `library`，`TodayScreen` 與 `FoodLibraryScreen` 都能導到 `food/{id}` 與 `food/new`
- [x] 4.5 `ui/TodayScreen.kt`：TopAppBar 加食物庫圖示（`Icons.Default.Restaurant`），放在歷史之前（D20）
- [x] 4.6 `ui/AddFoodScreen.kt`：沒有搜尋字串時改用 `foodGroups`（共用展開狀態、標頭不給 ⋯）；有搜尋字串時維持平鋪；「常吃／最近」不動
- [x] 4.7 `ui/AddFoodScreen.kt`：「快速輸入／新建食物」那一列加第三顆「食物庫」按鈕
- [x] 4.8 `ui/FoodEditScreen.kt`：加「分類」欄（自由輸入）＋ 既有分類的 `FilterChip` 列，點了填入、再點一次清空；空白＝未分類（D19）

## 5. 字串與版本

- [x] 5.1 `res/values/strings.xml`：`nav_library`、`library_title`、`library_empty`、`category_none`、`category_group_header`、`category_expand`／`category_collapse`、`category_rename_title`、`category_rename_hint`、`field_category`、`action_toggle_favorite`，全部繁中，不硬編（搜尋欄沿用既有的 `add_search_hint`）
- [x] 5.2 `apps/bite/build.gradle.kts`：versionCode 1 → 2、versionName `0.0.1` → `0.1.0`（要與未來的 tag `bite-v0.1.0` 一致）

## 6. 驗證

- [x] 6.1 `./gradlew :apps:bite:assembleDebug` 與 `:apps:bite:assembleRelease` 綠燈
- [x] 6.2 裝置（既有資料）：升級安裝後不崩潰，78 種食物全部顯示在「未分類（78）」底下（因為 `foods.json` 已存在，D13）
- [x] 6.3 裝置：重新匯入帶 category 的 `bite-import.json` 後分類出現，Subway 那組標頭是「Subway（36）」
- [x] 6.4 裝置：展開／收合有效；force-stop 後重開 App，展開狀態保留
- [x] 6.5 裝置：搜尋時不分組、清空搜尋回到分組且展開狀態還在
- [x] 6.6 裝置：重新命名分類後整組食物都改到新名稱，該組仍是展開的
- [x] 6.7 裝置：食物庫頁的編輯／切換常用／刪除（含確認框）都正確，刪除不影響既有紀錄
- [x] 6.8 裝置：編輯食物頁的分類欄與建議 chip；新分類會在食物庫長出新的一組
- [x] 6.9 裝置：新增紀錄頁的分組呈現，展開狀態與食物庫頁共用，點食物仍開份數對話框
- [x] 6.10 裝置：`pm clear` 後首次啟動，seed 直接帶分類（8 組、Subway 36）——這會清掉使用者資料，所以放最後，測完立刻重新匯入 `bite-import.json` 還原
- [x] 6.11 commit（英文、無 Co-Authored-By）；不 push、不 tag
