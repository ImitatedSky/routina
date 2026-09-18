# Design: add-food-categories

## Context

v0.0.1 已經在使用者的機器上跑了一陣子：78 種食物、185 筆紀錄。食物庫的規模與分布很清楚——
36 筆是 Subway（六吋主餐、配料、醬料）、8 筆麥當勞、5 筆好事多、3 筆蛋白粉、3 筆參考值（每 1 g 的蛋白質／脂肪／糖）、
2 筆池上便當、1 筆超商，其餘 20 筆是各種單品。來源資訊本來就寫在 `note` 裡（所以搜尋「subway」搜得到），
但畫面上是一條平坦的清單，看不出結構。

這一波沿用 add-bite-app 的既有決策：JSON 檔儲存（D1）、欄位只增不刪（D1）、`Food` 的資料模型（D2）、
seed 只在首次啟動載入（D8）、單一 Repository + StateFlow（D9）、視覺與刪除規範（D10）。
下面只寫這次新增的決策，編號接在 D12 之後。

## Goals / Non-Goals

**Goals:**
- 食物庫在畫面上看得出結構：同一個來源收在一起，一眼看到「有哪些來源、各幾種」
- 有一頁可以純粹整理食物庫（改分類、改名、標常用、刪除），不必假裝要記一餐
- 舊的 `foods.json` 與舊的備份檔不必轉檔就能讀，備份格式不變
- 新增紀錄頁與食物庫頁對「食物庫」的呈現是同一套，不維護兩份

**Non-Goals:**
- 分類不做成獨立的實體（沒有分類 id、沒有分類的顏色／圖示／排序權重）
- 不做多層分類、不做一個食物多個分類
- 不做拖曳排序、不做批次多選搬移分類（改名已經涵蓋「整組搬走」這件事）
- 不自動從 `note` 推斷分類回填舊資料（見 D13 的取捨）

## Decisions

### D13. `category` 是 `Food` 上的一個字串，不是獨立實體

```kotlin
@Serializable data class Food(
    val id: String, val name: String,
    val servingGrams: Double? = null,
    val nutrients: Nutrients,
    val note: String = "",
    val category: String = "",   // 新增；空字串＝未分類
    val favorite: Boolean = false,
    val createdAt: Long
)
```

考慮過的替代方案是「`Category(id, name)` 實體 + `Food.categoryId`」。否決的理由：
分類本身沒有任何屬性要存（名稱就是全部），加了實體就得處理分類檔的載入與寫入、孤兒分類、
改名時 id 不變但顯示要更新、匯入備份時兩份分類表怎麼合併——為了一個字串付這些成本不值得。
用字串的代價是「改名＝更新一批食物」與「刪光某分類的食物，那個分類就消失」，兩者都正是預期行為。

有預設值所以 `Json { ignoreUnknownKeys = true }` 之下舊檔直接可讀，
`encodeDefaults = true` 讓寫回去的 `foods.json` 一定帶 `category`。備份是整個 `Food` 序列化，
`category` 自動跟著走，`BiteBackup.schemaVersion` 維持 1（沒有任何舊版讀不懂的結構，只是多一個會被忽略的欄位）。

**舊裝機拿不到 seed 的分類。** seed 只在 `foods.json` 不存在時載入（D8），這條不改——
改成「啟動時把 seed 的分類補到同 id 的食物上」等於 App 會在使用者背後改他的資料，
而且使用者若刻意把某個 Subway 食物移出分類，下次啟動又會被塞回去。
既有使用者要拿到分類，走既有的路：重新匯入帶分類的備份檔（依 id 合併、同 id 以匯入的為準，D7），
或在編輯頁自己填。這件事要在 tasks 的驗證裡實際測到，不能只是「理論上」。

### D14. 分組預設全部收合，持久化的是「展開的集合」

存法：SharedPreferences `routina_bite`（既有的那個），key `expanded_categories`，
值是用 `\n` 串起來的分類名稱（`Set<String>` 直接進 `putStringSet` 也可以，但 `putStringSet` 回傳的 Set
不保證可變性也不保證順序，字串串接更好除錯）。未分類那組用固定的 key `__none__`，
因為它的分類名稱是空字串，串接後分不出來。

