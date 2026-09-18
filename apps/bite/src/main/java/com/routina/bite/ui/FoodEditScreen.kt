package com.routina.bite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.routina.bite.R
import com.routina.bite.data.allCategories
import com.routina.bite.model.Food
import com.routina.bite.model.Nutrients

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodEditScreen(
    viewModel: BiteViewModel,
    foodId: String?,
    onDone: () -> Unit
) {
    val existing = remember(foodId) { foodId?.let { viewModel.findFood(it) } }

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var servingGrams by remember {
        mutableStateOf(existing?.servingGrams?.let { formatGrams(it) }.orEmpty())
    }
    var kcal by remember { mutableStateOf(existing?.nutrients?.kcal?.let { formatKcal(it).toString() }.orEmpty()) }
    var protein by remember { mutableStateOf(existing.gramsOf { it.protein }) }
    var fat by remember { mutableStateOf(existing.gramsOf { it.fat }) }
    var satFat by remember { mutableStateOf(existing.gramsOf { it.satFat }) }
    var transFat by remember { mutableStateOf(existing.gramsOf { it.transFat }) }
    var carbs by remember { mutableStateOf(existing.gramsOf { it.carbs }) }
    var sugar by remember { mutableStateOf(existing.gramsOf { it.sugar }) }
    var sodium by remember { mutableStateOf(existing.gramsOf { it.sodium }) }
    var cholesterol by remember { mutableStateOf(existing.gramsOf { it.cholesterol }) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var category by remember { mutableStateOf(existing?.category.orEmpty()) }
    var favorite by remember { mutableStateOf(existing?.favorite ?: false) }

    val foods by viewModel.foods.collectAsStateWithLifecycle()
    val categories = allCategories(foods)

    var showErrors by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val nameError = name.isBlank()
    val kcalError = kcal.toDoubleOrNull() == null
    val deleteLabel = stringResource(R.string.action_delete)

    fun save() {
        showErrors = true
        if (nameError || kcalError) return
        viewModel.saveFood(
            Food(
                id = existing?.id ?: viewModel.newFoodId(),
                name = name.trim(),
                servingGrams = servingGrams.toDoubleOrNull()?.takeIf { it > 0.0 },
                nutrients = Nutrients(
                    kcal = kcal.toDoubleOrNull() ?: 0.0,
                    protein = protein.toDoubleOrNull() ?: 0.0,
                    fat = fat.toDoubleOrNull() ?: 0.0,
                    satFat = satFat.toDoubleOrNull() ?: 0.0,
                    transFat = transFat.toDoubleOrNull() ?: 0.0,
                    carbs = carbs.toDoubleOrNull() ?: 0.0,
                    sugar = sugar.toDoubleOrNull() ?: 0.0,
                    sodium = sodium.toDoubleOrNull() ?: 0.0,
                    cholesterol = cholesterol.toDoubleOrNull() ?: 0.0
                ),
                note = note.trim(),
                category = category.trim(),
                favorite = favorite,
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
        )
        onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (existing == null) R.string.food_new_title else R.string.food_edit_title
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(onClick = { save() }) { Text(stringResource(R.string.action_save)) }
                    if (existing != null) {
                        OverflowMenu(listOf(deleteLabel to { deleting = true }))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.field_name)) },
                singleLine = true,
                isError = showErrors && nameError,
                supportingText = if (showErrors && nameError) {
                    { Text(stringResource(R.string.error_name_required), color = MaterialTheme.colorScheme.error) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth()
            )
            NumberField(
                value = servingGrams,
                onValueChange = { servingGrams = it },
                label = stringResource(R.string.field_serving_grams),
                modifier = Modifier.fillMaxWidth()
            )
            NumberField(
                value = kcal,
                onValueChange = { kcal = it },
                label = stringResource(R.string.field_kcal),
                isError = showErrors && kcalError,
                errorText = stringResource(R.string.error_kcal_required),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.food_blank_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            NumberField(protein, { protein = it }, stringResource(R.string.field_protein), Modifier.fillMaxWidth())
            NumberField(fat, { fat = it }, stringResource(R.string.field_fat), Modifier.fillMaxWidth())
            NumberField(satFat, { satFat = it }, stringResource(R.string.field_sat_fat), Modifier.fillMaxWidth())
            NumberField(transFat, { transFat = it }, stringResource(R.string.field_trans_fat), Modifier.fillMaxWidth())
            NumberField(carbs, { carbs = it }, stringResource(R.string.field_carbs), Modifier.fillMaxWidth())
            NumberField(sugar, { sugar = it }, stringResource(R.string.field_sugar), Modifier.fillMaxWidth())
            NumberField(sodium, { sodium = it }, stringResource(R.string.field_sodium), Modifier.fillMaxWidth())
            NumberField(
                cholesterol,
                { cholesterol = it },
                stringResource(R.string.field_cholesterol),
                Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.field_note)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text(stringResource(R.string.field_category)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            // 既有分類直接點，省得自己打字打錯一個字就多出一組
            if (categories.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories, key = { it }) { name ->
                        FilterChip(
                            selected = name == category,
                            onClick = { category = if (name == category) "" else name },
                            label = { Text(name) }
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.field_favorite),
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = favorite, onCheckedChange = { favorite = it })
            }
        }
    }

    if (deleting && existing != null) {
        ConfirmDialog(
            title = stringResource(R.string.food_delete_title),
            message = stringResource(R.string.food_delete_message),
            onConfirm = {
                viewModel.deleteFood(existing.id)
                onDone()
            },
            onDismiss = { deleting = false }
        )
    }
}

/** 空白欄位就是 0，所以 0 顯示成空字串，使用者不必先刪掉一堆 0 */
private fun Food?.gramsOf(pick: (Nutrients) -> Double): String {
    val value = this?.nutrients?.let(pick) ?: return ""
    return if (value == 0.0) "" else formatGrams(value)
}
