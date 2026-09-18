package com.routina.hub.catalog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.routina.core.contract.Capability
import com.routina.core.contract.FamilyApp
import com.routina.core.contract.FamilyScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 目錄裡的一列：把「本機裝了什麼」與「遠端有什麼」合成一個畫面用的模型 */
data class CatalogEntry(
    val id: String,
    val name: String,
    val summary: String,
    val packageName: String,
    /** 未安裝的成員拿不到系統圖示，只能用名冊上的這個位址 */
    val iconUrl: String?,
    val installedVersion: String?,
    val remote: RemoteVersion?,
    /** 查不到遠端版本時的原因，誠實顯示而不是假裝沒有更新 */
    val remoteError: String?,
    val capabilities: List<Capability>,
    val status: Status
) {
    enum class Status {
        /** 名冊上有、本機沒裝 */
        NOT_INSTALLED,

        /** 裝了，且已是最新 */
        UP_TO_DATE,

        /** 裝了，遠端有更新的版本 */
        UPDATE_AVAILABLE,

        /** 裝了，但查不到遠端版本（離線、額度用完、還沒發版） */
        INSTALLED_UNKNOWN
    }

    val installed: Boolean get() = installedVersion != null
}

/** 某一列正在進行的工作。下載中途轉螢幕不該重來，所以狀態放在 ViewModel */
sealed interface AppJob {
    data class Downloading(val fraction: Float?, val doneBytes: Long, val totalBytes: Long) : AppJob
    data object Verifying : AppJob

    /** 已經把 APK 交給系統安裝器，等使用者在系統畫面上按確認 */
    data object HandedOff : AppJob

    /** 缺「允許安裝應用程式」的授權，要先去系統設定開 */
    data object NeedsInstallPermission : AppJob
    data class Failed(val reason: String) : AppJob
}

data class CatalogUiState(
    val entries: List<CatalogEntry> = emptyList(),
    val jobs: Map<String, AppJob> = emptyMap(),
    /** 成員 id → 快取裡已經下載了多少位元組。行程被回收後靠它讓按鈕說得出「繼續下載」 */
    val resumable: Map<String, Long> = emptyMap(),
    val loading: Boolean = false,
    /** 名冊本身讀不到時的提示。此時仍會列出已安裝的成員 */
    val registryError: String? = null
)

/**
 * 目錄的狀態來源。
 *
 * 合併兩份資料：[FamilyScanner] 掃到的已安裝成員，以及名冊上列出的可安裝成員。
 * 兩邊都要保留——名冊讀不到時至少還能列出裝好的（就是 v0.2 的行為），
 * 而手動側載、沒登記在名冊上的成員也不該從畫面上消失。
 */
class CatalogViewModel(app: Application) : AndroidViewModel(app) {

