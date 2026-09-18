package com.routina.bite.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.routina.bite.R
import com.routina.bite.data.FoodGroup
import com.routina.bite.model.Food

/**
 * 依分類分組的可摺疊食物清單。食物庫頁與新增紀錄頁共用，
 * 兩邊只有「點一列做什麼」與「⋯ 選單放什麼」不同，所以那兩件事用參數傳進來。
 *
 * [expanded] 放的是分類名稱本身（未分類是空字串）。
 * [headerMenu] 為 null 就不畫標頭的 ⋯（新增紀錄頁不提供改名分類）。
 */
fun LazyListScope.foodGroups(
    groups: List<FoodGroup>,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    onFoodClick: (Food) -> Unit,
    headerMenu: ((FoodGroup) -> List<Pair<String, () -> Unit>>)? = null,
    rowMenu: ((Food) -> List<Pair<String, () -> Unit>>)? = null
) {
    groups.forEach { group ->
        val isExpanded = group.category in expanded
        item(key = "header-" + group.category) {
            CategoryHeader(
                group = group,
                expanded = isExpanded,
                onToggle = { onToggle(group.category) },
                // 未分類不是真的分類，不給改名
                menu = if (group.category.isEmpty()) null else headerMenu?.invoke(group)
            )
        }
        if (isExpanded) {
            items(group.foods, key = { it.id }) { food ->
                FoodRow(
                    food = food,
                    onClick = { onFoodClick(food) },
                    menu = rowMenu?.invoke(food)
                )
            }
        }
    }
}

@Composable
private fun CategoryHeader(
    group: FoodGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    menu: List<Pair<String, () -> Unit>>?
) {
    val name = group.category.ifEmpty { stringResource(R.string.category_none) }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.category_collapse else R.string.category_expand
                )
            )
            Text(
                text = stringResource(R.string.category_group_header, name, group.foods.size),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
            if (menu != null) OverflowMenu(menu)
        }
    }
}

/** 食物庫的一列。[menu] 為 null 就不畫 ⋯ */
@Composable
fun FoodRow(food: Food, onClick: () -> Unit, menu: List<Pair<String, () -> Unit>>?) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = food.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = foodSubtitle(food),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (food.favorite) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = stringResource(R.string.field_favorite),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (menu != null) {
                OverflowMenu(menu)
            } else {
                Spacer(Modifier.width(16.dp))
            }
        }
    }
}

@Composable
fun foodSubtitle(food: Food): String {
    val kcal = formatKcal(food.nutrients.kcal).toString() + " " + stringResource(R.string.unit_kcal)
    val grams = food.servingGrams ?: return kcal
    return kcal + " · " + stringResource(R.string.add_per_serving, formatGrams(grams))
}

/** 改分類名稱。留白就是把整組食物變成未分類 */
@Composable
fun RenameCategoryDialog(
    current: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.category_rename_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_category)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.category_rename_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm(name.trim()) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
