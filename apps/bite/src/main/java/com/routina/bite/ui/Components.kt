package com.routina.bite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.routina.bite.R
import com.routina.bite.model.Meal

/** 一律用這個對話框做刪除確認；App 裡沒有滑動刪除 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String? = null,
    confirmLabel: String = stringResource(R.string.action_delete),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = message?.let { { Text(it) } },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/** ⋯ 溢位選單。[items] 是「顯示文字 → 做什麼」 */
@Composable
fun OverflowMenu(items: List<Pair<String, () -> Unit>>) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        items.forEach { (label, action) ->
            DropdownMenuItem(
                text = { Text(label) },
                onClick = {
                    expanded = false
                    action()
                }
            )
        }
    }
}

/** 數字輸入。只收得進數字與小數點，省掉一堆解析失敗的情況 */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it.isDigit() || it == '.' }) },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = if (isError && errorText != null) {
            { Text(errorText, color = MaterialTheme.colorScheme.error) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

@Composable
fun mealLabel(meal: Meal?): String = when (meal) {
    Meal.BREAKFAST -> stringResource(R.string.meal_breakfast)
    Meal.LUNCH -> stringResource(R.string.meal_lunch)
    Meal.DINNER -> stringResource(R.string.meal_dinner)
    Meal.SNACK -> stringResource(R.string.meal_snack)
    null -> stringResource(R.string.meal_none)
}

/** 四個餐別的選擇列。已經分好餐的紀錄不提供「未分餐」這個選項 */
@Composable
fun MealPicker(
    selected: Meal,
    onSelect: (Meal) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Meal.entries.forEach { meal ->
            FilterChip(
                selected = meal == selected,
                onClick = { onSelect(meal) },
                label = { Text(mealLabel(meal)) }
            )
        }
    }
}
