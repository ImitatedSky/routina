package com.routina.bite.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.routina.bite.BiteApp
import com.routina.bite.data.BackupCodec
import com.routina.bite.data.entriesOn
import com.routina.bite.data.shiftDate
import com.routina.bite.data.todayDate
import com.routina.bite.model.DayNote
import com.routina.bite.model.DiaryEntry
import com.routina.bite.model.Food
import com.routina.bite.model.Meal
import com.routina.bite.model.Nutrients
import com.routina.bite.model.Targets
import com.routina.bite.model.WeightEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** 匯入的結果：成功時回目前的總筆數，失敗時回拒收原因 */
sealed interface ImportOutcome {
    data class Ok(val foods: Int, val entries: Int, val weights: Int, val notes: Int) : ImportOutcome
    data class Failed(val reason: BackupCodec.Reject) : ImportOutcome
}

class BiteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as BiteApp).repository

    val foods: StateFlow<List<Food>> = repository.foods
    val entries: StateFlow<List<DiaryEntry>> = repository.entries
    val dayNotes: StateFlow<List<DayNote>> = repository.dayNotes
    val weights: StateFlow<List<WeightEntry>> = repository.weights
    val targets: StateFlow<Targets> = repository.targets

    /** 目前被展開的分類（未分類是空字串）。食物庫頁與新增紀錄頁共用同一份 */
    val expandedCategories: StateFlow<Set<String>> = repository.expandedCategories

    // 目前看的是哪一天。歷史頁選日期後也是改這個值，所以 today 這條路由不需要參數
    private val _selectedDate = MutableStateFlow(todayDate())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    fun selectDate(date: String) {
        _selectedDate.value = date
    }

    fun shiftSelectedDate(days: Long) {
        _selectedDate.value = shiftDate(_selectedDate.value, days)
    }

    fun backToToday() {
        _selectedDate.value = todayDate()
    }

    // ---------- 食物庫 ----------

    fun findFood(id: String): Food? = repository.findFood(id)

    fun saveFood(food: Food) = repository.upsertFood(food)

    fun deleteFood(id: String) = repository.deleteFood(id)

    fun toggleFavorite(food: Food) = repository.upsertFood(food.copy(favorite = !food.favorite))

    fun renameCategory(from: String, to: String) = repository.renameCategory(from, to)

    fun setCategoryExpanded(category: String, expanded: Boolean) =
        repository.setCategoryExpanded(category, expanded)

    fun newFoodId(): String = UUID.randomUUID().toString()

    // ---------- 紀錄 ----------

    fun findEntry(id: String): DiaryEntry? = repository.findEntry(id)

    fun addFoodEntry(date: String, meal: Meal, food: Food, servings: Double) {
        repository.addEntry(
            DiaryEntry(
                id = UUID.randomUUID().toString(),
                date = date,
                meal = meal,
                foodId = food.id,
                name = food.name,
                servings = servings,
                servingGrams = food.servingGrams,
                perServing = food.nutrients,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    /** 快速輸入：不建食物，直接記一筆（一份＝輸入的量） */
    fun addQuickEntry(date: String, meal: Meal, name: String, nutrients: Nutrients) {
        repository.addEntry(
            DiaryEntry(
                id = UUID.randomUUID().toString(),
                date = date,
                meal = meal,
                foodId = null,
                name = name,
                servings = 1.0,
                servingGrams = null,
                perServing = nutrients,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    fun updateEntry(entry: DiaryEntry, servings: Double, meal: Meal?) {
        repository.updateEntry(entry.copy(servings = servings, meal = meal))
    }

    fun deleteEntry(id: String) = repository.deleteEntry(id)

    /**
     * 把前一天的每一筆複製到 [date]，id 與時間都重新給。
     * 回傳複製的筆數，0 表示昨天沒有紀錄。
     */
    fun copyYesterday(date: String): Int {
        val source = entriesOn(entries.value, shiftDate(date, -1))
        if (source.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val copies = source.mapIndexed { index, entry ->
            entry.copy(
                id = UUID.randomUUID().toString(),
                date = date,
                // 加 index 讓複製出來的順序與昨天一致
                createdAt = now + index
            )
        }
        repository.addEntries(copies)
        return copies.size
    }

    fun setDayNote(date: String, note: String) = repository.setDayNote(date, note)

    fun dayNote(date: String): String =
        dayNotes.value.firstOrNull { it.date == date }?.note.orEmpty()

    // ---------- 體重 ----------

    fun saveWeight(entry: WeightEntry) = repository.upsertWeight(entry)

    fun deleteWeight(id: String) = repository.deleteWeight(id)

    fun newWeightId(): String = UUID.randomUUID().toString()

    // ---------- 目標 ----------

    fun setTargets(targets: Targets) = repository.setTargets(targets)

    // ---------- 備份 ----------

    fun exportBackup(uri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val content = BackupCodec.encode(repository.buildBackup())
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(content.toByteArray())
                    } ?: error("no output stream")
                }.isSuccess
            }
            onDone(ok)
        }
    }

    fun importBackup(uri: Uri, onDone: (ImportOutcome) -> Unit) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (text == null) {
                onDone(ImportOutcome.Failed(BackupCodec.Reject.UNREADABLE))
                return@launch
            }
            when (val parsed = BackupCodec.parse(text)) {
                is BackupCodec.Parsed.Failed -> onDone(ImportOutcome.Failed(parsed.reason))
                is BackupCodec.Parsed.Ok -> {
                    repository.applyBackup(parsed.backup)
                    onDone(
                        ImportOutcome.Ok(
                            foods = foods.value.size,
                            entries = entries.value.size,
                            weights = weights.value.size,
                            notes = dayNotes.value.size
                        )
                    )
                }
            }
        }
    }
}
