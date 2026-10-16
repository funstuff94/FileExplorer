package com.droid.fileexplorer.viewmodel

import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droid.fileexplorer.data.FileRepository
import com.droid.fileexplorer.data.SharedClipboardManager
import com.droid.fileexplorer.model.Breadcrumb
import com.droid.fileexplorer.model.ClipboardOperation
import com.droid.fileexplorer.model.ClipboardState
import com.droid.fileexplorer.model.DriveItem
import com.droid.fileexplorer.model.DriveType
import com.droid.fileexplorer.model.ExplorerTab
import com.droid.fileexplorer.model.FileItem
import com.droid.fileexplorer.model.QuickAccessItem
import com.droid.fileexplorer.model.SortDirection
import com.droid.fileexplorer.model.SortField
import com.droid.fileexplorer.model.SortOption
import com.droid.fileexplorer.model.TransferProgress
import com.droid.fileexplorer.model.ViewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class ExplorerUiState(
    val currentPath: String = "",
    val currentTreeUri: String? = null,
    val currentFolderName: String = "This PC",
    val breadcrumbs: List<Breadcrumb> = emptyList(),
    val items: List<FileItem> = emptyList(),
    val filteredItems: List<FileItem> = emptyList(),
    val quickAccessItems: List<QuickAccessItem> = emptyList(),
    val drives: List<DriveItem> = emptyList(),
    val cloudDrives: List<DriveItem> = emptyList(),
    val selectedItemPaths: Set<String> = emptySet(),
    val viewMode: ViewMode = ViewMode.GRID,
    val sortOption: SortOption = SortOption(SortField.NAME, SortDirection.ASCENDING),
    val searchQuery: String = "",
    val hasStoragePermission: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val statusText: String = "",
    val tabs: List<ExplorerTab> = emptyList(),
    val activeTabId: String = "",
    val clipboardState: ClipboardState? = null,
    val transferProgress: TransferProgress? = null,
    val needsVolumeAccess: Boolean = false,
    val volumeAccessName: String = "",
    val volumeAccessPath: String = "",
    val isReadOnlyVolume: Boolean = false,
    val readOnlyVolumeName: String = "",
    val readOnlyVolumePath: String = "",
    val showVideoThumbnails: Boolean = true,
    val isDarkMode: Boolean? = null
) {
    val selectedDirectories: List<FileItem>
        get() = filteredItems.filter { selectedItemPaths.contains(it.path) && it.isDirectory }

    val hasSelectedDirectories: Boolean
        get() = selectedDirectories.isNotEmpty()

    val areSelectedDirectoriesPinned: Boolean
        get() = selectedDirectories.isNotEmpty() && selectedDirectories.all { it.isPinned }

    val isCurrentDirectoryPinned: Boolean
        get() = quickAccessItems.any {
            it.path.trimEnd('/') == currentPath.trimEnd('/') ||
                (currentTreeUri != null && it.treeUri == currentTreeUri)
        }

    val canPaste: Boolean
        get() = clipboardState != null && clipboardState.items.isNotEmpty()
}