**預設全部收合。** 理由：食物庫頁一打開就是 8 行標頭，一眼看完「有哪些來源、各幾種」，一點就展開，
這正是使用者要的「同一個可以摺疊的部分」。反過來預設全展開的話，一進去是 86 行（78 食物 + 8 標頭），
其中 36 行是 Subway，摺疊功能的價值要等使用者自己一組一組收起來才兌現——等於把整理工作丟給使用者。
新增紀錄頁同理：那頁真正常用的是上面的「常吃／最近」與搜尋，下面的完整食物庫維持收合更清爽。

考慮過「預設全收、只展開未分類」（使用者的建議）。否決是因為它需要一個「是不是第一次」的旗標才存得起來
（存展開集合的話，空集合到底是「還沒設定」還是「使用者把全部收起來了」分不出來），
為了一組 20 筆的預設展開多一個狀態不划算。而且「未分類」在使用者整理完之後會越來越小，
把它釘成預設展開，只對現在這一刻是對的。

找東西的路徑不受影響：搜尋時不分組，直接列結果（見 D16）。

### D15. 改名分類＝把該分類底下所有食物一起改

```kotlin
fun renameCategory(from: String, to: String)   // to 空白 → 那些食物變成未分類
```

Repository 一次更新記憶體裡的清單再寫一次檔，不是 N 次 upsert（N 次 upsert 會排 N 次寫入）。
改成一個已經存在的分類名稱＝兩組合併，這是字串分類的自然結果，也剛好是使用者想要的操作
（「把『超商』併進『未分類』」不需要另一個功能）。

「未分類」不提供改名：它不是一個分類，是「category 是空字串」這件事的顯示名稱。
讓它可以改名的話，語意會變成「把所有沒分類的食物一次塞進某個分類」——那不是使用者按下「重新命名」時期待的事。
展開狀態也一併搬過去（`from` 是展開的，改名後 `to` 就是展開的），不然改完名那組會突然收起來，看起來像資料不見了。

### D16. 搜尋中不分組

有搜尋字串時兩個畫面都直接列結果（沿用既有的 `searchFoods`，favorite 優先再依名稱）。
搜尋本來就已經把範圍縮到幾筆，再包一層分組標頭只是多兩次點擊。
這也讓「預設收合」不會擋到任何人：找特定食物就打字，瀏覽結構就看標頭。

### D17. 分組排序：未分類永遠最後，其餘依名稱

`groupByCategory` 回傳 `List<FoodGroup>`（`category` + `foods`），
未分類（`category == ""`）固定排最後，其餘 `sortedBy { it.category }`（Java 的字串比較，不做 locale collation）。
組內沿用 `searchFoods(foods, "")` 的排序規則：favorite 優先再依名稱，跟搜尋結果一致。

不做 locale collation 是因為 `Collator` 對中文的排序結果一般人也說不出所以然，
而這個清單只有 8 組、使用者靠的是認名稱不是掃順序；穩定、可預期比「正確」重要。

### D18. 共用的分組清單做成 `LazyListScope` 擴充函式

`ui/FoodGroups.kt`：

```kotlin
fun LazyListScope.foodGroups(
    groups: List<FoodGroup>,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    onFoodClick: (Food) -> Unit,
    headerMenu: ((FoodGroup) -> List<Pair<String, () -> Unit>>)? = null,
    rowMenu: ((Food) -> List<Pair<String, () -> Unit>>)? = null
)
```

兩個畫面都是 `LazyColumn`，做成 `LazyListScope` 的擴充就能直接塞進各自的清單，
不必為了共用把整個畫面的捲動結構改掉——TodayScreen 的 `mealSection` 已經是這個寫法，照著做。
兩邊不同的只有「點一列要做什麼」（食物庫頁進編輯、新增紀錄頁開份數對話框）與「⋯ 選單放什麼」，
所以那兩件事用參數傳進去；`headerMenu` 為 null 就不畫標頭的 ⋯（新增紀錄頁不提供改名分類，那是整理動作）。

