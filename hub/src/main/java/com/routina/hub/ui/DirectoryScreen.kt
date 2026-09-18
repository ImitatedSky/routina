package com.routina.hub.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.routina.core.contract.FamilyScanner
import com.routina.hub.R
import com.routina.hub.catalog.AppJob
import com.routina.hub.catalog.ApkInstaller
import com.routina.hub.catalog.CatalogEntry
import com.routina.hub.catalog.CatalogViewModel
import com.routina.hub.catalog.RemoteIcons
import kotlinx.coroutines.launch

/**
 * 家族目錄：列出裝置上已安裝的成員，以及名冊上可以安裝的成員。
 *
 * 每次回到前景時重新整理（但用快取、不打 GitHub），因為使用者很可能是切出去
 * 裝完一個成員才回來。按重新整理鈕才會真的去問 GitHub 最新版。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryScreen(viewModel: CatalogViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh(force = false) }

    val launchFailed = stringResource(R.string.launch_failed)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.directory_title))
                        Text(
                            text = stringResource(R.string.directory_subtitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (state.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp).padding(end = 4.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { viewModel.refresh(force = true) }) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.action_refresh)
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (state.entries.isEmpty() && !state.loading) {
            EmptyState(state.registryError, Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 名冊讀不到時仍列出已安裝的成員，但要說清楚為什麼清單可能不完整
                state.registryError?.let { message ->
                    item(key = "registry-error") { OfflineNotice(message) }
                }

                // 分成兩區而不是混在一份清單裡：「還有什麼可以裝」是使用者最常來這裡問的問題，
                // 要一眼看得出來，不該靠卡片上有沒有按鈕去分辨。
                val available = state.entries
                    .filter { it.status == CatalogEntry.Status.NOT_INSTALLED }
                val installed = state.entries.filterNot {
                    it.status == CatalogEntry.Status.NOT_INSTALLED
                }

                item(key = "header-available") {
                    SectionHeader(stringResource(R.string.section_available), available.size)
                }
                if (available.isEmpty()) {
                    item(key = "available-empty") {
                        Text(
                            text = stringResource(R.string.section_all_installed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                fun cards(entries: List<CatalogEntry>) {
                    items(entries, key = { it.id }) { entry ->
                        EntryCard(
                            entry = entry,
                            job = state.jobs[entry.id],
                            onOpen = {
                                if (!FamilyScanner.launch(context, entry.packageName)) {
                                    scope.launch { snackbarHostState.showSnackbar(launchFailed) }
                                }
                            },
                            onInstall = { viewModel.install(entry) },
                            onRetry = { viewModel.retryInstall(entry) },
                            onDismissJob = { viewModel.clearJob(entry.id) },
                            onAppInfo = { FamilyScanner.openAppInfo(context, entry.packageName) },
                            onGrantInstall = { ApkInstaller.openInstallPermission(context) }
                        )
                    }
                }

                cards(available)

                if (installed.isNotEmpty()) {
                    item(key = "header-installed") {
                        SectionHeader(stringResource(R.string.section_installed), installed.size)
                    }
                    cards(installed)
                }
            }
        }
    }
}

/** 分區標題。帶數量，因為「還有幾個可以裝」本身就是使用者要的資訊 */
@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OfflineNotice(message: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(10.dp))
            Column {
                Text(
                    text = stringResource(R.string.registry_offline),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EntryCard(
    entry: CatalogEntry,
    job: AppJob?,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onDismissJob: () -> Unit,
    onAppInfo: () -> Unit,
    onGrantInstall: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            // 只有裝好的才能點整張卡開啟；沒裝的點了沒有意義
            .then(if (entry.installed) Modifier.clickable(onClick = onOpen) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EntryIcon(entry)
                Spacer(Modifier.size(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = entry.name, style = MaterialTheme.typography.titleMedium)
                    if (entry.summary.isNotBlank()) {
                        Text(
                            text = entry.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    VersionLine(entry)
                    if (entry.capabilities.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.capabilities_label,
                                entry.capabilities.joinToString("、") { it.label }
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (entry.installed) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_open)) },
                                onClick = { menuOpen = false; onOpen() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_app_info)) },
                                onClick = { menuOpen = false; onAppInfo() }
                            )
                        }
                    }
                }
            }

            if (job != null) {
                Spacer(Modifier.height(12.dp))
                JobRow(job, onRetry, onDismissJob, onGrantInstall)
            } else {
                PrimaryAction(entry, onOpen, onInstall)
            }
        }
    }
}

