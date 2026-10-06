package com.routina.hub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.routina.hub.R
import com.routina.hub.catalog.ApkInstaller
import com.routina.hub.catalog.CatalogViewModel
import com.routina.hub.catalog.HubConfig
import kotlinx.coroutines.launch

/** Hub 的設定頁。只放現在就用得到的項目 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: CatalogViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var canInstall by remember { mutableStateOf(ApkInstaller.canInstall(context)) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    // 開授權多半是跳去系統設定再切回來，回到前景時要重查一次
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canInstall = ApkInstaller.canInstall(context)
        viewModel.loadCacheSize()
    }
    // 下載跑完時快取大小變了，重算一次
    LaunchedEffect(state.busy) { viewModel.loadCacheSize() }

    val versionName = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    val resetDone = stringResource(R.string.settings_reset_order_done)

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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SettingsSection(stringResource(R.string.settings_section_permission)) {
                SettingsRow(
                    title = stringResource(R.string.settings_install_permission),
                    summary = stringResource(
                        if (canInstall) R.string.settings_install_granted else R.string.settings_install_denied
                    ),
                    summaryColor = if (canInstall) null else MaterialTheme.colorScheme.error,
                    onClick = { ApkInstaller.openInstallPermission(context) }
                )
            }

            SettingsSection(stringResource(R.string.settings_section_cache)) {
                val canClear = state.cacheBytes > 0 && !state.busy
                SettingsRow(
                    title = stringResource(R.string.settings_cache_title),
                    summary = when {
                        state.busy -> stringResource(R.string.settings_cache_busy)
                        state.cacheBytes > 0 ->
                            stringResource(R.string.settings_cache_size, sizeText(state.cacheBytes))
                        else -> stringResource(R.string.settings_cache_empty)
                    },
                    trailing = {
                        TextButton(onClick = { confirmClear = true }, enabled = canClear) {
                            Text(stringResource(R.string.action_clear))
                        }
                    }
                )
            }

            SettingsSection(stringResource(R.string.settings_section_order)) {
                SettingsRow(
                    title = stringResource(R.string.settings_reset_order),
                    summary = stringResource(R.string.settings_reset_order_hint),
                    onClick = { confirmReset = true }
                )
            }

            SettingsSection(stringResource(R.string.settings_section_about)) {
                SettingsRow(
                    title = stringResource(R.string.settings_version),
                    summary = "v$versionName"
                )
                HorizontalDivider()
                SettingsRow(
                    title = stringResource(R.string.action_github),
                    summary = "github.com/${HubConfig.HUB_REPO}",
                    onClick = { openReleasesPage(context, HubConfig.HUB_REPO) }
                )
                HorizontalDivider()
                SettingsRow(
                    title = stringResource(R.string.settings_registry),
                    summary = HubConfig.REGISTRY_URL
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.settings_cache_confirm_title)) },
            text = {
                Text(stringResource(R.string.settings_cache_confirm_body, sizeText(state.cacheBytes)))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearDownloadCache()
                }) { Text(stringResource(R.string.action_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.settings_reset_order_confirm_title)) },
            text = { Text(stringResource(R.string.settings_reset_order_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    viewModel.resetOrder()
                    // 設定頁上看不到排序的變化，要另外講一聲
                    scope.launch { snackbarHostState.showSnackbar(resetDone) }
                }) { Text(stringResource(R.string.action_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/** 一個分區：標題的樣子跟目錄的分區標題一致，底下的列包在同一張卡片裡 */
@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column { content() }
        }
    }
}

/** 設定的一列：標題加一行說明，可點的才給 onClick，右邊可以放一顆按鈕 */
@Composable
private fun SettingsRow(
    title: String,
    summary: String,
    summaryColor: Color? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = summaryColor ?: MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing?.invoke()
    }
}
