package com.routina.bite.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.routina.bite.R
import com.routina.bite.data.defaultMeal
import com.routina.bite.data.entriesOn
import com.routina.bite.data.todayDate
import com.routina.bite.data.totalOf
import com.routina.bite.model.DiaryEntry
import com.routina.bite.model.Meal
import com.routina.bite.model.Targets
import com.routina.bite.ui.theme.MacroColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    viewModel: BiteViewModel,
    onAdd: (String, Meal) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenWeights: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val date by viewModel.selectedDate.collectAsStateWithLifecycle()
    val allEntries by viewModel.entries.collectAsStateWithLifecycle()
    val dayNotes by viewModel.dayNotes.collectAsStateWithLifecycle()
    val targets by viewModel.targets.collectAsStateWithLifecycle()

    val entries = entriesOn(allEntries, date)
    val note = dayNotes.firstOrNull { it.date == date }?.note.orEmpty()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf<DiaryEntry?>(null) }
    var deleting by remember { mutableStateOf<DiaryEntry?>(null) }
    var editingNote by remember { mutableStateOf(false) }

    val copiedFormat = stringResource(R.string.today_copy_done)
    val emptyYesterday = stringResource(R.string.today_copy_empty)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenLibrary) {
                        Icon(Icons.Default.Restaurant, stringResource(R.string.nav_library))
                    }
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Default.History, stringResource(R.string.nav_history))
                    }
                    IconButton(onClick = onOpenWeights) {
                        Icon(Icons.Default.MonitorWeight, stringResource(R.string.nav_weights))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, stringResource(R.string.nav_settings))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAdd(date, defaultMeal()) }) {
                Icon(Icons.Default.Add, stringResource(R.string.today_add_entry))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            // 底部留白讓最後一筆不會被 FAB 蓋住
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DateBar(
                    date = date,
                    onPrevious = { viewModel.shiftSelectedDate(-1) },
                    onNext = { viewModel.shiftSelectedDate(1) },
                    onToday = { viewModel.backToToday() }
                )
            }

            item { SummaryCard(entries = entries, targets = targets) }

            item {
                NoteCard(note = note, onClick = { editingNote = true })
            }

            item {
                TextButton(onClick = {
                    val copied = viewModel.copyYesterday(date)
                    scope.launch {
                        snackbar.showSnackbar(
                            if (copied == 0) emptyYesterday else String.format(copiedFormat, copied)
                        )
                    }
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Text(
                        text = stringResource(R.string.today_copy_yesterday),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            // 未分餐（匯入的舊資料）只有在有資料時才出現，而且放最前面：
            // 那種日子四個餐別都是空的，排在後面會讓整天看起來像沒紀錄
            val unassigned = entries.filter { it.meal == null }
            if (unassigned.isNotEmpty()) {
                mealSection(
                    meal = null,
                    entries = unassigned,
                    onAdd = null,
                    onClick = { editing = it },
                    onDelete = { deleting = it }
                )
            }
            // 四個餐別固定顯示（空的也在，才能直接加進那一餐）
            Meal.entries.forEach { meal ->
                mealSection(
                    meal = meal,
                    entries = entries.filter { it.meal == meal },
                    onAdd = { onAdd(date, meal) },
                    onClick = { editing = it },
                    onDelete = { deleting = it }
                )
            }

            if (entries.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.today_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    editing?.let { entry ->
        EntryEditDialog(
            entry = entry,
            onSave = { servings, meal ->
                viewModel.updateEntry(entry, servings, meal)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    deleting?.let { entry ->
        ConfirmDialog(
            title = stringResource(R.string.entry_delete_title),
            message = stringResource(R.string.entry_delete_message),
            onConfirm = { viewModel.deleteEntry(entry.id) },
            onDismiss = { deleting = null }
        )
    }

    if (editingNote) {
        NoteDialog(
            initial = note,
            onSave = {
                viewModel.setDayNote(date, it)
                editingNote = false
            },
            onDismiss = { editingNote = false }
        )
    }
}

@Composable
private fun DateBar(
    date: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.ChevronLeft, stringResource(R.string.today_prev_day))
        }
        Text(
            text = formatDayLabel(date),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        if (date != todayDate()) {
            TextButton(onClick = onToday) { Text(stringResource(R.string.today_back_to_today)) }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Default.ChevronRight, stringResource(R.string.today_next_day))
        }
    }
}

@Composable
private fun SummaryCard(entries: List<DiaryEntry>, targets: Targets) {
    val total = totalOf(entries)
    val eaten = formatKcal(total.kcal)
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                KcalCell(stringResource(R.string.today_eaten), eaten, Modifier.weight(1f))
                KcalCell(stringResource(R.string.today_target), targets.kcal, Modifier.weight(1f))
                KcalCell(stringResource(R.string.today_remaining), targets.kcal - eaten, Modifier.weight(1f))
            }
            LinearProgressIndicator(
                progress = { progressOf(total.kcal, targets.kcal.toDouble()) },
                modifier = Modifier.fillMaxWidth()
            )
            MacroRow(
                label = stringResource(R.string.macro_protein),
                value = total.protein,
                target = targets.protein,
                color = MacroColors.Protein
            )
            MacroRow(
                label = stringResource(R.string.macro_fat),
                value = total.fat,
                target = targets.fat,
                color = MacroColors.Fat
            )
            MacroRow(
                label = stringResource(R.string.macro_carbs),
                value = total.carbs,
                target = targets.carbs,
                color = MacroColors.Carbs
            )
        }
    }
}

@Composable
private fun KcalCell(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MacroRow(label: String, value: Double, target: Int?, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (target == null) {
                    stringResource(R.string.today_macro_plain, formatGrams(value))
                } else {
                    stringResource(R.string.today_macro_with_target, formatGrams(value), target)
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
        // 沒設目標就不畫進度條：畫一條不知道終點在哪的條子只會誤導
        if (target != null) {
            LinearProgressIndicator(
                progress = { progressOf(value, target.toDouble()) },
                color = color,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun progressOf(value: Double, target: Double): Float =
    if (target <= 0.0) 0f else (value / target).coerceIn(0.0, 1.0).toFloat()

@Composable
private fun NoteCard(note: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Text(
            text = note.ifEmpty { stringResource(R.string.note_empty) },
            style = MaterialTheme.typography.bodyMedium,
            color = if (note.isEmpty()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(16.dp)
        )
    }
}

/** 一個餐別的標題與它底下的紀錄。meal 為 null 就是「未分餐」 */
private fun LazyListScope.mealSection(
    meal: Meal?,
    entries: List<DiaryEntry>,
    onAdd: (() -> Unit)?,
    onClick: (DiaryEntry) -> Unit,
    onDelete: (DiaryEntry) -> Unit
) {
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = mealLabel(meal),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(
                    R.string.today_section_subtotal,
                    formatKcal(totalOf(entries).kcal)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onAdd != null) {
                IconButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, stringResource(R.string.today_add_entry))
                }
            }
        }
    }
    items(entries, key = { it.id }) { entry ->
        EntryRow(entry = entry, onClick = { onClick(entry) }, onDelete = { onDelete(entry) })
    }
}

@Composable
private fun EntryRow(entry: DiaryEntry, onClick: () -> Unit, onDelete: () -> Unit) {
    val deleteLabel = stringResource(R.string.action_delete)
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = entry.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = amountLabel(entry),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatKcal(entry.total.kcal).toString(),
                style = MaterialTheme.typography.bodyLarge
            )
            OverflowMenu(listOf(deleteLabel to onDelete))
        }
    }
}

@Composable
private fun amountLabel(entry: DiaryEntry): String {
    val grams = entry.servingGrams
    return if (grams == null) {
        stringResource(R.string.entry_servings, formatAmount(entry.servings))
    } else {
        stringResource(
            R.string.entry_servings_grams,
            formatAmount(entry.servings),
            formatGrams(entry.servings * grams)
        )
    }
}

@Composable
private fun EntryEditDialog(
    entry: DiaryEntry,
    onSave: (Double, Meal?) -> Unit,
    onDismiss: () -> Unit
) {
    var servings by remember { mutableStateOf(formatAmount(entry.servings)) }
    var meal by remember { mutableStateOf(entry.meal ?: defaultMeal()) }
    val amount = servings.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.entry_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(entry.name, style = MaterialTheme.typography.bodyLarge)
                NumberField(
                    value = servings,
                    onValueChange = { servings = it },
                    label = stringResource(R.string.add_servings),
                    modifier = Modifier.fillMaxWidth()
                )
                MealPicker(selected = meal, onSelect = { meal = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null && amount > 0.0,
                onClick = { onSave(amount ?: entry.servings, meal) }
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun NoteDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.note_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.note_hint)) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
