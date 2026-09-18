# Design: add-bite-app

## Context

家族目前有 Hub（`:hub`）與 Flow（獨立 repo）。Bite 是第一個住在 monorepo 裡的子 App module，
所以除了 App 本身，還要把「monorepo 裡多個成員各自發版」這條路走通：settings include、CI、Hub 的更新偵測。

使用者的資料來源是一份 Google Sheets（已用 Python 轉成 App 的格式）：
78 種食物的營養標示、31 天共 184 筆紀錄、7 筆體重。轉檔腳本不進 repo；
seed 食物庫進 `assets/`，個人紀錄（`bite-import.json`）留在使用者機器上用匯入功能帶進 App。

## Goals / Non-Goals

**Goals:**
- 記一餐不超過 10 秒：常吃／最近一點就進、複製昨天、餐別自動帶入
- 資料模型從第一天就能整包備份還原（Flow 的備份只涵蓋 routines，這個缺口不再犯）
- Hub 在 monorepo 裡對每個成員都能正確判斷最新版

**Non-Goals:**
- 圖表、組合餐、小工具、外部食物庫、條碼、InBody 完整欄位（見 proposal 的 Non-goals）
- 抽共用 UI library（`:core:ui`）——目前只有 Hub 與 Bite 兩個消費者，先各自複製 theme，等第三個再抽

## Decisions

### D1. 儲存用 JSON 檔 + kotlinx.serialization，不用 Room
一年約三千筆明細，全部載進記憶體不到 1 MB，用不到索引與局部寫入。家族其他成員都是這個作法，
不引入 KSP 與 schema migration 的成本。寫入沿用 Flow 的 `tmp + renameTo` 原子寫入，
所有寫入在 `Dispatchers.IO` 上以 `Mutex` 序列化。

檔案：
- `foods.json` → `List<Food>`
- `diary.json` → `DiaryFile(entries, dayNotes)`
- `weights.json` → `List<WeightEntry>`
- 目標值放 SharedPreferences `routina_bite`（幾個整數，不值得一個檔）

`Json { ignoreUnknownKeys = true; encodeDefaults = true }`：欄位只增不刪，舊檔永遠可讀。

### D2. 資料模型
```kotlin
@Serializable data class Nutrients(
    val kcal: Double = 0.0, val protein: Double = 0.0, val fat: Double = 0.0,
    val satFat: Double = 0.0, val transFat: Double = 0.0, val carbs: Double = 0.0,
    val sugar: Double = 0.0, val sodium: Double = 0.0, val cholesterol: Double = 0.0
)   // 鈉、膽固醇是 mg，其餘 g；提供 times(factor) 與 plus

@Serializable data class Food(
    val id: String, val name: String,
    val servingGrams: Double? = null,     // 每份公克，沒有就只能用份數
    val nutrients: Nutrients,             // 每份
    val note: String = "", val favorite: Boolean = false, val createdAt: Long
)

@Serializable enum class Meal { BREAKFAST, LUNCH, DINNER, SNACK }

@Serializable data class DiaryEntry(
    val id: String, val date: String,     // yyyy-MM-dd（本地日期）
    val meal: Meal? = null,               // null = 未分餐（匯入的舊資料）
    val foodId: String? = null,           // null = 快速輸入
    val name: String, val servings: Double,
    val servingGrams: Double? = null, val perServing: Nutrients,   // 快照
    val createdAt: Long
)

@Serializable data class DayNote(val date: String, val note: String)
@Serializable data class DiaryFile(val entries: List<DiaryEntry> = emptyList(), val dayNotes: List<DayNote> = emptyList())

@Serializable enum class WeightSource { HOME_SCALE, INBODY }
@Serializable data class WeightEntry(
    val id: String, val date: String, val kg: Double,
    val bodyFatPct: Double? = null, val source: WeightSource = WeightSource.HOME_SCALE, val note: String = ""
)

@Serializable data class Targets(val kcal: Int = 2000, val protein: Int = 120, val fat: Int? = null, val carbs: Int? = null)

@Serializable data class BiteBackup(
    val schemaVersion: Int = 1, val app: String = "bite", val exportedAt: Long,
    val foods: List<Food>, val diary: List<DiaryEntry>, val weights: List<WeightEntry>,
    val dayNotes: List<DayNote>, val targets: Targets? = null
)
```
欄位名與 `assets/seed_foods.json`、使用者的 `bite-import.json` 完全一致（同一支腳本產出）。id 一律 UUID 字串。

### D3. 紀錄存營養快照，不只存 foodId
`DiaryEntry` 複製當時的 `name`、`perServing`、`servingGrams`。之後改食物數值不會改寫歷史，
刪除食物也不影響已記錄的天；「快速輸入」就是 `foodId = null` 的一筆。`foodId` 只用來算常吃／最近與跳回食物。
代價是檔案大一點，可忽略。

### D4. 餐別可為 null，新紀錄依時間自動帶入
匯入的舊資料沒有餐別，硬塞成點心是說謊；`meal = null` 顯示在「未分餐」段，只在有資料時出現。
新增時預設餐別：04:00–10:29 早餐、10:30–14:29 午餐、14:30–16:59 點心、17:00–21:29 晚餐、其餘點心。
使用者可在編輯時改。

### D5. 份數是唯一真值，公克是輸入方式
輸入公克時 `servings = grams / servingGrams`，存的仍是 `servings`；`servingGrams` 為 null 的食物不提供公克輸入。
顯示：熱量取整數，其餘一位小數。

