# Tasks: add-bite-app

## 1. Module 骨架

- [ ] 1.1 `settings.gradle.kts` include `:apps:bite`；`gradle/libs.versions.toml` 加 `navigation-compose` 2.8.5
- [ ] 1.2 `apps/bite/build.gradle.kts`（application + compose + serialization 外掛、簽章區塊與 Hub 相同、versionCode 1 / versionName 0.1.0、依賴 `:core:contract`、navigation-compose）與 `proguard-rules.pro`
- [ ] 1.3 `AndroidManifest.xml`：零 `uses-permission`、family meta-data（member/id/name/summary/capabilities）、`MainActivity` singleTop + LAUNCHER、`CapabilityActivity`（exported、noHistory、excludeFromRecents、taskAffinity=""、隱形主題）
- [ ] 1.4 res：`strings.xml`（繁中）、`themes.xml`／`themes_invisible.xml`／`values-night`、`colors.xml`（`ic_launcher_background`）、`xml/family_capabilities.xml`（`open_today`）；啟動圖示檔已在 `res/`
- [ ] 1.5 `ui/theme`：複製 Hub 的 `FamilyColors` 與 `RoutinaTheme`，加蛋白質／脂肪／碳水三個固定低飽和色

## 2. 模型與儲存

- [ ] 2.1 `model/`：`Nutrients`（`times`、`plus`）、`Food`、`Meal`、`DiaryEntry`、`DayNote`、`DiaryFile`、`WeightSource`、`WeightEntry`、`Targets`、`BiteBackup`，欄位名與 design D2 一字不差
- [ ] 2.2 `data/BiteRepository`：載入 `foods.json`／`diary.json`／`weights.json` 成 StateFlow；`Mutex` + tmp/renameTo 原子寫入（IO thread）；沒有 `foods.json` 時載入 `assets/seed_foods.json`（D8）；foods／diary／dayNotes／weights 的增改刪；`Targets` 存 SharedPreferences `routina_bite`
- [ ] 2.3 統計與查詢：某日合計、餐別小計、常吃（90 天計數前 12）、最近（不重複前 12）、搜尋（name+note 小寫 contains，favorite 優先再名稱）、依時間預設餐別（D4）
- [ ] 2.4 `data/BackupCodec`：匯出組 `BiteBackup`；匯入驗證 `app == "bite"` 且 `schemaVersion <= 1`，依 id／date 合併、`targets` 有值才套用（D7）
- [ ] 2.5 `BiteApp : Application` 持有 repository；`ui/BiteViewModel` 包住 repository 給畫面用

## 3. UI

- [ ] 3.1 `MainActivity` + `NavHost`：`today`、`add/{date}/{meal}`、`food/{id}`、`food/new`、`history`、`weights`、`settings`；edge-to-edge
- [ ] 3.2 `TodayScreen`：日期列（前一天／後一天／回今天）、進度區（已吃／目標／剩餘可為負 + 蛋白質脂肪碳水各對目標）、餐別分段含小計、未分餐段僅在有資料時出現、點一筆可改份數與餐別、⋯ 刪除確認、複製昨天（昨天沒有紀錄時提示）、每日備註、FAB 新增、進入歷史／體重／設定的入口
- [ ] 3.3 `AddFoodScreen`：常吃、最近、搜尋欄與結果；點食物開份數／公克輸入（食物無 `servingGrams` 時不給公克；非正數停用加入）；快速輸入（名稱預設「快速輸入」、kcal 必填、三大營養素選填）；「新建食物」入口
- [ ] 3.4 `FoodEditScreen`：名稱必填、kcal 必填、其餘 9 欄空白＝0、每份公克選填、備註、常用切換、⋯ 刪除確認（刪除不影響既有紀錄）
- [ ] 3.5 `HistoryScreen`：有紀錄或備註的日期倒序，顯示總 kcal／目標／備註，點入該日的 Today
- [ ] 3.6 `WeightScreen`：倒序列表（日期、kg、體脂、來源）、新增／編輯對話框（kg > 0、體脂 0–100 驗證、來源二選一、備註）、⋯ 刪除確認
- [ ] 3.7 `SettingsScreen`：目標四欄（kcal、蛋白質必填；脂肪、碳水可空）、匯出備份（`CreateDocument`，預設檔名 `bite-backup-yyyyMMdd.json`）、匯入備份（`OpenDocument`，顯示合併結果或拒收原因）、版本號
- [ ] 3.8 `CapabilityActivity`：`open_today` 以 NEW_TASK 帶起 `MainActivity`；未知能力 toast 後 finish

## 4. Hub 配合

- [x] 4.1 `catalog/Registry.kt`：`RegistrySource.tagPrefix: String = "v"`；`Version.normalize(raw, prefix)` 先去前綴再去 `v`，既有呼叫點改帶前綴
- [x] 4.2 `catalog/RegistryClient.latest`：改打 `/repos/{repo}/releases?per_page=30`，取第一個非 draft／prerelease 且 tag 以前綴開頭者，再依 `assetPattern` 挑附件；錯誤訊息照舊誠實
- [x] 4.3 `apps.json` 加 bite（`tagPrefix: "bite-v"`、`assetPattern: "routina-bite-*.apk"`、`packageName: com.routina.bite`、`icon: icons/bite.png`）；`README.md` 成員表加 Routina Bite
- [x] 4.4 `.github/workflows/build.yml`：`tags` 加 `bite-v*`；build job 改 `./gradlew assembleRelease`（全 module）並上傳兩支 APK、條件改 `github.ref_type != 'tag'`；新增 `release-bite` job（tag 去 `bite-v` 後與 `apps/bite` versionName 比對、`:apps:bite:assembleRelease`、`gh release create bite-vX.Y.Z routina-bite-vX.Y.Z.apk --title "Routina Bite vX.Y.Z"`），簽章步驟與 Hub 相同

## 5. 驗證與提交

- [ ] 5.1 `./gradlew assembleRelease` 全 module 綠燈（debug 簽章 fallback）
- [ ] 5.2 BlueStacks 端到端：首次啟動食物庫 78 種；份數新增、公克新增、快速輸入、改餐別、⋯ 刪除、複製昨天、每日備註、歷史列表、體重增改刪、目標修改後今日頁即時更新
- [ ] 5.3 BlueStacks 備份：匯出檔含四個集合與 targets；匯入 `bite-import.json` 後 78 食物／184 筆／31 天／7 體重／備註「感冒日」；再匯入一次數量不變
- [ ] 5.4 BlueStacks 家族：Hub 目錄看到「Routina Bite／熱量飲食紀錄」並可開啟；adb 呼叫 `open_today` 帶起今日頁；Hub 對 Bite 顯示「查不到可安裝的版本」（尚未發版）、對自己仍解析到 v0.3.x
- [ ] 5.5 commit（英文、無 co-author）、push main；不打 tag
