package com.routina.bite.data

import android.content.Context
import com.routina.bite.model.BiteBackup
import com.routina.bite.model.DayNote
import com.routina.bite.model.DiaryEntry
import com.routina.bite.model.DiaryFile
import com.routina.bite.model.Food
import com.routina.bite.model.Targets
import com.routina.bite.model.WeightEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Bite 的本地儲存：filesDir 底下三個 JSON 檔，加上放在 SharedPreferences 的每日目標。
 *
 * - 資料量小（一年約三千筆），整份載進記憶體、整份寫回，不用資料庫
 * - 以 StateFlow 對外，UI 直接 collect
 * - 寫入排在 IO 執行緒、以 Mutex 序列化，並用 tmp + rename 讓檔案不會停在寫到一半的狀態
 * - 檔案損毀無法解析時以空清單啟動，不崩潰
 */
class BiteRepository(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val foodsFile: File get() = File(appContext.filesDir, FILE_FOODS)
    private val diaryFile: File get() = File(appContext.filesDir, FILE_DIARY)
    private val weightsFile: File get() = File(appContext.filesDir, FILE_WEIGHTS)

    private val _foods = MutableStateFlow<List<Food>>(emptyList())
    val foods: StateFlow<List<Food>> = _foods.asStateFlow()

    private val _entries = MutableStateFlow<List<DiaryEntry>>(emptyList())
    val entries: StateFlow<List<DiaryEntry>> = _entries.asStateFlow()

    private val _dayNotes = MutableStateFlow<List<DayNote>>(emptyList())
    val dayNotes: StateFlow<List<DayNote>> = _dayNotes.asStateFlow()

    private val _weights = MutableStateFlow<List<WeightEntry>>(emptyList())
    val weights: StateFlow<List<WeightEntry>> = _weights.asStateFlow()

    private val _targets = MutableStateFlow(Targets())
    val targets: StateFlow<Targets> = _targets.asStateFlow()

    init {
        // 檔案只有幾百 KB，啟動時同步載入，讓第一幀就有正確資料
        val seeded = loadFoods()
        val diary = readDiary()
        _entries.value = diary.entries
        _dayNotes.value = diary.dayNotes
        _weights.value = readList(weightsFile, ListSerializer(WeightEntry.serializer()))
        _targets.value = readTargets()
        // 內建食物庫讀失敗時（理論上不會）不落地空檔，下次啟動還有機會補上
        if (seeded && _foods.value.isNotEmpty()) persistFoods()
    }

    // ---------- 載入 ----------

    /** 回傳 true 表示這次是用內建食物庫開的檔，要把它落地成 foods.json */
    private fun loadFoods(): Boolean {
        if (foodsFile.exists()) {
            _foods.value = readList(foodsFile, ListSerializer(Food.serializer()))
            return false
        }
        // 首次啟動才載入內建的食物庫；使用者之後自己刪光就維持空的，不再回填
        _foods.value = try {
            appContext.assets.open(ASSET_SEED).bufferedReader().use { reader ->
                json.decodeFromString(ListSerializer(Food.serializer()), reader.readText())
            }
        } catch (t: Throwable) {
            emptyList()
        }
        return true
    }

    private fun <T> readList(file: File, serializer: KSerializer<List<T>>): List<T> = try {
        if (file.exists()) json.decodeFromString(serializer, file.readText()) else emptyList()
    } catch (t: Throwable) {
        emptyList()
    }

    private fun readDiary(): DiaryFile = try {
        if (diaryFile.exists()) {
            json.decodeFromString(DiaryFile.serializer(), diaryFile.readText())
        } else {
            DiaryFile()
        }
    } catch (t: Throwable) {
        DiaryFile()
    }

    private fun readTargets(): Targets {
        val fallback = Targets()
        return Targets(
            kcal = prefs.getInt(KEY_TARGET_KCAL, fallback.kcal),
            protein = prefs.getInt(KEY_TARGET_PROTEIN, fallback.protein),
            fat = if (prefs.contains(KEY_TARGET_FAT)) prefs.getInt(KEY_TARGET_FAT, 0) else null,
            carbs = if (prefs.contains(KEY_TARGET_CARBS)) prefs.getInt(KEY_TARGET_CARBS, 0) else null
        )
    }

    // ---------- 食物庫 ----------

    fun findFood(id: String): Food? = _foods.value.firstOrNull { it.id == id }

    fun upsertFood(food: Food) {
        val current = _foods.value
        val index = current.indexOfFirst { it.id == food.id }
        _foods.value = if (index >= 0) {
            current.toMutableList().apply { this[index] = food }
        } else {
            current + food
        }
        persistFoods()
    }

    /** 刪除食物不動任何紀錄：紀錄存的是當時的快照 */
    fun deleteFood(id: String) {
        _foods.value = _foods.value.filterNot { it.id == id }
        persistFoods()
    }

    // ---------- 紀錄 ----------

    fun findEntry(id: String): DiaryEntry? = _entries.value.firstOrNull { it.id == id }

    fun addEntry(entry: DiaryEntry) {
        _entries.value = _entries.value + entry
        persistDiary()
    }

    fun addEntries(newEntries: List<DiaryEntry>) {
        if (newEntries.isEmpty()) return
        _entries.value = _entries.value + newEntries
        persistDiary()
    }

    fun updateEntry(entry: DiaryEntry) {
        val current = _entries.value
        val index = current.indexOfFirst { it.id == entry.id }
        if (index < 0) return
        _entries.value = current.toMutableList().apply { this[index] = entry }
        persistDiary()
    }

    fun deleteEntry(id: String) {
        _entries.value = _entries.value.filterNot { it.id == id }
        persistDiary()
    }

    /** 備註清空就把那天的備註移除，不留一筆空字串 */
    fun setDayNote(date: String, note: String) {
        val trimmed = note.trim()
        val without = _dayNotes.value.filterNot { it.date == date }
        _dayNotes.value = if (trimmed.isEmpty()) without else without + DayNote(date, trimmed)
        persistDiary()
    }

    // ---------- 體重 ----------

    fun upsertWeight(entry: WeightEntry) {
        val current = _weights.value
        val index = current.indexOfFirst { it.id == entry.id }
        _weights.value = if (index >= 0) {
            current.toMutableList().apply { this[index] = entry }
        } else {
            current + entry
        }
        persistWeights()
    }

    fun deleteWeight(id: String) {
        _weights.value = _weights.value.filterNot { it.id == id }
        persistWeights()
    }

    // ---------- 目標 ----------

    fun setTargets(targets: Targets) {
        _targets.value = targets
        val editor = prefs.edit()
        editor.putInt(KEY_TARGET_KCAL, targets.kcal)
        editor.putInt(KEY_TARGET_PROTEIN, targets.protein)
        if (targets.fat == null) editor.remove(KEY_TARGET_FAT) else editor.putInt(KEY_TARGET_FAT, targets.fat)
        if (targets.carbs == null) editor.remove(KEY_TARGET_CARBS) else editor.putInt(KEY_TARGET_CARBS, targets.carbs)
        editor.apply()
    }

    // ---------- 備份 ----------

    fun buildBackup(): BiteBackup = BiteBackup(
        exportedAt = System.currentTimeMillis(),
        foods = _foods.value,
        diary = _entries.value,
        weights = _weights.value,
        dayNotes = _dayNotes.value,
        targets = _targets.value
    )

    /**
     * 依 id（備註依日期）合併匯入的備份：同 id 以匯入的為準，
     * 現有但檔案裡沒有的一律保留，所以重複匯入同一份不會長出重複資料。
     */
    fun applyBackup(backup: BiteBackup) {
        _foods.value = mergeBy(_foods.value, backup.foods) { it.id }
        _entries.value = mergeBy(_entries.value, backup.diary) { it.id }
        _weights.value = mergeBy(_weights.value, backup.weights) { it.id }
        _dayNotes.value = mergeBy(_dayNotes.value, backup.dayNotes) { it.date }
        backup.targets?.let { setTargets(it) }
        persistFoods()
        persistDiary()
        persistWeights()
    }

    private fun <T> mergeBy(current: List<T>, incoming: List<T>, key: (T) -> String): List<T> {
        val byKey = LinkedHashMap<String, T>()
        current.forEach { byKey[key(it)] = it }
        incoming.forEach { byKey[key(it)] = it }
        return byKey.values.toList()
    }

    // ---------- 寫入 ----------

    // 快照在取得鎖之後才讀：若在鎖外先取，兩次連續異動可能以相反順序寫入，
    // 讓較舊的內容蓋掉較新的內容。
    private fun persistFoods() {
        scope.launch {
            writeMutex.withLock {
                val content = json.encodeToString(ListSerializer(Food.serializer()), _foods.value)
                writeAtomically(foodsFile, content)
            }
        }
    }

    private fun persistDiary() {
        scope.launch {
            writeMutex.withLock {
                val file = DiaryFile(entries = _entries.value, dayNotes = _dayNotes.value)
                writeAtomically(diaryFile, json.encodeToString(DiaryFile.serializer(), file))
            }
        }
    }

    private fun persistWeights() {
        scope.launch {
            writeMutex.withLock {
                val content = json.encodeToString(ListSerializer(WeightEntry.serializer()), _weights.value)
                writeAtomically(weightsFile, content)
            }
        }
    }

    /** 呼叫端必須已持有 writeMutex */
    private fun writeAtomically(target: File, content: String) {
        try {
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeText(content)
            if (!tmp.renameTo(target)) {
                target.writeText(content)
                tmp.delete()
            }
        } catch (t: Throwable) {
            // 寫入失敗不影響 App 運作（記憶體內資料仍然正確）
        }
    }

    private companion object {
        const val FILE_FOODS = "foods.json"
        const val FILE_DIARY = "diary.json"
        const val FILE_WEIGHTS = "weights.json"
        const val ASSET_SEED = "seed_foods.json"

        const val PREFS_NAME = "routina_bite"
        const val KEY_TARGET_KCAL = "target_kcal"
        const val KEY_TARGET_PROTEIN = "target_protein"
        const val KEY_TARGET_FAT = "target_fat"
        const val KEY_TARGET_CARBS = "target_carbs"
    }
}
