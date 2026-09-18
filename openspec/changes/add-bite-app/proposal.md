# Change: add-bite-app

## Why

使用者用 Google Sheets 記了 31 天熱量之後停了：每筆都要下拉選食物、填份數、按存檔，記錯成本太高。
實際吃的東西高度重複（前 10 種食物佔了 57% 的紀錄），缺的不是更大的食物庫，而是一個「一點就進」的紀錄器。
把它做成家族第三個成員 **Routina Bite**，並順手補上 Hub 在 monorepo 裡偵測成員更新的缺口。

## What Changes

- **新 module `:apps:bite`**（applicationId `com.routina.bite`、顯示名 Routina Bite、`family.id` = `bite`），
  獨立 APK、零權限。tag `bite-v*`，APK `routina-bite-vX.Y.Z.apk`。
- **食物庫**：欄位＝台灣營養標示 10 項（熱量、蛋白質、脂肪、飽和脂肪、反式脂肪、碳水、糖、鈉、膽固醇）
  加每份公克與備註。首次啟動載入 78 種 seed（轉自使用者的營養標示表，`assets/seed_foods.json`）。
  新增／編輯／封存、搜尋（名稱與備註、中英都搜）、常用星號。
- **今日紀錄**：頂部「已吃／目標／剩餘」加蛋白質、脂肪、碳水三條進度；下面依早餐、午餐、晚餐、點心分段，
  餐別依當下時間自動帶入。每筆可用份數或公克輸入；「快速輸入」只填熱量（可選三大營養素）不必先建食物；
  「複製昨天」整天帶入；每天一句備註。日期可左右切換。
- **新增頁**：最上面是「常吃」與「最近」，其次搜尋結果；點食物→填份數→加入。
- **目標**：每日熱量與蛋白質手動輸入，脂肪、碳水選填。App 不自動算 TDEE。
- **歷史**：依日期列出每日總熱量與目標達成狀況，點進去看該日。
- **體重紀錄**：日期、公斤、體脂率、來源（家用體重計／InBody），列表增改刪。這一波不畫圖。
- **備份**：整包匯出／匯入 JSON（食物庫、紀錄、體重、每日備註、目標），
  用系統文件選擇器。使用者既有的 31 天資料由轉檔好的 `bite-import.json` 匯入。
- **家族掛載**：manifest meta-data、能力 `open_today`（把今日頁帶到前景）與透明跳板 Activity。
- **Hub 配合修改**：
  - registry 的 source 加選用欄位 `tagPrefix`（預設 `v`），Bite 的是 `bite-v`。
  - `RegistryClient.latest` 改成列出 releases、依 `tagPrefix` 篩選第一個非 draft／prerelease 的。
    現在用 `/releases/latest` 在 monorepo 會拿到「整個 repo 最新的 release」，Bite 一發版 Hub 自己的更新檢查就會抓錯。
  - `Version.normalize` 吃掉前綴再比數字。
  - `apps.json` 加 bite、`icons/bite.png`。
  - CI：一般 build job 改建全部 module；新增 `bite-v*` tag 的 release job（tag 與 `:apps:bite` 的 versionName 比對）。

## Non-goals（留給之後的版本）

- 圖表（體重趨勢、週平均）、組合餐、桌面小工具、`log_weight` / `add_food` 能力 → v0.2
- InBody 完整欄位與附圖、內建衛福部食品成分資料庫、條碼掃描 → v0.3
- 30 天啞鈴訓練表不屬於 Bite，之後另一個成員

## Capabilities

### New Capabilities
- `bite-food-library`: 食物資料模型、seed 初始化、新增／編輯／封存、搜尋、常用
- `bite-diary`: 每日紀錄——餐別、份數／公克、快速輸入、複製昨天、每日備註、今日進度、歷史列表
- `bite-weight-log`: 體重紀錄列表與增改刪
- `bite-settings-backup`: 目標設定、整包匯出／匯入
- `bite-family-member`: 家族 meta-data、`open_today` 能力、跳板 Activity
- `hub-member-releases`: Hub 對前綴 tag 成員的最新版偵測與版本比對

### Modified Capabilities
（`openspec/specs/` 目前是空的，無既有 spec 可修改）

## Impact

- 新增 `apps/bite/`（module、manifest、Compose UI、JSON 儲存）；`settings.gradle.kts` include `:apps:bite`；
  `gradle/libs.versions.toml` 加 `navigation-compose`
- Hub：`catalog/Registry.kt`（`RegistrySource.tagPrefix`、`Version.normalize`）、`catalog/RegistryClient.kt`（`latest`）、
  `apps.json`、`icons/bite.png`、`.github/workflows/build.yml`、`README.md` 成員表
- Bite 依賴 `:core:contract`（收能力 Intent）與 `navigation-compose`；無新權限；匯入匯出走系統文件選擇器所以不需儲存權限
- 資料檔全部是新的（`foods.json`、`diary.json`、`weights.json`、SharedPreferences `routina_bite`），沒有相容性負擔
