package com.routina.bite.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.routina.bite.R
import com.routina.bite.data.BackupCodec
import com.routina.bite.model.Targets
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: BiteViewModel,
    onBack: () -> Unit
) {
    val targets by viewModel.targets.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var kcal by remember { mutableStateOf(targets.kcal.toString()) }
    var protein by remember { mutableStateOf(targets.protein.toString()) }
    var fat by remember { mutableStateOf(targets.fat?.toString().orEmpty()) }
    var carbs by remember { mutableStateOf(targets.carbs?.toString().orEmpty()) }

    // 匯入會改掉目標，欄位要跟著換成新值
    LaunchedEffect(targets) {
        kcal = targets.kcal.toString()
        protein = targets.protein.toString()
        fat = targets.fat?.toString().orEmpty()
        carbs = targets.carbs?.toString().orEmpty()
    }

    val kcalValue = kcal.toIntOrNull()
    val proteinValue = protein.toIntOrNull()
    val kcalOk = kcalValue != null && kcalValue > 0
    val proteinOk = proteinValue != null && proteinValue > 0

    val targetsSaved = stringResource(R.string.targets_saved)
    val exportDone = stringResource(R.string.export_done)
    val exportFailed = stringResource(R.string.export_failed)
    val importDone = stringResource(R.string.import_done)
    val importUnreadable = stringResource(R.string.import_failed_unreadable)
    val importNotBite = stringResource(R.string.import_failed_not_bite)
    val importNewer = stringResource(R.string.import_failed_newer)

    fun toast(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.exportBackup(uri) { ok -> toast(if (ok) exportDone else exportFailed) }
    }

    // 有些檔案管理器把 .json 標成別的 MIME，所以也放行 */*
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.importBackup(uri) { outcome ->
            when (outcome) {
                is ImportOutcome.Ok -> toast(
                    String.format(importDone, outcome.foods, outcome.entries, outcome.weights, outcome.notes)
                )
                is ImportOutcome.Failed -> toast(
                    when (outcome.reason) {
                        BackupCodec.Reject.UNREADABLE -> importUnreadable
                        BackupCodec.Reject.NOT_BITE -> importNotBite
                        BackupCodec.Reject.NEWER_SCHEMA -> importNewer
                    }
                )
            }
        }
    }

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.settings_targets), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.settings_targets_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            NumberField(
                value = kcal,
                onValueChange = { kcal = it },
                label = stringResource(R.string.field_target_kcal),
                isError = !kcalOk,
                errorText = stringResource(R.string.error_target_required),
                modifier = Modifier.fillMaxWidth()
            )
            NumberField(
                value = protein,
                onValueChange = { protein = it },
                label = stringResource(R.string.field_target_protein),
                isError = !proteinOk,
                errorText = stringResource(R.string.error_target_required),
                modifier = Modifier.fillMaxWidth()
            )
            NumberField(
                value = fat,
                onValueChange = { fat = it },
                label = stringResource(R.string.field_target_fat),
                modifier = Modifier.fillMaxWidth()
            )
            NumberField(
                value = carbs,
                onValueChange = { carbs = it },
                label = stringResource(R.string.field_target_carbs),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                enabled = kcalOk && proteinOk,
                onClick = {
                    viewModel.setTargets(
                        Targets(
                            kcal = kcalValue ?: targets.kcal,
                            protein = proteinValue ?: targets.protein,
                            fat = fat.toIntOrNull()?.takeIf { it > 0 },
                            carbs = carbs.toIntOrNull()?.takeIf { it > 0 }
                        )
                    )
                    toast(targetsSaved)
                }
            ) { Text(stringResource(R.string.action_save)) }

            HorizontalDivider()

            Text(stringResource(R.string.settings_backup), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.settings_backup_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch(BackupCodec.defaultFileName()) }) {
                    Text(stringResource(R.string.action_export))
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }
                ) {
                    Text(stringResource(R.string.action_import))
                }
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.settings_version, versionName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