class FileExplorerViewModel(
    private val repository: FileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExplorerUiState())
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    // Per-tab navigation history
    private val tabHistories = mutableMapOf<String, Pair<MutableList<NavigationEntry>, MutableList<NavigationEntry>>>()

    data class NavigationEntry(val path: String, val treeUri: String? = null)
    private var pendingNavigation: NavigationEntry? = null

    init {
        val initialThumbnails = repository.isVideoThumbnailsEnabled()
        val initialViewMode = repository.getViewMode()
        val initialDarkMode = repository.getDarkModePreference()
        _uiState.update {
            it.copy(
                showVideoThumbnails = initialThumbnails,
                viewMode = initialViewMode,
                isDarkMode = initialDarkMode
            )
        }
        loadInitialData()
        observeSharedState()
    }

    private fun observeSharedState() {
        viewModelScope.launch {
            SharedClipboardManager.clipboardState.collect { sharedClip ->
                _uiState.update { it.copy(clipboardState = sharedClip) }
            }
        }

        viewModelScope.launch {
            SharedClipboardManager.directoryChangeEvents.collect { changedPath ->
                val currentPath = _uiState.value.currentPath.trimEnd('/')
                val currentTree = _uiState.value.currentTreeUri
                val cleanChanged = changedPath.trimEnd('/')

                val shouldRefresh = cleanChanged.isEmpty() ||
                    cleanChanged == "*" ||
                    cleanChanged == currentPath ||
                    currentPath.startsWith("$cleanChanged/") ||
                    (currentTree != null && (cleanChanged == currentTree || currentTree.contains(cleanChanged) || cleanChanged.contains(currentTree)))

                if (shouldRefresh) {
                    refresh()
                }
            }
        }
    }

    fun handleIntentPath(path: String?, treeUri: String?) {
        if (path.isNullOrEmpty() && treeUri.isNullOrEmpty()) return
        if (_uiState.value.tabs.isNotEmpty()) {
            navigateTo(path ?: "", treeUri, addToHistory = false)
        } else {
            pendingNavigation = NavigationEntry(path ?: "", treeUri)
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val drives = repository.getStorageDrives()
            val quickAccess = repository.getQuickAccessItems()
            val cloudDrives = repository.getCloudDrives()

            val pending = pendingNavigation
            val defaultPath = drives.firstOrNull()?.path ?: "/storage/emulated/10"
            val initialPath = pending?.path?.ifEmpty { null } ?: defaultPath
            val initialTreeUri = pending?.treeUri
            pendingNavigation = null

            val initialTabId = UUID.randomUUID().toString()
            val initialTab = ExplorerTab(
                id = initialTabId,
                title = if (initialTreeUri != null) "Cloud Drive" else "This PC",
                path = initialPath,
                treeUri = initialTreeUri
            )
            tabHistories[initialTabId] = Pair(mutableListOf(), mutableListOf())

            _uiState.update {
                it.copy(
                    drives = drives,
                    quickAccessItems = quickAccess,
                    cloudDrives = cloudDrives,
                    tabs = listOf(initialTab),
                    activeTabId = initialTabId
                )
            }

            navigateTo(initialPath, initialTreeUri, addToHistory = false)
        }
    }

    fun openNewTab(path: String? = null, treeUri: String? = null) {
        val targetPath = path ?: _uiState.value.drives.firstOrNull()?.path ?: "/storage/emulated/10"
        val tabId = UUID.randomUUID().toString()
        val title = when {
            treeUri != null -> "Cloud Drive"
            targetPath == "/storage/emulated/10" || targetPath == "/storage/emulated/0" -> "Internal Storage"
            else -> File(targetPath).name.ifEmpty { "This PC" }
        }
        val newTab = ExplorerTab(id = tabId, title = title, path = targetPath, treeUri = treeUri)
        tabHistories[tabId] = Pair(mutableListOf(), mutableListOf())

        _uiState.update {
            it.copy(
                tabs = it.tabs + newTab,
                activeTabId = tabId
            )
        }
        navigateTo(targetPath, treeUri, addToHistory = false)
    }

    fun closeTab(tabId: String) {
        val currentTabs = _uiState.value.tabs
        if (currentTabs.size <= 1) return // Keep at least one tab

        val index = currentTabs.indexOfFirst { it.id == tabId }
        val remaining = currentTabs.filter { it.id != tabId }
        tabHistories.remove(tabId)

        val newActiveTab = if (_uiState.value.activeTabId == tabId) {
            val nextIndex = (index - 1).coerceAtLeast(0)
            remaining[nextIndex]
        } else {
            remaining.firstOrNull { it.id == _uiState.value.activeTabId } ?: remaining.first()
        }

        _uiState.update {
            it.copy(
                tabs = remaining,
                activeTabId = newActiveTab.id
            )
        }
        navigateTo(newActiveTab.path, newActiveTab.treeUri, addToHistory = false)
    }

    fun switchTab(tabId: String) {
        val tab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        _uiState.update { it.copy(activeTabId = tabId) }
        navigateTo(tab.path, tab.treeUri, addToHistory = false)
    }

    fun navigateToPathInput(input: String) {
        val raw = input.trim()
        if (raw.isEmpty()) return

        // 1. Normalize backslashes to forward slashes and collapse duplicate slashes
        var normalized = raw.replace('\\', '/')
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/")
        }

        val internalDrive = _uiState.value.drives.firstOrNull { it.type == DriveType.INTERNAL }
        val internalRoot = internalDrive?.path ?: "/storage/emulated/10"

        val externalDrive = _uiState.value.drives.firstOrNull {
            it.type != DriveType.INTERNAL && it.type != DriveType.LINUX_SHARED
        }

        var resolvedPath = normalized
        var resolvedTreeUri: String? = null

        when {
            // "This PC" or "/" -> navigate to root
            normalized.equals("This PC", ignoreCase = true) || normalized == "/" -> {
                resolvedPath = internalRoot
            }
            // "C:" or "C:/" or "C:\..." -> maps to internal storage
            normalized.startsWith("C:", ignoreCase = true) -> {
                val sub = normalized.substring(2).trimStart('/')
                resolvedPath = if (sub.isEmpty()) internalRoot else "$internalRoot/$sub"
            }
            // "D:" or "D:/" or "D:\..." -> maps to external drive (if present)
            normalized.startsWith("D:", ignoreCase = true) -> {
                val sub = normalized.substring(2).trimStart('/')
                if (externalDrive != null) {
                    resolvedPath = if (sub.isEmpty()) externalDrive.path else "${externalDrive.path.trimEnd('/')}/$sub"
                    resolvedTreeUri = externalDrive.treeUri
                } else {
                    resolvedPath = normalized
                }
            }
            // Linux Apps or Linux Projects -> /storage/emulated/10/Apps
            normalized.equals("Linux Apps", ignoreCase = true) || normalized.equals("Linux Projects", ignoreCase = true) || normalized.equals("Apps", ignoreCase = true) -> {
                resolvedPath = "$internalRoot/Apps"
            }
            // "~" -> internal root
            normalized.startsWith("~") -> {
                resolvedPath = normalized.replaceFirst("~", internalRoot)
            }
            // "/sdcard" -> internal root
            normalized.startsWith("/sdcard", ignoreCase = true) -> {
                resolvedPath = normalized.replaceFirst(Regex("^/sdcard", RegexOption.IGNORE_CASE), internalRoot)
            }
            // Relative path or folder name (e.g. "Download", "Documents", "Movies", or subfolder)
            !normalized.startsWith("/") && !normalized.startsWith("content://") -> {
                val current = _uiState.value.currentPath
                val childFolder = _uiState.value.items.firstOrNull { it.isDirectory && it.name.equals(normalized, ignoreCase = true) }
                val qaFolder = _uiState.value.quickAccessItems.firstOrNull { it.title.equals(normalized, ignoreCase = true) }
                val driveMatch = _uiState.value.drives.firstOrNull { it.name.equals(normalized, ignoreCase = true) }

                resolvedPath = when {
                    childFolder != null -> childFolder.path
                    qaFolder != null -> qaFolder.path
                    driveMatch != null -> driveMatch.path
                    current.isNotEmpty() && File(current, normalized).exists() -> File(current, normalized).absolutePath
                    File(internalRoot, normalized).exists() -> File(internalRoot, normalized).absolutePath
                    current.isNotEmpty() -> "$current/$normalized"
                    else -> "$internalRoot/$normalized"
                }
            }
        }

        // If user pointed to a file instead of a folder, navigate to parent folder
        val targetFile = File(resolvedPath)
        val finalPath = if (targetFile.exists() && targetFile.isFile) {
            targetFile.parent ?: resolvedPath
        } else {
            resolvedPath.trimEnd('/')
        }

        // Check if external storage volume tree URI is known
        val effectiveTreeUri = resolvedTreeUri
            ?: if (isExternalStoragePath(finalPath)) externalDrive?.treeUri else null

        viewModelScope.launch {
            if (!repository.exists(finalPath, effectiveTreeUri)) {
                _uiState.update { it.copy(statusText = "Folder does not exist: '$input'") }
                return@launch
            }
            navigateTo(finalPath, effectiveTreeUri)
        }
    }

    fun navigateTo(path: String, treeUri: String? = null, addToHistory: Boolean = true) {
        val targetPath = if (path.isEmpty() && treeUri == null) {
            _uiState.value.drives.firstOrNull()?.path ?: "/storage/emulated/10"
        } else path

        val current = _uiState.value.currentPath
        val currentUri = _uiState.value.currentTreeUri
        val activeId = _uiState.value.activeTabId
        val (backStack, forwardStack) = tabHistories.getOrPut(activeId) { Pair(mutableListOf(), mutableListOf()) }

        if (addToHistory && (current.isNotEmpty() || currentUri != null)) {
            backStack.add(NavigationEntry(current, currentUri))
            forwardStack.clear()
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, searchQuery = "", selectedItemPaths = emptySet()) }

            // Check if this is an external storage drive that requires SAF volume permission
            val isExternalVolume = isExternalStoragePath(targetPath)
            val targetUuid = targetPath.trimEnd('/').substringAfterLast('/')
            val matchingDrive = _uiState.value.drives.firstOrNull {
                it.type != DriveType.INTERNAL && it.type != DriveType.LINUX_SHARED &&
                (it.path == targetPath || targetPath.startsWith(it.path) ||
                (treeUri != null && it.treeUri == treeUri) ||
                (it.treeUri != null && (targetPath == it.treeUri || targetPath.startsWith(it.treeUri))) ||
                it.path.contains(targetUuid) ||
                it.id.contains(targetUuid) ||
                (it.type == DriveType.SMARTPHONE && (targetPath.startsWith("mtp") || targetPath.contains("com.android.mtp.documents"))))
            }
            val effectiveTreeUri = if (treeUri != null) {
                treeUri
            } else if (matchingDrive?.treeUri != null) {
                matchingDrive.treeUri
            } else if (isExternalVolume) {
                repository.getPersistedTreeUriForPath(targetPath)
            } else {
                null
            }

            val isDirectlyReadable = if (isExternalVolume && effectiveTreeUri == null) {
                try {
                    val file = File(targetPath)
                    file.canRead() && file.listFiles() != null
                } catch (_: Exception) {
                    false
                }
            } else true

            val isDirectlyWritable = if (isExternalVolume && effectiveTreeUri == null) {
                try {
                    val file = File(targetPath)
                    file.canWrite()
                } catch (_: Exception) {
                    false
                }
            } else true

            val driveName = matchingDrive?.name ?: "External Drive ($targetUuid)"
            val driveRootPath = matchingDrive?.path ?: targetPath
            val isReadOnly = isExternalVolume && effectiveTreeUri == null && !isDirectlyWritable

            if (isExternalVolume && effectiveTreeUri == null && !isDirectlyReadable) {
                val breadcrumbs = listOf(Breadcrumb("This PC", ""), Breadcrumb(driveName, targetPath))

                val updatedTabs = _uiState.value.tabs.map { tab ->
                    if (tab.id == activeId) {
                        tab.copy(title = driveName, path = targetPath, treeUri = null)
                    } else tab
                }

                _uiState.update { state ->
                    state.copy(
                        currentPath = targetPath,
                        currentTreeUri = null,
                        currentFolderName = driveName,
                        breadcrumbs = breadcrumbs,
                        items = emptyList(),
                        filteredItems = emptyList(),
                        tabs = updatedTabs,
                        isLoading = false,
                        needsVolumeAccess = true,
                        volumeAccessName = driveName,
                        volumeAccessPath = driveRootPath,
                        isReadOnlyVolume = false,
                        readOnlyVolumeName = "",
                        readOnlyVolumePath = "",
                        statusText = "Permission required to view $driveName"
                    )
                }
                return@launch
            }

            val files = repository.listFiles(targetPath, effectiveTreeUri)
            val breadcrumbs = generateBreadcrumbs(targetPath, effectiveTreeUri)
            val folderName = when {
                effectiveTreeUri != null -> breadcrumbs.lastOrNull()?.title ?: "Storage"
                targetPath == "/storage/emulated/10" || targetPath == "/storage/emulated/0" -> "Internal Storage"
                targetPath.isEmpty() -> "This PC"
                else -> File(targetPath).name.ifEmpty { targetPath }
            }

            _uiState.update { state ->
                val sorted = sortAndFilter(files, state.searchQuery, state.sortOption)
                val updatedTabs = state.tabs.map { tab ->
                    if (tab.id == activeId) {
                        tab.copy(title = folderName, path = targetPath, treeUri = effectiveTreeUri)
                    } else tab
                }
                state.copy(
                    currentPath = targetPath,
                    currentTreeUri = effectiveTreeUri,
                    currentFolderName = folderName,
                    breadcrumbs = breadcrumbs,
                    items = files,
                    filteredItems = sorted,
                    tabs = updatedTabs,
                    isLoading = false,
                    needsVolumeAccess = false,
                    volumeAccessName = "",
                    volumeAccessPath = "",
                    isReadOnlyVolume = isReadOnly,
                    readOnlyVolumeName = if (isReadOnly) driveName else "",
                    readOnlyVolumePath = if (isReadOnly) driveRootPath else "",
                    statusText = if (isReadOnly) "Read-only access. Grant permission to write files." else generateStatusText(sorted, emptySet())
                )
            }
        }
    }

    fun attachVolumeUri(path: String, uriString: String) {
        viewModelScope.launch {
            repository.saveVolumeTreeUri(path, uriString)
            val isMtp = path.startsWith("mtp") || uriString.contains("com.android.mtp.documents")
            val targetUuid = path.trimEnd('/').substringAfterLast('/')
            val targetNavPath = if (isMtp) uriString else path

            _uiState.update { state ->
                val updatedDrives = state.drives.map { drive ->
                    if (drive.type == DriveType.INTERNAL || drive.type == DriveType.LINUX_SHARED) {
                        drive
                    } else {
                        val isMatch = drive.path == path ||
                            (targetUuid.isNotEmpty() && (drive.path.contains(targetUuid) || drive.id.contains(targetUuid))) ||
                            (isMtp && drive.type == DriveType.SMARTPHONE)
                        if (isMatch) {
                            drive.copy(
                                path = if (isMtp) uriString else drive.path,
                                treeUri = uriString
                            )
                        } else drive
                    }
                }
                val updatedTabs = state.tabs.map { tab ->
                    if (tab.id == state.activeTabId) {
                        tab.copy(path = targetNavPath, treeUri = uriString)
                    } else tab
                }
                state.copy(
                    drives = updatedDrives,
                    tabs = updatedTabs,
                    needsVolumeAccess = false,
                    volumeAccessPath = "",
                    volumeAccessName = "",
                    isReadOnlyVolume = false,
                    readOnlyVolumeName = "",
                    readOnlyVolumePath = ""
                )
            }
            navigateTo(targetNavPath, uriString, addToHistory = false)
            refreshDrives()
        }
    }

    fun navigateBack() {
        val activeId = _uiState.value.activeTabId
        val (backStack, forwardStack) = tabHistories.getOrPut(activeId) { Pair(mutableListOf(), mutableListOf()) }
        if (backStack.isNotEmpty()) {
            val last = backStack.removeAt(backStack.size - 1)
            forwardStack.add(NavigationEntry(_uiState.value.currentPath, _uiState.value.currentTreeUri))
            navigateTo(last.path, last.treeUri, addToHistory = false)
        }
    }

    fun navigateForward() {
        val activeId = _uiState.value.activeTabId
        val (backStack, forwardStack) = tabHistories.getOrPut(activeId) { Pair(mutableListOf(), mutableListOf()) }
        if (forwardStack.isNotEmpty()) {
            val next = forwardStack.removeAt(forwardStack.size - 1)
            backStack.add(NavigationEntry(_uiState.value.currentPath, _uiState.value.currentTreeUri))
            navigateTo(next.path, next.treeUri, addToHistory = false)
        }
    }

    fun navigateUp() {
        val crumbs = _uiState.value.breadcrumbs
        if (crumbs.size > 2) {
            val parentCrumb = crumbs[crumbs.size - 2]
            navigateTo(parentCrumb.path, parentCrumb.treeUri)
        } else if (crumbs.size == 2) {
            val defaultPath = _uiState.value.drives.firstOrNull()?.path ?: "/storage/emulated/10"
            if (_uiState.value.currentPath != defaultPath || _uiState.value.currentTreeUri != null) {
                navigateTo(defaultPath, null)
            }
        }
    }

    private var deviceMonitorJob: Job? = null

    fun startDeviceMonitoring() {
        if (deviceMonitorJob?.isActive == true) return
        deviceMonitorJob = viewModelScope.launch {
            while (isActive) {
                delay(1500)
                try {
                    val currentDrives = _uiState.value.drives
                    val newDrives = repository.getStorageDrives()
                    if (newDrives != currentDrives) {
                        val cloudDrives = repository.getCloudDrives()
                        _uiState.update { it.copy(drives = newDrives, cloudDrives = cloudDrives) }

                        val currentPath = _uiState.value.currentPath
                        val currentTree = _uiState.value.currentTreeUri
                        val isExternal = isExternalStoragePath(currentPath) || currentTree?.contains("mtp") == true
                        if (isExternal) {
                            val driveStillConnected = newDrives.any { drive ->
                                (currentPath == drive.path || currentPath.startsWith(drive.path.trimEnd('/') + "/")) ||
                                (currentTree != null && (drive.treeUri == currentTree || drive.path == currentTree)) ||
                                (currentTree?.contains("mtp") == true && drive.type == DriveType.SMARTPHONE)
                            }
                            if (!driveStillConnected) {
                                val fallback = newDrives.firstOrNull()?.path ?: "/storage/emulated/10"
                                navigateTo(fallback, null)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun stopDeviceMonitoring() {
        deviceMonitorJob?.cancel()
        deviceMonitorJob = null
    }

    fun refreshDrives() {
        viewModelScope.launch {
            try {
                val drives = repository.getStorageDrives()
                val cloudDrives = repository.getCloudDrives()
                _uiState.update { state ->
                    state.copy(
                        drives = drives,
                        cloudDrives = cloudDrives
                    )
                }

                val currentPath = _uiState.value.currentPath
                val currentTree = _uiState.value.currentTreeUri
                val isExternal = isExternalStoragePath(currentPath) || currentTree?.contains("mtp") == true
                if (isExternal) {
                    val driveStillConnected = drives.any { drive ->
                        (currentPath == drive.path || currentPath.startsWith(drive.path.trimEnd('/') + "/")) ||
                        (currentTree != null && (drive.treeUri == currentTree || drive.path == currentTree)) ||
                        (currentTree?.contains("mtp") == true && drive.type == DriveType.SMARTPHONE)
                    }
                    if (!driveStillConnected) {
                        val fallback = drives.firstOrNull()?.path ?: "/storage/emulated/10"
                        navigateTo(fallback, null)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun refresh() {
        refreshDrives()
        val current = _uiState.value
        navigateTo(current.currentPath, current.currentTreeUri, addToHistory = false)
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            val sorted = sortAndFilter(state.items, query, state.sortOption)
            state.copy(
                searchQuery = query,
                filteredItems = sorted,
                statusText = generateStatusText(sorted, state.selectedItemPaths)
            )
        }
    }

    fun setSortOption(option: SortOption) {
        _uiState.update { state ->
            val sorted = sortAndFilter(state.items, state.searchQuery, option)
            state.copy(
                sortOption = option,
                filteredItems = sorted
            )
        }
    }

    fun setViewMode(mode: ViewMode) {
        repository.setViewMode(mode)
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun toggleVideoThumbnails() {
        val next = !_uiState.value.showVideoThumbnails
        repository.setVideoThumbnailsEnabled(next)
        _uiState.update { it.copy(showVideoThumbnails = next) }
    }

    fun setShowVideoThumbnails(enabled: Boolean) {
        repository.setVideoThumbnailsEnabled(enabled)
        _uiState.update { it.copy(showVideoThumbnails = enabled) }
    }

    fun toggleDarkMode(systemInDarkTheme: Boolean) {
        val current = _uiState.value.isDarkMode ?: systemInDarkTheme
        val next = !current
        repository.setDarkModePreference(next)
        _uiState.update { it.copy(isDarkMode = next) }
    }

    fun setDarkMode(isDark: Boolean) {
        repository.setDarkModePreference(isDark)
        _uiState.update { it.copy(isDarkMode = isDark) }
    }

    fun toggleSelect(path: String) {
        _uiState.update { state ->
            val newSelection = state.selectedItemPaths.toMutableSet()
            if (newSelection.contains(path)) {
                newSelection.remove(path)
            } else {
                newSelection.add(path)
            }
            state.copy(
                selectedItemPaths = newSelection,
                statusText = generateStatusText(state.filteredItems, newSelection)
            )
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            val allPaths = state.filteredItems.map { it.path }.toSet()
            state.copy(
                selectedItemPaths = allPaths,
                statusText = generateStatusText(state.filteredItems, allPaths)
            )
        }
    }

    fun clearSelection() {
        _uiState.update { state ->
            state.copy(
                selectedItemPaths = emptySet(),
                statusText = generateStatusText(state.filteredItems, emptySet())
            )
        }
    }

    fun selectPaths(paths: Set<String>) {
        _uiState.update { state ->
            state.copy(
                selectedItemPaths = paths,
                statusText = generateStatusText(state.filteredItems, paths)
            )
        }
    }

    // --- Clipboard Operations (Copy, Cut/Move, Paste) ---
    fun copyItems(items: List<FileItem>) {
        val state = ClipboardState(items = items, operation = ClipboardOperation.COPY)
        SharedClipboardManager.setClipboard(state)
        _uiState.update {
            it.copy(
                clipboardState = state,
                statusText = "Copied ${items.size} item(s) to clipboard"
            )
        }
    }

    fun cutItems(items: List<FileItem>) {
        val state = ClipboardState(items = items, operation = ClipboardOperation.CUT)
        SharedClipboardManager.setClipboard(state)
        _uiState.update {
            it.copy(
                clipboardState = state,
                statusText = "Cut ${items.size} item(s) (Ready to move)"
            )
        }
    }

    fun clearClipboard() {
        SharedClipboardManager.clearClipboard()
        _uiState.update {
            it.copy(
                clipboardState = null,
                statusText = "Clipboard cleared"
            )
        }
    }

    fun copySelected() {
        val selected = _uiState.value.filteredItems.filter { _uiState.value.selectedItemPaths.contains(it.path) }
        if (selected.isNotEmpty()) {
            copyItems(selected)
        }
    }

    fun cutSelected() {
        val selected = _uiState.value.filteredItems.filter { _uiState.value.selectedItemPaths.contains(it.path) }
        if (selected.isNotEmpty()) {
            cutItems(selected)
        }
    }

    private var transferJob: Job? = null
    @Volatile
    private var isTransferCancelled = false

    fun cancelTransfer() {
        isTransferCancelled = true
        transferJob?.cancel()
        _uiState.update { it.copy(transferProgress = null, statusText = "Transfer cancelled") }
    }

    fun paste(targetDirPath: String? = null, targetTreeUri: String? = null) {
        val clipboard = SharedClipboardManager.getClipboard() ?: _uiState.value.clipboardState ?: return
        val dest = targetDirPath ?: _uiState.value.currentPath

        transferJob?.cancel()
        isTransferCancelled = false

        transferJob = viewModelScope.launch {
            val items = clipboard.items
            if (items.isEmpty()) return@launch

            val isExternal = isExternalStoragePath(dest)
            val effectiveDestUri = targetTreeUri ?: _uiState.value.currentTreeUri
                ?: if (isExternal) repository.getPersistedTreeUriForPath(dest) else null

            val isDirectlyWritable = if (isExternal && effectiveDestUri == null) {
                try { File(dest).canWrite() } catch (_: Exception) { false }
            } else true

            if (isExternal && effectiveDestUri == null && !isDirectlyWritable) {
                val targetUuid = dest.trimEnd('/').substringAfterLast('/')
                val matchingDrive = _uiState.value.drives.firstOrNull {
                    it.type != DriveType.INTERNAL && it.type != DriveType.LINUX_SHARED &&
                    (it.path == dest || dest.startsWith(it.path) || it.path.contains(targetUuid) || it.id.contains(targetUuid))
                }
                val driveName = matchingDrive?.name ?: "External Drive ($targetUuid)"
                val driveRoot = matchingDrive?.path ?: dest
                _uiState.update {
                    it.copy(
                        statusText = "Write permission required for $driveName. Please grant volume access.",
                        needsVolumeAccess = true,
                        volumeAccessName = driveName,
                        volumeAccessPath = driveRoot
                    )
                }
                return@launch
            }

            val sourceDirs = items.mapNotNull { item ->
                if (item.treeUri != null) item.treeUri else File(item.path).parent
            }.distinct()

            var totalBytes = 0L
            val itemSizes = mutableListOf<Long>()
            for (item in items) {
                val s = if (item.size > 0L && !item.isDirectory) item.size else repository.calculateItemSize(item.path, item.treeUri)
                itemSizes.add(s)
                totalBytes += s
            }

            val opName = if (clipboard.operation == ClipboardOperation.CUT) "Moving" else "Copying"
            val totalItems = items.size
            var overallBytesCopied = 0L
            val startTime = System.currentTimeMillis()
            var lastUiUpdate = 0L

            _uiState.update { state ->
                state.copy(
                    transferProgress = TransferProgress(
                        operation = opName,
                        currentFileName = items.first().name,
                        currentItemIndex = 1,
                        totalItems = totalItems,
                        bytesTransferred = 0L,
                        totalBytes = totalBytes,
                        sourcePath = items.first().path,
                        destPath = dest
                    )
                )
            }

            var successCount = 0
            for (i in items.indices) {
                if (isTransferCancelled) break
                val item = items[i]
                val baseBytesBeforeThisItem = overallBytesCopied

                val progressCallback: (Long, Long, String) -> Unit = { fileBytesCopied, _, currentFileName ->
                    val now = System.currentTimeMillis()
                    val currentTotalCopied = baseBytesBeforeThisItem + fileBytesCopied
                    val elapsedSec = (now - startTime) / 1000.0
                    val speed = if (elapsedSec > 0.1) currentTotalCopied / elapsedSec else 0.0
                    val remainingBytes = (totalBytes - currentTotalCopied).coerceAtLeast(0L)
                    val remainingSec = if (speed > 1024.0) (remainingBytes / speed).toLong() else 0L

                    if (now - lastUiUpdate > 120 || currentTotalCopied >= totalBytes) {
                        lastUiUpdate = now
                        _uiState.update { state ->
                            state.copy(
                                transferProgress = TransferProgress(
                                    operation = opName,
                                    currentFileName = currentFileName,
                                    currentItemIndex = i + 1,
                                    totalItems = totalItems,
                                    bytesTransferred = currentTotalCopied,
                                    totalBytes = totalBytes,
                                    speedBytesPerSec = speed,
                                    estimatedRemainingSeconds = remainingSec,
                                    sourcePath = item.path,
                                    destPath = dest
                                )
                            )
                        }
                    }
                }

                val ok = if (clipboard.operation == ClipboardOperation.CUT) {
                    repository.moveItem(item.path, dest, item.treeUri, effectiveDestUri, progressCallback, { isTransferCancelled })
                } else {
                    repository.copyItem(item.path, dest, item.treeUri, effectiveDestUri, progressCallback, { isTransferCancelled })
                }

                if (ok) {
                    successCount++
                }
                overallBytesCopied = baseBytesBeforeThisItem + itemSizes[i]
            }

            val finalOverall = overallBytesCopied
            val isCut = clipboard.operation == ClipboardOperation.CUT
            val allSucceeded = successCount == totalItems && !isTransferCancelled

            if (isCut && allSucceeded) {
                SharedClipboardManager.clearClipboard()
            }

            _uiState.update { state ->
                val updatedClipboard = if (isCut && allSucceeded) {
                    null
                } else {
                    SharedClipboardManager.getClipboard() ?: state.clipboardState
                }
                val status = if (isTransferCancelled) {
                    "Transfer cancelled ($successCount of $totalItems completed)"
                } else {
                    "Successfully ${if (isCut) "moved" else "copied"} $successCount of $totalItems item(s) (${FileItem.formatByteSize(finalOverall)})"
                }
                state.copy(
                    transferProgress = null,
                    clipboardState = updatedClipboard,
                    statusText = status
                )
            }

            // Broadcast directory change events to notify ALL open windows/tabs to refresh
            sourceDirs.forEach { SharedClipboardManager.notifyDirectoryChanged(it) }
            SharedClipboardManager.notifyDirectoryChanged(dest)
            if (effectiveDestUri != null) SharedClipboardManager.notifyDirectoryChanged(effectiveDestUri)

            refresh()
        }
    }

    fun moveItemsToDirectory(
        items: List<FileItem>,
        targetDirPath: String,
        targetDirName: String = File(targetDirPath).name.ifEmpty { "folder" },
        targetTreeUri: String? = null
    ) {
        val dest = targetDirPath.trimEnd('/')
        if (dest.isEmpty()) return

        // Filter out target folder itself, its descendants, or items already directly inside targetDirPath
        val validItems = items.filter { item ->
            if (item.path == dest) return@filter false
            if (item.isDirectory && dest.startsWith(item.path.trimEnd('/') + "/")) return@filter false
            val parentPath = File(item.path).parent
            if (parentPath != null && parentPath.trimEnd('/') == dest) return@filter false
            true
        }
        if (validItems.isEmpty()) return

        transferJob?.cancel()
        isTransferCancelled = false

        transferJob = viewModelScope.launch {
            val isExternal = isExternalStoragePath(dest)
            val destUri = targetTreeUri ?: if (isExternal) repository.getPersistedTreeUriForPath(dest) else null

            val isDirectlyWritable = if (isExternal && destUri == null) {
                try { File(dest).canWrite() } catch (_: Exception) { false }
            } else true

            if (isExternal && destUri == null && !isDirectlyWritable) {
                val targetUuid = dest.trimEnd('/').substringAfterLast('/')
                val matchingDrive = _uiState.value.drives.firstOrNull {
                    it.type != DriveType.INTERNAL && it.type != DriveType.LINUX_SHARED &&
                    (it.path == dest || dest.startsWith(it.path) || it.path.contains(targetUuid) || it.id.contains(targetUuid))
                }
                val driveName = matchingDrive?.name ?: "External Drive ($targetUuid)"
                val driveRoot = matchingDrive?.path ?: dest
                _uiState.update {
                    it.copy(
                        statusText = "Write permission required for $driveName. Please grant volume access.",
                        needsVolumeAccess = true,
                        volumeAccessName = driveName,
                        volumeAccessPath = driveRoot
                    )
                }
                return@launch
            }
            val sourceDirs = validItems.mapNotNull { item ->
                if (item.treeUri != null) item.treeUri else File(item.path).parent
            }.distinct()

            var totalBytes = 0L
            val itemSizes = mutableListOf<Long>()
            for (item in validItems) {
                val s = if (item.size > 0L && !item.isDirectory) item.size else repository.calculateItemSize(item.path, item.treeUri)
                itemSizes.add(s)
                totalBytes += s
            }

            val opName = "Moving"
            val totalItems = validItems.size
            var overallBytesCopied = 0L
            val startTime = System.currentTimeMillis()
            var lastUiUpdate = 0L

            _uiState.update { state ->
                state.copy(
                    transferProgress = TransferProgress(
                        operation = opName,
                        currentFileName = validItems.first().name,
                        currentItemIndex = 1,
                        totalItems = totalItems,
                        bytesTransferred = 0L,
                        totalBytes = totalBytes,
                        sourcePath = validItems.first().path,
                        destPath = dest
                    )
                )
            }

            var successCount = 0
            for (i in validItems.indices) {
                if (isTransferCancelled) break
                val item = validItems[i]
                val baseBytesBeforeThisItem = overallBytesCopied

                val progressCallback: (Long, Long, String) -> Unit = { fileBytesCopied, _, currentFileName ->
                    val now = System.currentTimeMillis()
                    val currentTotalCopied = baseBytesBeforeThisItem + fileBytesCopied
                    val elapsedSec = (now - startTime) / 1000.0
                    val speed = if (elapsedSec > 0.1) currentTotalCopied / elapsedSec else 0.0
                    val remainingBytes = (totalBytes - currentTotalCopied).coerceAtLeast(0L)
                    val remainingSec = if (speed > 1024.0) (remainingBytes / speed).toLong() else 0L

                    if (now - lastUiUpdate > 120 || currentTotalCopied >= totalBytes) {
                        lastUiUpdate = now
                        _uiState.update { state ->
                            state.copy(
                                transferProgress = TransferProgress(
                                    operation = opName,
                                    currentFileName = currentFileName,
                                    currentItemIndex = i + 1,
                                    totalItems = totalItems,
                                    bytesTransferred = currentTotalCopied,
                                    totalBytes = totalBytes,
                                    speedBytesPerSec = speed,
                                    estimatedRemainingSeconds = remainingSec,
                                    sourcePath = item.path,
                                    destPath = dest
                                )
                            )
                        }
                    }
                }

                val ok = repository.moveItem(item.path, dest, item.treeUri, destUri, progressCallback, { isTransferCancelled })
                if (ok) {
                    successCount++
                }
                overallBytesCopied = baseBytesBeforeThisItem + itemSizes[i]
            }

            val finalOverall = overallBytesCopied
            _uiState.update { state ->
                val status = if (isTransferCancelled) {
                    "Move cancelled"
                } else {
                    "Successfully moved $successCount of $totalItems item(s) to '$targetDirName' (${FileItem.formatByteSize(finalOverall)})"
                }
                state.copy(
                    transferProgress = null,
                    selectedItemPaths = emptySet(),
                    statusText = status
                )
            }

            sourceDirs.forEach { SharedClipboardManager.notifyDirectoryChanged(it) }
            SharedClipboardManager.notifyDirectoryChanged(dest)
            if (destUri != null) SharedClipboardManager.notifyDirectoryChanged(destUri)

            refresh()
        }
    }

    fun moveItemsToFolder(items: List<FileItem>, targetFolder: FileItem) {
        if (!targetFolder.isDirectory) return
        moveItemsToDirectory(items, targetFolder.path, targetFolder.name, targetFolder.treeUri)
    }

    fun createShortcut(targetItem: FileItem, destDirPath: String? = null, destTreeUri: String? = null) {
        val dest = destDirPath ?: _uiState.value.currentPath
        val destUri = destTreeUri ?: _uiState.value.currentTreeUri
        viewModelScope.launch {
            val ok = repository.createShortcut(targetItem, dest, destUri)
            if (ok) {
                SharedClipboardManager.notifyDirectoryChanged(dest)
                if (destUri != null) SharedClipboardManager.notifyDirectoryChanged(destUri)
                refresh()
                _uiState.update { it.copy(statusText = "Shortcut created for '${targetItem.name}'") }
            } else {
                _uiState.update { it.copy(statusText = "Failed to create shortcut") }
            }
        }
    }

    // --- Quick Access Reordering ---
    fun moveQuickAccessUp(path: String) {
        viewModelScope.launch {
            repository.reorderQuickAccessItem(path, moveUp = true)
            val updated = repository.getQuickAccessItems()
            _uiState.update { it.copy(quickAccessItems = updated) }
        }
    }

    fun moveQuickAccessDown(path: String) {
        viewModelScope.launch {
            repository.reorderQuickAccessItem(path, moveUp = false)
            val updated = repository.getQuickAccessItems()
            _uiState.update { it.copy(quickAccessItems = updated) }
        }
    }

    // --- Pinning Operations ---
    fun isPathPinned(path: String): Boolean {
        val clean = path.trimEnd('/')
        return _uiState.value.quickAccessItems.any { it.path.trimEnd('/') == clean || (it.treeUri != null && it.treeUri == path) }
    }

    fun pinDirectory(path: String, name: String, treeUri: String? = null) {
        viewModelScope.launch {
            val displayName = name.ifEmpty { File(path).name.ifEmpty { path } }
            repository.pinDirectory(path, displayName, treeUri)
            val updated = repository.getQuickAccessItems()
            val pinnedPaths = updated.map { it.path.trimEnd('/') }.toSet()

            _uiState.update { state ->
                val updatedItems = state.items.map { it.copy(isPinned = pinnedPaths.contains(it.path.trimEnd('/'))) }
                val updatedFiltered = state.filteredItems.map { it.copy(isPinned = pinnedPaths.contains(it.path.trimEnd('/'))) }
                state.copy(
                    quickAccessItems = updated,
                    items = updatedItems,
                    filteredItems = updatedFiltered
                )
            }
        }
    }

    fun unpinDirectory(path: String) {
        viewModelScope.launch {
            repository.unpinDirectory(path)
            val updated = repository.getQuickAccessItems()
            val pinnedPaths = updated.map { it.path.trimEnd('/') }.toSet()

            _uiState.update { state ->
                val updatedItems = state.items.map { it.copy(isPinned = pinnedPaths.contains(it.path.trimEnd('/'))) }
                val updatedFiltered = state.filteredItems.map { it.copy(isPinned = pinnedPaths.contains(it.path.trimEnd('/'))) }
                state.copy(
                    quickAccessItems = updated,
                    items = updatedItems,
                    filteredItems = updatedFiltered
                )
            }
        }
    }

    fun togglePin(path: String, name: String = "", treeUri: String? = null) {
        if (isPathPinned(path)) {
            unpinDirectory(path)
        } else {
            pinDirectory(path, name, treeUri)
        }
    }

    fun pinSelectedDirectories() {
        viewModelScope.launch {
            val state = _uiState.value
            val selected = state.selectedDirectories
            if (selected.isNotEmpty()) {
                selected.forEach { dir ->
                    repository.pinDirectory(dir.path, dir.name, dir.treeUri)
                }
            } else if (state.currentPath.isNotEmpty()) {
                repository.pinDirectory(state.currentPath, state.currentFolderName, state.currentTreeUri)
            }
            val updated = repository.getQuickAccessItems()
            val pinnedPaths = updated.map { it.path.trimEnd('/') }.toSet()
            _uiState.update { s ->
                val updatedItems = s.items.map { it.copy(isPinned = pinnedPaths.contains(it.path.trimEnd('/'))) }
                val updatedFiltered = s.filteredItems.map { it.copy(isPinned = pinnedPaths.contains(it.path.trimEnd('/'))) }
                s.copy(
                    quickAccessItems = updated,
                    items = updatedItems,
                    filteredItems = updatedFiltered
                )
            }
        }
    }

    fun unpinSelectedDirectories() {
        viewModelScope.launch {
            val state = _uiState.value
            val selected = state.selectedDirectories
            if (selected.isNotEmpty()) {
                selected.forEach { dir ->
                    repository.unpinDirectory(dir.path)
                }
            } else if (state.currentPath.isNotEmpty()) {
                repository.unpinDirectory(state.currentPath)
            }
            val updated = repository.getQuickAccessItems()
            val pinnedPaths = updated.map { it.path.trimEnd('/') }.toSet()
            _uiState.update { s ->
                val updatedItems = s.items.map { it.copy(isPinned = pinnedPaths.contains(it.path.trimEnd('/'))) }
                val updatedFiltered = s.filteredItems.map { it.copy(isPinned = pinnedPaths.contains(it.path.trimEnd('/'))) }
                s.copy(
                    quickAccessItems = updated,
                    items = updatedItems,
                    filteredItems = updatedFiltered
                )
            }
        }
    }

    fun togglePinSelected() {
        if (_uiState.value.hasSelectedDirectories) {
            if (_uiState.value.areSelectedDirectoriesPinned) {
                unpinSelectedDirectories()
            } else {
                pinSelectedDirectories()
            }
        } else {
            togglePinCurrentDirectory()
        }
    }

    fun togglePinCurrentDirectory() {
        val current = _uiState.value
        if (current.currentPath.isNotEmpty()) {
            togglePin(current.currentPath, current.currentFolderName, current.currentTreeUri)
        }
    }

    fun createDirectory(name: String, targetDirPath: String? = null, targetTreeUri: String? = null) {
        val current = _uiState.value
        val dir = targetDirPath ?: current.currentPath
        val isExternal = isExternalStoragePath(dir)

        viewModelScope.launch {
            val treeUri = targetTreeUri ?: current.currentTreeUri
                ?: if (isExternal) repository.getPersistedTreeUriForPath(dir) else null

            val isDirectlyWritable = if (isExternal && treeUri == null) {
                try { File(dir).canWrite() } catch (_: Exception) { false }
            } else true

            if (isExternal && treeUri == null && !isDirectlyWritable) {
                val targetUuid = dir.trimEnd('/').substringAfterLast('/')
                val matchingDrive = current.drives.firstOrNull {
                    it.type != DriveType.INTERNAL && it.type != DriveType.LINUX_SHARED &&
                    (it.path == dir || dir.startsWith(it.path) || it.path.contains(targetUuid) || it.id.contains(targetUuid))
                }
                val driveName = matchingDrive?.name ?: "External Drive ($targetUuid)"
                val driveRoot = matchingDrive?.path ?: dir
                _uiState.update {
                    it.copy(
                        statusText = "Write permission required for $driveName. Please grant volume access.",
                        needsVolumeAccess = true,
                        volumeAccessName = driveName,
                        volumeAccessPath = driveRoot
                    )
                }
                return@launch
            }

            val ok = repository.createDirectory(dir, name, treeUri)
            if (ok) {
                SharedClipboardManager.notifyDirectoryChanged(dir)
                if (treeUri != null) SharedClipboardManager.notifyDirectoryChanged(treeUri)
                refresh()
            } else {
                _uiState.update { it.copy(statusText = "Failed to create folder '$name'") }
            }
        }
    }

    fun createTextFile(name: String, content: String = "", targetDirPath: String? = null, targetTreeUri: String? = null) {
        val current = _uiState.value
        val dir = targetDirPath ?: current.currentPath
        val isExternal = isExternalStoragePath(dir)

        viewModelScope.launch {
            val treeUri = targetTreeUri ?: current.currentTreeUri
                ?: if (isExternal) repository.getPersistedTreeUriForPath(dir) else null

            val isDirectlyWritable = if (isExternal && treeUri == null) {
                try { File(dir).canWrite() } catch (_: Exception) { false }
            } else true

            if (isExternal && treeUri == null && !isDirectlyWritable) {
                val targetUuid = dir.trimEnd('/').substringAfterLast('/')
                val matchingDrive = current.drives.firstOrNull {
                    it.type != DriveType.INTERNAL && it.type != DriveType.LINUX_SHARED &&
                    (it.path == dir || dir.startsWith(it.path) || it.path.contains(targetUuid) || it.id.contains(targetUuid))
                }
                val driveName = matchingDrive?.name ?: "External Drive ($targetUuid)"
                val driveRoot = matchingDrive?.path ?: dir
                _uiState.update {
                    it.copy(
                        statusText = "Write permission required for $driveName. Please grant volume access.",
                        needsVolumeAccess = true,
                        volumeAccessName = driveName,
                        volumeAccessPath = driveRoot
                    )
                }
                return@launch
            }

            val ok = repository.createTextFile(dir, name, content, treeUri)
            if (ok) {
                SharedClipboardManager.notifyDirectoryChanged(dir)
                if (treeUri != null) SharedClipboardManager.notifyDirectoryChanged(treeUri)
                refresh()
            } else {
                _uiState.update { it.copy(statusText = "Failed to create file '$name'") }
            }
        }
    }

    fun renameItem(path: String, newName: String) {
        val current = _uiState.value
        viewModelScope.launch {
            repository.rename(path, newName, current.currentTreeUri)
            SharedClipboardManager.notifyDirectoryChanged(current.currentPath)
            if (current.currentTreeUri != null) SharedClipboardManager.notifyDirectoryChanged(current.currentTreeUri)
            refresh()
        }
    }

    fun deleteSelected() {
        val current = _uiState.value
        viewModelScope.launch {
            current.selectedItemPaths.forEach { path ->
                repository.delete(path, current.currentTreeUri)
            }
            clearSelection()
            SharedClipboardManager.notifyDirectoryChanged(current.currentPath)
            if (current.currentTreeUri != null) SharedClipboardManager.notifyDirectoryChanged(current.currentTreeUri)
            refresh()
        }
    }

    fun addCloudDrive(name: String, treeUri: String) {
        viewModelScope.launch {
            repository.addCloudDrive(name, treeUri)
            val updated = repository.getCloudDrives()
            _uiState.update { it.copy(cloudDrives = updated) }
            navigateTo(name, treeUri)
        }
    }

    fun removeCloudDrive(treeUri: String) {
        viewModelScope.launch {
            repository.removeCloudDrive(treeUri)
            val updated = repository.getCloudDrives()
            _uiState.update { it.copy(cloudDrives = updated) }
            if (_uiState.value.currentTreeUri == treeUri) {
                val defaultPath = _uiState.value.drives.firstOrNull()?.path ?: "/storage/emulated/10"
                navigateTo(defaultPath)
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        val wasGranted = _uiState.value.hasStoragePermission
        _uiState.update { it.copy(hasStoragePermission = granted) }
        if (granted) {
            if (_uiState.value.tabs.isEmpty()) {
                loadInitialData()
            } else {
                refreshDrives()
                if (!wasGranted) {
                    refresh()
                }
            }
        }
    }

    private fun sortAndFilter(items: List<FileItem>, query: String, sort: SortOption): List<FileItem> {
        val filtered = if (query.isBlank()) {
            items
        } else {
            items.filter { it.name.contains(query, ignoreCase = true) || it.extension.contains(query, ignoreCase = true) }
        }

        val folders = filtered.filter { it.isDirectory }
        val files = filtered.filter { !it.isDirectory }

        val folderComparator = getComparator(sort)
        val fileComparator = getComparator(sort)

        return folders.sortedWith(folderComparator) + files.sortedWith(fileComparator)
    }

    private fun getComparator(sort: SortOption): Comparator<FileItem> {
        val base = when (sort.field) {
            SortField.NAME -> compareBy<FileItem> { it.name.lowercase() }
            SortField.DATE_MODIFIED -> compareBy<FileItem> { it.lastModified }
            SortField.SIZE -> compareBy<FileItem> { it.size }
            SortField.TYPE -> compareBy<FileItem> { it.extension.lowercase() }.thenBy { it.name.lowercase() }
        }
        return if (sort.direction == SortDirection.ASCENDING) base else base.reversed()
    }

    private fun generateBreadcrumbs(path: String, treeUri: String?): List<Breadcrumb> {
        if (treeUri != null && path.startsWith("content://")) {
            return parseTreeBreadcrumbs(path, treeUri)
        }
        if (path.isEmpty()) return listOf(Breadcrumb("This PC", ""))

        val crumbs = mutableListOf(Breadcrumb("This PC", ""))
        val cleanPath = path.trimEnd('/')

        // Find the most specific matching drive
        val matchingDrive = _uiState.value.drives
            .filter { it.path.isNotEmpty() && (cleanPath == it.path || cleanPath.startsWith(it.path + "/")) }
            .maxByOrNull { it.path.length }

        if (matchingDrive != null) {
            crumbs.add(Breadcrumb(matchingDrive.name, matchingDrive.path, matchingDrive.treeUri))
            val relPath = cleanPath.removePrefix(matchingDrive.path).trim('/')
            if (relPath.isNotEmpty()) {
                val segments = relPath.split("/").filter { it.isNotEmpty() }
                var accumulated = matchingDrive.path
                for (seg in segments) {
                    accumulated += "/$seg"
                    crumbs.add(Breadcrumb(seg, accumulated, matchingDrive.treeUri))
                }
            }
        } else {
            val segments = cleanPath.split("/").filter { it.isNotEmpty() }
            var accumulated = ""
            for (seg in segments) {
                accumulated += "/$seg"
                val display = when {
                    accumulated == "/storage/emulated/10" || accumulated == "/storage/emulated/0" -> "Internal Storage (C:)"
                    accumulated == "/storage/emulated/10/Apps" -> "Linux Apps & Projects"
                    else -> seg
                }
                crumbs.add(Breadcrumb(display, accumulated, treeUri))
            }
        }
        return crumbs
    }

    private fun parseTreeBreadcrumbs(path: String, treeUriStr: String): List<Breadcrumb> {
        val crumbs = mutableListOf(Breadcrumb("This PC", ""))
        val uri = try { Uri.parse(treeUriStr) } catch (_: Exception) { return crumbs }

        val matchingDrive = _uiState.value.drives.firstOrNull {
            (it.treeUri != null && treeUriStr.startsWith(it.treeUri)) ||
            (it.path.isNotEmpty() && path.startsWith(it.path))
        } ?: _uiState.value.cloudDrives.firstOrNull {
            it.treeUri != null && treeUriStr.startsWith(it.treeUri)
        }

        val rootName = matchingDrive?.name ?: if (treeUriStr.contains("com.android.externalstorage.documents")) {
            "External Drive"
        } else if (treeUriStr.contains("com.android.mtp.documents") || treeUriStr.startsWith("mtp")) {
            "Android Smartphone"
        } else {
            "Cloud Storage"
        }
        val rootTreeUri = matchingDrive?.treeUri ?: try {
            val treeId = DocumentsContract.getTreeDocumentId(uri)
            DocumentsContract.buildTreeDocumentUri(uri.authority, treeId).toString()
        } catch (_: Exception) {
            treeUriStr
        }

        crumbs.add(Breadcrumb(rootName, matchingDrive?.path ?: rootTreeUri, rootTreeUri))

        try {
            if (treeUriStr.contains("/document/")) {
                val docId = DocumentsContract.getDocumentId(uri)
                val treeId = DocumentsContract.getTreeDocumentId(uri)

                val relativePath = if (docId.startsWith(treeId)) {
                    docId.substring(treeId.length).trim('/')
                } else if (docId.contains(":")) {
                    docId.substringAfter(":").trim('/')
                } else {
                    ""
                }

                if (relativePath.isNotEmpty()) {
                    val segments = relativePath.split("/").filter { it.isNotEmpty() }
                    var accumRel = ""
                    val prefix = if (treeId.endsWith(":")) treeId else "$treeId:"
                    for (seg in segments) {
                        accumRel = if (accumRel.isEmpty()) seg else "$accumRel/$seg"
                        val segDocId = "$prefix$accumRel"
                        val segUri = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(rootTreeUri), segDocId).toString()
                        crumbs.add(Breadcrumb(seg, segUri, segUri))
                    }
                }
            }
        } catch (_: Exception) {}

        return crumbs
    }

    private fun generateStatusText(items: List<FileItem>, selectedPaths: Set<String>): String {
        val totalCount = items.size
        val selectedCount = selectedPaths.size
        return if (selectedCount > 0) {
            val totalBytes = items.filter { selectedPaths.contains(it.path) }.sumOf { it.size }
            "$totalCount items | $selectedCount items selected (${FileItem.formatByteSize(totalBytes)})"
        } else {
            "$totalCount items"
        }
    }

    private fun isExternalStoragePath(path: String): Boolean {
        val clean = path.trimEnd('/')
        if (clean.isEmpty() || clean == "/storage" || clean.startsWith("/storage/emulated") || clean.startsWith("/storage/self") || clean.startsWith("/mnt/shared")) {
            return false
        }
        if (clean.startsWith("mtp://") || clean.contains("com.android.mtp.documents")) {
            return true
        }
        return clean.startsWith("/mnt/media_rw") ||
               clean.startsWith("/mnt/pass_through") ||
               (clean.startsWith("/mnt/") && !clean.startsWith("/mnt/sdcard")) ||
               clean.startsWith("/storage/")
    }
}