### D6. 常吃／最近從紀錄算，不另存欄位
- 最近：依 `createdAt` 倒序取不重複的 `foodId`，最多 12 個
- 常吃：最近 90 天內以 `foodId` 計數排序，最多 12 個
- 兩者都排除 `foodId = null`（快速輸入）與已不存在的食物
搜尋：小寫後對 `name` 與 `note` 做 contains（中英都搜，備註帶「Subway」「麥當勞」所以品牌也搜得到）；
結果 `favorite` 優先再依名稱。

### D7. 備份匯入是「依 id 合併」，永不刪除
匯入時 foods／diary／weights 各依 `id` 合併（同 id 以匯入的覆蓋），dayNotes 依 `date`，`targets` 有值才套用。
重複匯入同一份不會產生重複；seed 與 `bite-import.json` 出自同一支腳本、id 相同，所以匯入後食物庫不會出現兩份。
拒收：`app != "bite"` 或 `schemaVersion > 1`（提示先更新 App）。
匯出走 `ActivityResultContracts.CreateDocument("application/json")`，匯入走 `OpenDocument`，不需要儲存權限。

### D8. Seed 只在沒有 foods.json 時載入
首次啟動 `foods.json` 不存在 → 讀 `assets/seed_foods.json` 寫成 `foods.json`。之後使用者刪光也不再回填
（他刪就是不想要）。

### D9. 導覽用 navigation-compose，狀態集中在一個 Repository
`BiteApp : Application` 持有 `BiteRepository`（單例、StateFlow 對外）；一個 `BiteViewModel` 包住它給畫面用。
不引入 DI 框架。路由：`today`、`add/{date}/{meal}`、`food/{id}`、`food/new`、`history`、`weights`、`settings`。
版本沿用 Flow 的 2.8.5（本機已快取、與 Compose BOM 2026.08 相容已驗證）。

### D10. 視覺
複製 Hub 的 `FamilyColors` 與 `RoutinaTheme`。三大營養素固定三色（蛋白質、脂肪、碳水各一，低飽和），
熱量用主色；顏色只做分組輔助，數字與文字才是辨識依據。刪除一律走 ⋯ 溢位選單加確認，不做滑動刪除。
啟動圖示先沿用家族品牌圖（與 Hub、Flow 相同），`icons/bite.png` 亦同。

### D11. Hub：列 releases 依前綴篩選，取代 `/releases/latest`
`RegistrySource` 加 `tagPrefix: String = "v"`。`latest()` 改打 `GET /repos/{repo}/releases?per_page=30`，
取第一個 `!draft && !prerelease && tag_name.startsWith(tagPrefix)` 的 release（GitHub 依建立時間倒序回傳），
再依 `assetPattern` 挑附件。`Version.normalize(raw, prefix)` 先去前綴再去 `v`。
一個成員仍然一次 API 呼叫，額度負擔不變。`schemaVersion` 維持 1（只加選用欄位，舊 Hub 讀得懂、只是比不出 Bite 的版本）。

### D12. CI
- `build` job：不再只建 `:hub`，改 `./gradlew assembleRelease` 建全部 module，兩支 APK 都上傳 artifact；
  觸發條件改成 `github.ref_type != 'tag'`。
- workflow 的 `tags` 加 `bite-v*`。既有 `release` job 的 `startsWith(github.ref, 'refs/tags/v')` 對 `bite-v*` 為 false，不需改。
- 新 `release-bite` job：`startsWith(github.ref, 'refs/tags/bite-v')`；tag 去掉 `bite-v` 後與
  `apps/bite/build.gradle.kts` 的 versionName 比對；`:apps:bite:assembleRelease`；
  `gh release create bite-vX.Y.Z routina-bite-vX.Y.Z.apk --title "Routina Bite vX.Y.Z"`。
  簽章區塊與 Hub 的一模一樣（同一把 keystore）。

## Risks / Trade-offs

- [匯入資料含一筆負份數（使用者當年用 -1 扣掉 Subway 麵包）] → 顯示照存的值；只有新增／編輯時才驗證 > 0。
- [JSON 整檔重寫，紀錄越多寫入越慢] → 三千筆／年、十年 30k 筆仍是幾十毫秒級；寫入在 IO thread 且原子，不阻塞 UI。
- [常吃靠 `foodId`，使用者刪食物後它從常吃消失] → 符合預期；歷史因快照不受影響。
- [Hub 舊版讀到 `tagPrefix` 會忽略，對 Bite 顯示「查不到可安裝的版本」] → 已是既有的誠實降級，使用者更新 Hub 即可。
- [`releases?per_page=30` 若 Hub 連發超過 30 版而 Bite 都沒發，Bite 會被擠出清單] → 不切實際；真發生就加分頁。
- [R8 與 kotlinx.serialization] → Hub 已在 minify 下用同一套序列化跑 Registry，沿用即可。

## Migration Plan

沒有既有資料要遷移。部署順序：
1. push main（Bite module、Hub 修改、apps.json、CI）。此時 Hub 目錄會列出 Bite 但顯示「查不到可安裝的版本」，因為還沒有 release。
2. 使用者決定發版時打 `bite-v0.1.0`，CI 產出 release，Hub 即可安裝。
3. 使用者裝好後用「匯入備份」帶入 `C:\Users\User\Desktop\bite-import.json`。

## Open Questions

- 目標預設值 2000 kcal／120 g 蛋白質只是讓畫面有東西可算，使用者第一次進設定就會改。