展開狀態由 Repository 持有（`StateFlow<Set<String>>`），兩個畫面 collect 同一份，
所以在食物庫頁展開 Subway 之後回新增紀錄頁也是展開的。狀態放 Repository 而不是各自 `remember`
是因為它本來就要持久化，持久化的東西在這個 App 裡一律歸 Repository（D9）。

### D19. 編輯頁的分類欄：文字輸入 + 既有分類的 chip 列

`OutlinedTextField`（自由輸入）下面一排 `FilterChip`（現有分類，點了就填進欄位、再點一次清空）。

考慮過 `ExposedDropdownMenuBox`。否決的理由：它是 Material 3 的實驗性 API，
要處理 `menuAnchor`、焦點與展開狀態的互動，程式碼比它解決的問題複雜；
而 chip 列把 8 個分類一次攤開，不用先點開才知道有哪些，反而少一次點擊。
App 裡已經有 `MealPicker` 是同樣的 chip 列寫法，維護的人不用學新東西。

### D20. 食物庫的入口放 TopAppBar 第一個

今日頁的 TopAppBar 變成四個圖示：食物庫（`Icons.Default.Restaurant`）、歷史、體重、設定。
食物庫放最前面是因為它是這四個裡面最常進去的（記錄之外唯一會反覆做的事就是整理食物），
而設定維持在最後。新增紀錄頁的「新建食物」旁再加一個「食物庫」按鈕——那一列本來只有兩顆
（快速輸入、新建食物），加第三顆不會擠。

## Risks / Trade-offs

- [既有使用者升級後看到的全是「未分類」] → 這是 D8 的直接後果，不是 bug。設計上接受，
  使用者重新匯入 `bite-import.json`（已含 category）就有分類；驗證項目要實際跑這條路徑。
- [預設全收合，使用者第一次進食物庫可能以為食物不見了] → 標頭帶數量（「Subway（36）」），
  看得到東西在裡面；空的食物庫才顯示空狀態文字。
- [改名成既有分類會直接合併，沒有二次確認] → 合併是可逆的（再改回去就分開了，前提是原本兩組沒有重名的食物），
  而且分類本來就只是個字串標籤，誤操作的代價低於多一個確認框的摩擦。
- [`expanded_categories` 存的是分類名稱，改名後如果不搬就會失效] → D15 已經把搬移一起做掉；
  分類被刪光（底下食物全刪）留在 prefs 的殘留名稱不影響顯示，下次寫入時自然會被蓋掉。
- [分組把 78 筆食物分成 8 組，每次 recomposition 都重算] → 78 筆的 groupBy 是微秒級，
  用 `remember(foods)` 包起來就夠，不需要快取或搬進 ViewModel。

## Migration Plan

沒有資料遷移步驟：`category` 有預設值，舊 `foods.json` 直接可讀，第一次寫入時自動補上空字串。
部署順序：

1. 在 `add-food-categories` 分支上 commit（不 push、不 tag）。
2. 使用者驗收後 merge 進 main、決定何時打 `bite-v0.1.0`；CI 的 `release-bite` job 會比對
   tag 去掉 `bite-v` 之後與 `apps/bite/build.gradle.kts` 的 versionName，所以 versionName 這一波先改成 `0.1.0`。
3. 使用者裝上新版後，重新匯入帶分類的 `bite-import.json`，既有的 78 種食物就有分類了
   （依 id 合併、同 id 以匯入的為準，紀錄不會重複）。

回滾：裝回 v0.0.1 即可。`category` 對舊版是未知欄位，`ignoreUnknownKeys = true` 會忽略它，
但舊版寫回 `foods.json` 時會把 `category` 洗掉——所以回滾前先匯出一份備份。

## Open Questions

- 目前沒有「批次把多個食物搬到某分類」的操作。實際整理時如果發現「一個一個進編輯頁改」太慢，
  下一波可以考慮在食物庫頁加多選；先看使用者實際會不會做這件事。
