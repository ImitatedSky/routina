package com.routina.bite.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.routina.bite.R
import com.routina.bite.data.frequentFoods
import com.routina.bite.data.groupByCategory
import com.routina.bite.data.recentFoods
import com.routina.bite.data.searchFoods
import com.routina.bite.model.Food
import com.routina.bite.model.Meal
import com.routina.bite.model.Nutrients

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodScreen(
    viewModel: BiteViewModel,
    date: String,
    initialMeal: Meal,
    onDone: () -> Unit,
    onEditFood: (String) -> Unit,
    onNewFood: () -> Unit,
    onOpenLibrary: () -> Unit
) {
    val foods by viewModel.foods.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val expanded by viewModel.expandedCategories.collectAsStateWithLifecycle()

    var meal by remember { mutableStateOf(initialMeal) }
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Food?>(null) }
    var quickAdd by remember { mutableStateOf(false) }

    val frequent = frequentFoods(entries, foods)
    val recent = recentFoods(entries, foods)
    val results = searchFoods(foods, query)
    val groups = remember(foods) { groupByCategory(foods) }

    val editLabel = stringResource(R.string.action_edit)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { MealPicker(selected = meal, onSelect = { meal = it }) }

            if (frequent.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.add_frequent)) }
                item { FoodChips(frequent) { picked = it } }
            }
            if (recent.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.add_recent)) }
                item { FoodChips(recent) { picked = it } }
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.add_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { quickAdd = true }) {
                        Text(stringResource(R.string.add_quick))
                    }
                    OutlinedButton(onClick = onNewFood) {
                        Text(stringResource(R.string.add_new_food))
                    }
                    OutlinedButton(onClick = onOpenLibrary) {
                        Text(stringResource(R.string.nav_library))
                    }
                }
            }

            item {
                SectionTitle(
                    if (query.isBlank()) {
                        stringResource(R.string.add_all_foods)
                    } else {
                        stringResource(R.string.add_results)
                    }
                )
            }

            if (foods.isEmpty()) {
                item { EmptyHint(stringResource(R.string.add_library_empty)) }
            } else if (query.isBlank()) {
                // 沒搜尋就依分類分組，摺疊狀態與食物庫頁共用
                foodGroups(
                    groups = groups,
                    expanded = expanded,
                    onToggle = { category ->
                        viewModel.setCategoryExpanded(category, category !in expanded)
                    },
                    onFoodClick = { picked = it },
                    rowMenu = { food -> listOf(editLabel to { onEditFood(food.id) }) }
                )
            } else if (results.isEmpty()) {
                item { EmptyHint(stringResource(R.string.add_no_results)) }
            } else {
                items(results, key = { it.id }) { food ->
                    FoodRow(
                        food = food,
                        onClick = { picked = food },
                        menu = listOf(editLabel to { onEditFood(food.id) })
                    )
                }
            }
        }
    }

    picked?.let { food ->
        AmountDialog(
            food = food,
            onAdd = { servings ->
                viewModel.addFoodEntry(date, meal, food, servings)
                picked = null
                onDone()
            },
            onDismiss = { picked = null }
        )
    }

    if (quickAdd) {
        QuickAddDialog(
            onAdd = { name, nutrients ->
                viewModel.addQuickEntry(date, meal, name, nutrients)
                quickAdd = false
                onDone()
            },
            onDismiss = { quickAdd = false }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun FoodChips(foods: List<Food>, onClick: (Food) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(foods, key = { it.id }) { food ->
            AssistChip(onClick = { onClick(food) }, label = { Text(food.name) })
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * 份數／公克輸入。份數是唯一真值，公克只是另一種輸入方式（D5），
 * 所以改任一邊就把另一邊算出來。
 */
@Composable
private fun AmountDialog(food: Food, onAdd: (Double) -> Unit, onDismiss: () -> Unit) {
    val servingGrams = food.servingGrams
    var servings by remember { mutableStateOf("1") }
    var grams by remember {
        mutableStateOf(if (servingGrams == null) "" else formatGrams(servingGrams))
    }
    // 最後改的是公克欄時，份數要用公克直接除回去算。份數欄顯示的是四捨五入過的文字，
    // 拿它當真值會把 150 g 存成 150.6 g。
    var gramsEditedLast by remember { mutableStateOf(false) }
    val amount = if (gramsEditedLast && servingGrams != null && servingGrams > 0.0) {
        grams.toDoubleOrNull()?.let { it / servingGrams }
    } else {
        servings.toDoubleOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(food.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    value = servings,
                    onValueChange = { input ->
                        servings = input
                        gramsEditedLast = false
                        if (servingGrams != null) {
                            val value = input.toDoubleOrNull()
                            grams = if (value == null) "" else formatGrams(value * servingGrams)
                        }
                    },
                    label = stringResource(R.string.add_servings),
                    modifier = Modifier.fillMaxWidth()
                )
                if (servingGrams != null && servingGrams > 0.0) {
                    NumberField(
                        value = grams,
                        onValueChange = { input ->
                            grams = input
                            gramsEditedLast = true
                            val value = input.toDoubleOrNull()
                            servings = if (value == null) "" else formatAmount(value / servingGrams)
                        },
                        label = stringResource(R.string.add_grams),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.add_amount_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = previewLine(food.nutrients, amount ?: 0.0),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null && amount > 0.0,
                onClick = { amount?.let(onAdd) }
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun previewLine(perServing: Nutrients, servings: Double): String {
    val total = perServing * servings
    return formatKcal(total.kcal).toString() + " " + stringResource(R.string.unit_kcal) +
        " · " + stringResource(R.string.macro_protein) + " " + formatGrams(total.protein) + " g"
}

@Composable
private fun QuickAddDialog(
    onAdd: (String, Nutrients) -> Unit,
    onDismiss: () -> Unit
) {
    val defaultName = stringResource(R.string.add_quick)
    var name by remember { mutableStateOf(defaultName) }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    val kcalValue = kcal.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_quick)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                NumberField(
                    value = kcal,
                    onValueChange = { kcal = it },
                    label = stringResource(R.string.field_kcal),
                    modifier = Modifier.fillMaxWidth()
                )
                NumberField(
                    value = protein,
                    onValueChange = { protein = it },
                    label = stringResource(R.string.field_protein),
                    modifier = Modifier.fillMaxWidth()
                )
                NumberField(
                    value = fat,
                    onValueChange = { fat = it },
                    label = stringResource(R.string.field_fat),
                    modifier = Modifier.fillMaxWidth()
                )
                NumberField(
                    value = carbs,
                    onValueChange = { carbs = it },
                    label = stringResource(R.string.field_carbs),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = kcalValue != null && kcalValue > 0.0,
                onClick = {
                    onAdd(
                        name.trim().ifEmpty { defaultName },
                        Nutrients(
                            kcal = kcalValue ?: 0.0,
                            protein = protein.toDoubleOrNull() ?: 0.0,
                            fat = fat.toDoubleOrNull() ?: 0.0,
                            carbs = carbs.toDoubleOrNull() ?: 0.0
                        )
                    )
                }
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
