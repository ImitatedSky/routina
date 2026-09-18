package com.routina.bite.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.routina.bite.R
import com.routina.bite.data.isValidDate
import com.routina.bite.data.todayDate
import com.routina.bite.model.WeightEntry
import com.routina.bite.model.WeightSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightScreen(
    viewModel: BiteViewModel,
    onBack: () -> Unit
) {
    val weights by viewModel.weights.collectAsStateWithLifecycle()
    val sorted = weights.sortedByDescending { it.date }

    var editing by remember { mutableStateOf<WeightEntry?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<WeightEntry?>(null) }
    val deleteLabel = stringResource(R.string.action_delete)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.weight_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Default.Add, stringResource(R.string.weight_new_title))
            }
        }
    ) { padding ->
        if (sorted.isEmpty()) {
            Text(
                text = stringResource(R.string.weight_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(padding).padding(16.dp)
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            // 底部留白讓最後一筆不會被 FAB 蓋住
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sorted, key = { it.id }) { entry ->
                Card(modifier = Modifier.fillMaxWidth().clickable { editing = entry }) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = formatDayLabel(entry.date),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = weightSubtitle(entry),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = stringResource(R.string.weight_row, formatGrams(entry.kg)),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        OverflowMenu(listOf(deleteLabel to { deleting = entry }))
                    }
                }
            }
        }
    }

    if (creating) {
        WeightDialog(
            entry = null,
            onSave = { viewModel.saveWeight(it); creating = false },
            onDismiss = { creating = false },
            newId = { viewModel.newWeightId() }
        )
    }

    editing?.let { entry ->
        WeightDialog(
            entry = entry,
            onSave = { viewModel.saveWeight(it); editing = null },
            onDismiss = { editing = null },
            newId = { viewModel.newWeightId() }
        )
    }

    deleting?.let { entry ->
        ConfirmDialog(
            title = stringResource(R.string.weight_delete_title),
            onConfirm = { viewModel.deleteWeight(entry.id) },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun weightSubtitle(entry: WeightEntry): String {
    val source = stringResource(
        if (entry.source == WeightSource.INBODY) R.string.source_inbody else R.string.source_home_scale
    )
    val bodyFat = entry.bodyFatPct?.let {
        " · " + stringResource(R.string.weight_row_body_fat, formatGrams(it))
    }.orEmpty()
    val note = entry.note.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
    return source + bodyFat + note
}

@Composable
private fun WeightDialog(
    entry: WeightEntry?,
    onSave: (WeightEntry) -> Unit,
    onDismiss: () -> Unit,
    newId: () -> String
) {
    var date by remember { mutableStateOf(entry?.date ?: todayDate()) }
    var kg by remember { mutableStateOf(entry?.kg?.let { formatGrams(it) }.orEmpty()) }
    var bodyFat by remember { mutableStateOf(entry?.bodyFatPct?.let { formatGrams(it) }.orEmpty()) }
    var source by remember { mutableStateOf(entry?.source ?: WeightSource.HOME_SCALE) }
    var note by remember { mutableStateOf(entry?.note.orEmpty()) }

    val dateOk = isValidDate(date)
    val kgValue = kg.toDoubleOrNull()
    val kgOk = kgValue != null && kgValue > 0.0
    val bodyFatValue = bodyFat.toDoubleOrNull()
    val bodyFatOk = bodyFat.isBlank() || (bodyFatValue != null && bodyFatValue in 0.0..100.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (entry == null) R.string.weight_new_title else R.string.weight_edit_title
                )
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text(stringResource(R.string.field_date)) },
                    singleLine = true,
                    isError = !dateOk,
                    supportingText = if (!dateOk) {
                        { Text(stringResource(R.string.error_date_invalid), color = MaterialTheme.colorScheme.error) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                NumberField(
                    value = kg,
                    onValueChange = { kg = it },
                    label = stringResource(R.string.field_kg),
                    isError = kg.isNotBlank() && !kgOk,
                    errorText = stringResource(R.string.error_kg_invalid),
                    modifier = Modifier.fillMaxWidth()
                )
                NumberField(
                    value = bodyFat,
                    onValueChange = { bodyFat = it },
                    label = stringResource(R.string.field_body_fat),
                    isError = !bodyFatOk,
                    errorText = stringResource(R.string.error_body_fat_invalid),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = source == WeightSource.HOME_SCALE,
                        onClick = { source = WeightSource.HOME_SCALE },
                        label = { Text(stringResource(R.string.source_home_scale)) }
                    )
                    FilterChip(
                        selected = source == WeightSource.INBODY,
                        onClick = { source = WeightSource.INBODY },
                        label = { Text(stringResource(R.string.source_inbody)) }
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.field_note)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = dateOk && kgOk && bodyFatOk,
                onClick = {
                    onSave(
                        WeightEntry(
                            id = entry?.id ?: newId(),
                            date = date,
                            kg = kgValue ?: 0.0,
                            bodyFatPct = if (bodyFat.isBlank()) null else bodyFatValue,
                            source = source,
                            note = note.trim()
                        )
                    )
                }
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