    private val client = RegistryClient(app)
    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    /**
     * 重新整理。
     *
     * @param force true＝忽略快取直接問 GitHub（使用者按重新整理時）。
     *   平常用 false，避免把每小時 60 次的匿名 API 額度花在每次回到前景上。
     */
    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }

            val app = getApplication<Application>()
            val installed = FamilyScanner.scan(app).associateBy { it.packageName }

            val registryResult = client.registry(force)
            val registry = registryResult.getOrNull()
            val registryError = registryResult.exceptionOrNull()?.message

            val entries = mutableListOf<CatalogEntry>()
            val coveredPackages = mutableSetOf<String>()

            registry?.apps?.forEach { listed ->
                coveredPackages += listed.packageName
                // Hub 自己也列在名冊上（這樣才能自我更新）。FamilyScanner 刻意排除呼叫方，
                // 所以自己的版本要另外問，否則會被誤判成「未安裝」而跳出安裝鈕。
                val local = if (listed.packageName == app.packageName) {
                    selfAsFamilyApp(listed)
                } else {
                    installed[listed.packageName]
                }
                val remoteResult = client.latest(listed, force)
                entries += merge(listed, local, remoteResult)
            }

            // 名冊沒列到、但裝在機器上的成員（手動側載或名冊還沒更新）
            installed.values
                .filter { it.packageName !in coveredPackages }
                .forEach { entries += fromInstalledOnly(it) }

            val sorted = entries.sortedWith(compareBy({ it.status.ordinal }, { it.name }))

            // 交給系統安裝器之後，使用者裝完回來就會走到這裡。已經裝到最新的項目
            // 不該還掛著「已交給系統安裝畫面」的提示，自己收掉。
            val settled = sorted
                .filter { it.status == CatalogEntry.Status.UP_TO_DATE }
                .map { it.id }
                .toSet()

            val resumable = resumableBytes(sorted)

            _state.update { current ->
                current.copy(
                    entries = sorted,
                    resumable = resumable,
                    jobs = current.jobs.filterNot { (id, job) ->
                        job is AppJob.HandedOff && id in settled
                    },
                    loading = false,
                    registryError = if (registry == null) registryError else null
                )
            }
        }
    }

    private fun merge(
        listed: RegistryApp,
        local: FamilyApp?,
        remoteResult: Result<RemoteVersion>
    ): CatalogEntry {
        val remote = remoteResult.getOrNull()
        val installedVersion = local?.versionName?.takeIf { it.isNotBlank() }

        val status = when {
            installedVersion == null -> CatalogEntry.Status.NOT_INSTALLED
            remote == null -> CatalogEntry.Status.INSTALLED_UNKNOWN
            else -> {
                val diff = Version.compare(remote.version, installedVersion)
                when {
                    diff == null -> CatalogEntry.Status.INSTALLED_UNKNOWN
                    diff > 0 -> CatalogEntry.Status.UPDATE_AVAILABLE
                    else -> CatalogEntry.Status.UP_TO_DATE
                }
            }
        }

        return CatalogEntry(
            id = listed.id,
            // 已安裝時以成員自己宣告的名稱為準（它比名冊更貼近實際裝的那一版）
            name = local?.name?.takeIf { it.isNotBlank() } ?: listed.name,
            summary = local?.summary?.takeIf { it.isNotBlank() } ?: listed.summary,
            packageName = listed.packageName,
            iconUrl = listed.icon.takeIf { it.isNotBlank() }?.let { HubConfig.REGISTRY_BASE + it },
            installedVersion = installedVersion,
            remote = remote,
            remoteError = remoteResult.exceptionOrNull()?.message,
            capabilities = local?.capabilities.orEmpty(),
            status = status
        )
    }

    /** Hub 自己：版本從自己的 packageInfo 讀，其餘欄位沿用名冊上的 */
    private fun selfAsFamilyApp(listed: RegistryApp): FamilyApp? {
        val app = getApplication<Application>()
        val version = runCatching {
            @Suppress("DEPRECATION")
            app.packageManager.getPackageInfo(app.packageName, 0).versionName
        }.getOrNull().orEmpty()
        if (version.isBlank()) return null
        return FamilyApp(
            packageName = listed.packageName,
            familyId = listed.id,
            name = listed.name,
            summary = listed.summary,
            versionName = version,
            capabilities = emptyList()
        )
    }

    private fun fromInstalledOnly(local: FamilyApp) = CatalogEntry(
        id = local.familyId,
        name = local.name,
        summary = local.summary,
        packageName = local.packageName,
        iconUrl = null,
        installedVersion = local.versionName.takeIf { it.isNotBlank() },
        remote = null,
        remoteError = null,
        capabilities = local.capabilities,
        status = CatalogEntry.Status.INSTALLED_UNKNOWN
    )

    /** 下載 → 驗簽章 → 交給系統安裝器。每一步失敗都要說得出原因 */
    fun install(entry: CatalogEntry) {
        val remote = entry.remote ?: return
        val app = getApplication<Application>()

        // 同一個成員只跑一份下載。續傳是用 append 寫的，兩份同時寫同一個 .part 會把檔案寫壞
        val running = _state.value.jobs[entry.id]
        if (running is AppJob.Downloading || running == AppJob.Verifying) return

        viewModelScope.launch {
            // 從既有的殘檔接起，進度數字才不會先閃一下 0
            val already = _state.value.resumable[entry.id] ?: 0L
            setJob(entry.id, AppJob.Downloading(null, already, remote.sizeBytes))

            val downloaded = ApkDownloader.download(
                context = app,
                url = remote.downloadUrl,
                fileName = apkFileName(entry.id, remote),
                expectedSize = remote.sizeBytes
            ) { done, total ->
                val known = if (total > 0) total else remote.sizeBytes
                val fraction = if (known > 0) (done.toFloat() / known).coerceIn(0f, 1f) else null
                setJob(entry.id, AppJob.Downloading(fraction, done, known))
            }.getOrElse { error ->
                setJob(entry.id, AppJob.Failed(error.message ?: "下載失敗"))
                refreshResumable()
                return@launch
            }

            setJob(entry.id, AppJob.Verifying)
            when (val verdict = ApkVerifier.verify(app, downloaded, entry.packageName)) {
                is ApkVerifier.Result.Rejected -> {
                    downloaded.delete()
                    setJob(entry.id, AppJob.Failed(verdict.reason))
                    return@launch
                }

                ApkVerifier.Result.Ok -> Unit
            }

            // 沒有安裝授權就先停在這裡並說清楚，而不是叫出一個必定失敗的對話框
            if (!ApkInstaller.canInstall(app)) {
                setJob(entry.id, AppJob.NeedsInstallPermission)
                return@launch
            }

            if (!ApkInstaller.install(app, downloaded)) {
                setJob(entry.id, AppJob.Failed("叫不出系統的安裝畫面"))
                return@launch
            }
            setJob(entry.id, AppJob.HandedOff)
        }
    }

    /** 使用者去開完授權回來後，直接續跑安裝 */
    fun retryInstall(entry: CatalogEntry) {
        clearJob(entry.id)
        install(entry)
    }

    fun clearJob(id: String) {
        _state.update { it.copy(jobs = it.jobs - id) }
    }

    /** 快取裡的檔名。帶版本才不會讓不同版本的殘檔混在一起 */
    private fun apkFileName(id: String, remote: RemoteVersion) = "$id-${remote.version}.apk"

    private suspend fun resumableBytes(entries: List<CatalogEntry>): Map<String, Long> =
        withContext(Dispatchers.IO) {
            val app = getApplication<Application>()
            entries.mapNotNull { entry ->
                val remote = entry.remote ?: return@mapNotNull null
                val bytes = ApkDownloader.resumableBytes(app, apkFileName(entry.id, remote))
                if (bytes > 0) entry.id to bytes else null
            }.toMap()
        }

    private suspend fun refreshResumable() {
        val bytes = resumableBytes(_state.value.entries)
        _state.update { it.copy(resumable = bytes) }
    }

    private fun setJob(id: String, job: AppJob) {
        _state.update { it.copy(jobs = it.jobs + (id to job)) }
    }
}
