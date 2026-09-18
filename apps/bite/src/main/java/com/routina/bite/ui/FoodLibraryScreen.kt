package com.routina.bite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.routina.bite.data.groupByCategory
import com.routina.bite.data.searchFoods
import com.routina.bite.model.Food

/** 整理食物庫的地方：依分類分組、可摺疊，每一列可編輯／切換常用／刪除 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodLibraryScreen(
    viewModel: BiteViewModel,
    onEditFood: (String) -> Unit,
    onNewFood: () -> Unit,
    onBack: () -> Unit
) {
    val foods by viewModel.foods.collectAsStateWithLifecycle()
    val expanded by viewModel.expandedCategories.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Food?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }

    val groups = remember(foods) { groupByCategory(foods) }
    val results = searchFoods(foods, query)

    val editLabel = stringResource(R.string.action_edit)
    val favoriteLabel = stringResource(R.string.action_toggle_favorite)
    val deleteLabel = stringResource(R.string.action_delete)
    val renameLabel = stringResource(R.string.category_rename_title)

    fun rowMenu(food: Food): List<Pair<String, () -> Unit>> = listOf(
        editLabel to { onEditFood(food.id) },
        favoriteLabel to { viewModel.toggleFavorite(food) },
        deleteLabel to { deleting = food }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewFood) {
                Icon(Icons.Default.Add, stringResource(R.string.add_new_food))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            // 底部留白讓最後一筆不會被 FAB 蓋住
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.add_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (foods.isEmpty()) {
                item { HintText(stringResource(R.string.library_empty)) }
            } else if (query.isBlank()) {
                foodGroups(
                    groups = groups,
                    expanded = expanded,
                    onToggle = { category ->
                        viewModel.setCategoryExpanded(category, category !in expanded)
                    },
                    onFoodClick = { onEditFood(it.id) },
                    headerMenu = { group -> listOf(renameLabel to { renaming = group.category }) },
                    rowMenu = { food -> rowMenu(food) }
                )
            } else if (results.isEmpty()) {
                item { HintText(stringResource(R.string.add_no_results)) }
            } else {
                items(results, key = { it.id }) { food ->
                    FoodRow(
                        food = food,
                        onClick = { onEditFood(food.id) },
                        menu = rowMenu(food)
                    )
                }
            }
        }
    }

    deleting?.let { food ->
        ConfirmDialog(
            title = stringResource(R.string.food_delete_title),
            message = stringResource(R.string.food_delete_message),
            onConfirm = { viewModel.deleteFood(food.id) },
            onDismiss = { deleting = null }
        )
    }

    renaming?.let { category ->
        RenameCategoryDialog(
            current = category,
            onConfirm = { viewModel.renameCategory(category, it) },
            onDismiss = { renaming = null }
        )
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