/** 版本那一行：四種狀態各說各的話，不確定時就說不確定 */
@Composable
private fun VersionLine(entry: CatalogEntry) {
    val text = when (entry.status) {
        CatalogEntry.Status.NOT_INSTALLED ->
            entry.remote?.let { "v${it.version} · ${sizeText(it.sizeBytes)}" }
                ?: entry.remoteError
                ?: stringResource(R.string.version_unknown)

        CatalogEntry.Status.UP_TO_DATE ->
            "v${entry.installedVersion} · " + stringResource(R.string.version_latest)

        CatalogEntry.Status.UPDATE_AVAILABLE ->
            "v${entry.installedVersion} → v${entry.remote?.version}"

        CatalogEntry.Status.INSTALLED_UNKNOWN ->
            "v${entry.installedVersion}" + (entry.remoteError?.let { " · $it" } ?: "")
    }
    val tint = if (entry.status == CatalogEntry.Status.UPDATE_AVAILABLE) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = tint)
}

@Composable
private fun PrimaryAction(entry: CatalogEntry, onOpen: () -> Unit, onInstall: () -> Unit) {
    val installable = entry.remote != null
    when (entry.status) {
        CatalogEntry.Status.NOT_INSTALLED -> {
            Spacer(Modifier.height(10.dp))
            Button(onClick = onInstall, enabled = installable) {
                Text(stringResource(R.string.action_install))
            }
        }

        CatalogEntry.Status.UPDATE_AVAILABLE -> {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onInstall, enabled = installable) {
                    Text(stringResource(R.string.action_update))
                }
                OutlinedButton(onClick = onOpen) { Text(stringResource(R.string.action_open)) }
            }
        }

        // 已是最新／查不到遠端：整張卡可點開啟，不必再放一顆按鈕
        else -> Unit
    }
}

@Composable
private fun JobRow(
    job: AppJob,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onGrantInstall: () -> Unit
) {
    when (job) {
        is AppJob.Downloading -> Column {
            if (job.fraction != null) {
                LinearProgressIndicator(
                    progress = { job.fraction },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.job_downloading,
                    sizeText(job.doneBytes),
                    sizeText(job.totalBytes)
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AppJob.Verifying -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(10.dp))
            Text(
                text = stringResource(R.string.job_verifying),
                style = MaterialTheme.typography.labelMedium
            )
        }

        AppJob.HandedOff -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.job_handed_off),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        }

        AppJob.NeedsInstallPermission -> Column {
            Text(
                text = stringResource(R.string.job_needs_permission),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onGrantInstall) {
                    Text(stringResource(R.string.action_open_settings))
                }
                OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
        }

        is AppJob.Failed -> Column {
            Text(
                text = job.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
            }
        }
    }
}

/** 已安裝的用系統圖示（跟著主題與密度走）；沒裝的只能用名冊上的 PNG */
@Composable
private fun EntryIcon(entry: CatalogEntry) {
    val local = if (entry.installed) rememberFamilyIcon(entry.packageName) else null
    var remote by remember(entry.iconUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(entry.iconUrl, local) {
        val url = entry.iconUrl
        if (local == null && url != null) remote = RemoteIcons.load(url)
    }

    val bitmap = local ?: remote
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(44.dp)
        )
    } else {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Apps,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyState(registryError: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (registryError != null) Icons.Filled.CloudOff else Icons.Filled.Apps,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(
                    if (registryError != null) R.string.empty_offline_title else R.string.empty_title
                ),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = registryError ?: stringResource(R.string.empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 位元組轉成人看得懂的大小。總數未知時顯示 -- 而不是一個假的數字 */
private fun sizeText(bytes: Long): String = when {
    bytes <= 0 -> "--"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
