package com.droid.fileexplorer.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.droid.fileexplorer.MainActivity
import com.droid.fileexplorer.model.FileItem
import com.droid.fileexplorer.model.ViewMode
import com.droid.fileexplorer.theme.Win11Blue
import com.droid.fileexplorer.ui.components.MainContentDetails
import com.droid.fileexplorer.ui.components.MainContentGrid
import com.droid.fileexplorer.ui.components.PermissionBanner
import com.droid.fileexplorer.ui.components.QuickAccessSidebar
import com.droid.fileexplorer.ui.components.WindowsCommandBar
import com.droid.fileexplorer.ui.components.WindowsTopBar
import com.droid.fileexplorer.ui.dialogs.DeleteConfirmDialog
import com.droid.fileexplorer.ui.dialogs.FileTransferDialog
import com.droid.fileexplorer.ui.dialogs.NewItemDialog
import com.droid.fileexplorer.ui.dialogs.PropertiesDialog
import com.droid.fileexplorer.ui.dialogs.RenameDialog
import android.os.storage.StorageManager
import com.droid.fileexplorer.ui.components.VolumeAccessCard
import com.droid.fileexplorer.util.FileOpener
import com.droid.fileexplorer.util.ShortcutHelper
import com.droid.fileexplorer.viewmodel.FileExplorerViewModel

@Composable
fun FileExplorerApp(
    viewModel: FileExplorerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Dialog States
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var deleteTargetCount by remember { mutableStateOf<Int?>(null) }
    var propertiesTarget by remember { mutableStateOf<FileItem?>(null) }
    var isEditingAddress by remember { mutableStateOf(false) }
    var newItemTargetFolder by remember { mutableStateOf<FileItem?>(null) }

    LaunchedEffect(uiState.currentPath, uiState.activeTabId) {
        isEditingAddress = false
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    // Check MANAGE_EXTERNAL_STORAGE permission
    fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onPermissionResult(checkPermission())
                viewModel.refreshDrives()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Storage Access Framework Launcher for Cloud Drives / Google Drive / External Documents
    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)

                val displayName = uri.lastPathSegment ?: "Cloud Drive"
                val cleanName = when {
                    displayName.contains("primary:") -> displayName.substringAfter("primary:")
                    displayName.contains(":") -> displayName.substringAfter(":")
                    else -> displayName
                }.ifEmpty { "Cloud Drive" }

                // If this is an external storage drive (e.g. from com.android.externalstorage.documents), attach as volume
                if (uri.authority == "com.android.externalstorage.documents") {
                    val uuid = uri.lastPathSegment?.substringBefore(":") ?: ""
                    if (uuid.isNotEmpty() && uuid != "primary") {
                        viewModel.attachVolumeUri("/storage/$uuid", uri.toString())
                    }
                } else if (uri.authority == "com.android.mtp.documents") {
                    viewModel.attachVolumeUri(uri.toString(), uri.toString())
                }

                viewModel.addCloudDrive(cleanName, uri.toString())
            } catch (e: Exception) {
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    viewModel.addCloudDrive("Cloud Drive", uri.toString())
                } catch (_: Exception) {}
            }
        }
    }

    // Storage Access Framework Launcher for External Hard Drives / USB Drives / Smartphones
    val volumeAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri != null) {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: Exception) {
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
            }
            val uriStr = uri.toString()
            val isMtp = uri.authority == "com.android.mtp.documents" || uiState.volumeAccessPath.startsWith("mtp")
            val uuid = uri.lastPathSegment?.substringBefore(":") ?: ""
            val targetPath = if (uiState.volumeAccessPath.isNotEmpty()) {
                uiState.volumeAccessPath
            } else if (isMtp) {
                uriStr
            } else if (uuid.isNotEmpty() && uuid != "primary") {
                "/storage/$uuid"
            } else ""
            viewModel.attachVolumeUri(targetPath, uriStr)
        }
    }

    fun launchVolumeAccess(path: String) {
        val isMtp = path.startsWith("mtp") || path.contains("com.android.mtp.documents")
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        val volume = if (!isMtp && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && storageManager != null) {
            storageManager.storageVolumes.find { vol ->
                val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) vol.directory else null
                dir?.absolutePath == path || (vol.uuid != null && path.contains(vol.uuid!!))
            }
        } else null

        var intent: Intent? = null
        if (!isMtp && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && volume != null) {
            try {
                intent = volume.createOpenDocumentTreeIntent()
            } catch (_: Exception) {}
        }

        if (intent == null) {
            intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (isMtp) {
                        try {
                            val rootsUri = DocumentsContract.buildRootsUri("com.android.mtp.documents")
                            putExtra(DocumentsContract.EXTRA_INITIAL_URI, rootsUri)
                        } catch (_: Exception) {}
                    } else {
                        val uuid = volume?.uuid ?: path.trimEnd('/').substringAfterLast('/')
                        if (uuid.isNotEmpty()) {
                            try {
                                val rootUri = DocumentsContract.buildRootUri("com.android.externalstorage.documents", uuid)
                                putExtra(DocumentsContract.EXTRA_INITIAL_URI, rootUri)
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }

        try {
            volumeAccessLauncher.launch(intent)
        } catch (_: Exception) {
            openDocumentTreeLauncher.launch(null)
        }
    }

    fun openInNewWindow(targetPath: String, targetTreeUri: String? = null) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(MainActivity.EXTRA_INITIAL_PATH, targetPath)
                putExtra(MainActivity.EXTRA_INITIAL_TREE_URI, targetTreeUri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open new window: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (renameTarget == null && !showNewFolderDialog && !showNewFileDialog) {
                    if (event.type == KeyEventType.KeyDown) {
                        if (event.isCtrlPressed) {
                            when (event.key) {
                                Key.N -> {
                                    openInNewWindow(uiState.currentPath, uiState.currentTreeUri)
                                    true
                                }
                                Key.T -> {
                                    viewModel.openNewTab()
                                    true
                                }
                                Key.W -> {
                                    if (uiState.tabs.size > 1) {
                                        viewModel.closeTab(uiState.activeTabId)
                                    }
                                    true
                                }
                                Key.X -> {
                                    viewModel.cutSelected()
                                    true
                                }
                                Key.C -> {
                                    viewModel.copySelected()
                                    true
                                }
                                Key.V -> {
                                    if (uiState.canPaste) {
                                        viewModel.paste()
                                    }
                                    true
                                }
                                Key.A -> {
                                    viewModel.selectAll()
                                    true
                                }
                                Key.L -> {
                                    isEditingAddress = true
                                    true
                                }
                                else -> false
                            }
                        } else if (event.isAltPressed && event.key == Key.D) {
                            isEditingAddress = true
                            true
                        } else if (event.key == Key.F5) {
                            viewModel.refresh()
                            true
                        } else if (event.key == Key.Delete) {
                            if (uiState.selectedItemPaths.isNotEmpty()) {
                                deleteTargetCount = uiState.selectedItemPaths.size
                                true
                            } else false
                        } else false
                    } else false
                } else false
            }
    ) {
        // 1. Top Bar (Tabs, Navigation, Breadcrumbs Address Bar, Search, Pin Active Folder)
        WindowsTopBar(
            tabs = uiState.tabs,
            activeTabId = uiState.activeTabId,
            currentFolderName = uiState.currentFolderName,
            currentPath = uiState.currentPath,
            breadcrumbs = uiState.breadcrumbs,
            searchQuery = uiState.searchQuery,
            isCurrentFolderPinned = uiState.isCurrentDirectoryPinned,
            onTabClick = { tabId -> viewModel.switchTab(tabId) },
            onTabClose = { tabId -> viewModel.closeTab(tabId) },
            onNewTab = { viewModel.openNewTab() },
            onOpenNewWindow = { openInNewWindow(uiState.currentPath, uiState.currentTreeUri) },
            onTogglePinCurrent = { viewModel.togglePinCurrentDirectory() },
            onBack = { viewModel.navigateBack() },
            onForward = { viewModel.navigateForward() },
            onUp = { viewModel.navigateUp() },
            onRefresh = { viewModel.refresh() },
            onBreadcrumbClick = { crumb -> viewModel.navigateTo(crumb.path, crumb.treeUri) },
            onSearchChange = { viewModel.setSearchQuery(it) },
            isEditingAddress = isEditingAddress,
            onEditingAddressChange = { isEditingAddress = it },
            onAddressSubmit = { path -> viewModel.navigateToPathInput(path) }
        )

        // 2. Windows 11 Command Bar with Cut, Copy, Paste, Pin, View, Sort
        val canPin = uiState.hasSelectedDirectories || uiState.currentPath.isNotEmpty()
        val isPinActive = if (uiState.hasSelectedDirectories) uiState.areSelectedDirectoriesPinned else uiState.isCurrentDirectoryPinned

        WindowsCommandBar(
            viewMode = uiState.viewMode,
            sortOption = uiState.sortOption,
            hasSelection = uiState.selectedItemPaths.isNotEmpty(),
            canPin = canPin,
            isPinned = isPinActive,
            canPaste = uiState.canPaste,
            showVideoThumbnails = uiState.showVideoThumbnails,
            onCut = { viewModel.cutSelected() },
            onCopy = { viewModel.copySelected() },
            onPaste = { viewModel.paste() },
            onTogglePin = { viewModel.togglePinSelected() },
            onViewModeChange = { viewModel.setViewMode(it) },
            onSortChange = { viewModel.setSortOption(it) },
            onToggleVideoThumbnails = { viewModel.toggleVideoThumbnails() },
            onNewFolder = {
                newItemTargetFolder = null
                showNewFolderDialog = true
            },
            onNewFile = {
                newItemTargetFolder = null
                showNewFileDialog = true
            },
            onOpenNewWindow = { openInNewWindow(uiState.currentPath, uiState.currentTreeUri) },
            onDelete = {
                deleteTargetCount = uiState.selectedItemPaths.size
            },
            onRename = {
                val firstSelected = uiState.filteredItems.firstOrNull { uiState.selectedItemPaths.contains(it.path) }
                if (firstSelected != null) {
                    renameTarget = firstSelected
                }
            },
            onShare = {
                val firstSelected = uiState.filteredItems.firstOrNull { uiState.selectedItemPaths.contains(it.path) }
                if (firstSelected != null) {
                    FileOpener.shareFile(context, firstSelected)
                }
            },
            onSelectAll = {
                if (uiState.selectedItemPaths.size == uiState.filteredItems.size && uiState.filteredItems.isNotEmpty()) {
                    viewModel.clearSelection()
                } else {
                    viewModel.selectAll()
                }
            }
        )

        // 3. Tablet Split Layout: Left-Hand Sidebar + Right-Hand Main Grid/Details
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Left-Hand Sidebar: Quick Access
            QuickAccessSidebar(
                quickAccessItems = uiState.quickAccessItems,
                drives = uiState.drives,
                cloudDrives = uiState.cloudDrives,
                currentPath = uiState.currentPath,
                currentTreeUri = uiState.currentTreeUri,
                onNavigate = { path, treeUri ->
                    isEditingAddress = false
                    viewModel.navigateTo(path, treeUri)
                },
                onOpenInNewTab = { path, treeUri ->
                    isEditingAddress = false
                    viewModel.openNewTab(path, treeUri)
                },
                onOpenInNewWindow = { path, treeUri -> openInNewWindow(path, treeUri) },
                onMoveUp = { path -> viewModel.moveQuickAccessUp(path) },
                onMoveDown = { path -> viewModel.moveQuickAccessDown(path) },
                onPinCurrentFolder = { viewModel.togglePinCurrentDirectory() },
                onUnpin = { path -> viewModel.unpinDirectory(path) },
                onAddCloudDrive = { openDocumentTreeLauncher.launch(null) },
                onRemoveCloudDrive = { uri -> viewModel.removeCloudDrive(uri) }
            )

            // Right-Hand Main Explorer Area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Permission Warning Banner if not granted
                if (!uiState.hasStoragePermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    PermissionBanner(
                        onGrantClick = {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                val genericIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(genericIntent)
                            }
                        }
                    )
                }

                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Win11Blue)
                    }
                } else if (uiState.needsVolumeAccess) {
                    VolumeAccessCard(
                        driveName = uiState.volumeAccessName,
                        onGrantAccess = { launchVolumeAccess(uiState.volumeAccessPath) }
                    )
                } else {
                    if (uiState.isReadOnlyVolume) {
                        ReadOnlyDriveBanner(
                            driveName = uiState.readOnlyVolumeName,
                            onGrantAccess = { launchVolumeAccess(uiState.readOnlyVolumePath) }
                        )
                    }
                    val handleItemClick: (FileItem) -> Unit = { item ->
                        isEditingAddress = false
                        if (item.isShortcut && !item.shortcutTargetPath.isNullOrEmpty()) {
                            if (item.shortcutTargetIsDirectory) {
                                viewModel.navigateTo(item.shortcutTargetPath, item.shortcutTargetTreeUri)
                            } else {
                                val targetFile = java.io.File(item.shortcutTargetPath)
                                if (targetFile.exists()) {
                                    val targetItem = FileItem(
                                        id = targetFile.absolutePath,
                                        name = targetFile.name,
                                        path = targetFile.absolutePath,
                                        isDirectory = false,
                                        size = targetFile.length(),
                                        extension = targetFile.extension
                                    )
                                    FileOpener.openFile(context, targetItem)
                                } else {
                                    Toast.makeText(context, "Shortcut target no longer exists", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else if (item.isDirectory) {
                            viewModel.navigateTo(item.path, item.treeUri)
                        } else {
                            FileOpener.openFile(context, item)
                        }
                    }

                    val handleItemSelectionClick: (FileItem, Boolean, Boolean) -> Unit = { item, isCtrl, isShift ->
                        val currentSelection = uiState.selectedItemPaths
                        val items = uiState.filteredItems

                        if (isCtrl) {
                            // Ctrl + Click: toggle item in selection
                            viewModel.toggleSelect(item.path)
                        } else if (isShift && currentSelection.isNotEmpty()) {
                            // Shift + Click: range select
                            val lastSelectedPath = currentSelection.lastOrNull()
                            val lastIndex = items.indexOfFirst { it.path == lastSelectedPath }
                            val thisIndex = items.indexOfFirst { it.path == item.path }
                            if (lastIndex != -1 && thisIndex != -1) {
                                val start = minOf(lastIndex, thisIndex)
                                val end = maxOf(lastIndex, thisIndex)
                                val rangePaths = items.subList(start, end + 1).map { it.path }.toSet()
                                viewModel.selectPaths(currentSelection + rangePaths)
                            } else {
                                viewModel.selectPaths(currentSelection + item.path)
                            }
                        } else {
                            // Normal click (touch or mouse without modifier keys)
                            if (currentSelection.size >= 2) {
                                if (currentSelection.contains(item.path)) {
                                    // Clicking an already selected item preserves the selection group
                                } else {
                                    // Add to multi-selection
                                    viewModel.selectPaths(currentSelection + item.path)
                                }
                            } else if (currentSelection.size == 1 && currentSelection.contains(item.path)) {
                                // Already selected: keep selected
                            } else {
                                // Select this single item
                                viewModel.selectPaths(setOf(item.path))
                            }
                        }
                    }

                    val getTargetItems: (FileItem) -> List<FileItem> = { item ->
                        if (uiState.selectedItemPaths.contains(item.path) && uiState.selectedItemPaths.size > 1) {
                            uiState.filteredItems.filter { uiState.selectedItemPaths.contains(it.path) }
                        } else {
                            listOf(item)
                        }
                    }

                    when (uiState.viewMode) {
                        ViewMode.GRID -> {
                            MainContentGrid(
                                items = uiState.filteredItems,
                                selectedPaths = uiState.selectedItemPaths,
                                currentPath = uiState.currentPath,
                                currentFolderName = uiState.currentFolderName,
                                currentTreeUri = uiState.currentTreeUri,
                                canPaste = uiState.canPaste,
                                onItemClick = handleItemSelectionClick,
                                onItemDoubleClick = handleItemClick,
                                onItemSelect = { item -> viewModel.toggleSelect(item.path) },
                                onSelectionChange = { paths -> viewModel.selectPaths(paths) },
                                onMoveItemsToFolder = { dragged, target -> viewModel.moveItemsToFolder(dragged, target) },
                                onMoveItemsToDirectory = { items, path, name, treeUri -> viewModel.moveItemsToDirectory(items, path, name, treeUri) },
                                onOpenInNewTab = { item ->
                                    val targetPath = if (item.isShortcut) item.shortcutTargetPath ?: item.path else item.path
                                    val targetUri = if (item.isShortcut) item.shortcutTargetTreeUri ?: item.treeUri else item.treeUri
                                    viewModel.openNewTab(targetPath, targetUri)
                                },
                                onOpenInNewWindow = { item ->
                                    val targetPath = if (item.isShortcut) item.shortcutTargetPath ?: item.path else item.path
                                    val targetUri = if (item.isShortcut) item.shortcutTargetTreeUri ?: item.treeUri else item.treeUri
                                    openInNewWindow(targetPath, targetUri)
                                },
                                onCut = { item -> viewModel.cutItems(getTargetItems(item)) },
                                onCopy = { item -> viewModel.copyItems(getTargetItems(item)) },
                                onPasteInto = { item -> viewModel.paste(item.path, item.treeUri) },
                                onBackgroundPaste = { viewModel.paste() },
                                onPin = { item -> viewModel.togglePin(item.path, item.name, item.treeUri) },
                                onCreateShortcut = { item -> viewModel.createShortcut(item) },
                                onPinToDesktop = { item -> ShortcutHelper.createDesktopShortcut(context, item) },
                                onRename = { item -> renameTarget = item },
                                onDelete = { item ->
                                    val toDelete = getTargetItems(item)
                                    viewModel.selectPaths(toDelete.map { it.path }.toSet())
                                    deleteTargetCount = toDelete.size
                                },
                                onShare = { item -> FileOpener.shareFile(context, item) },
                                onProperties = { item -> propertiesTarget = item },
                                showVideoThumbnails = uiState.showVideoThumbnails,
                                onToggleVideoThumbnails = { viewModel.toggleVideoThumbnails() },
                                onNewFolder = {
                                    newItemTargetFolder = null
                                    showNewFolderDialog = true
                                },
                                onNewFile = {
                                    newItemTargetFolder = null
                                    showNewFileDialog = true
                                },
                                onNewFolderIn = { folder ->
                                    newItemTargetFolder = folder
                                    showNewFolderDialog = true
                                },
                                onNewFileIn = { folder ->
                                    newItemTargetFolder = folder
                                    showNewFileDialog = true
                                },
                                onRefresh = { viewModel.refresh() }
                            )
                        }
                        ViewMode.DETAILS -> {
                            MainContentDetails(
                                items = uiState.filteredItems,
                                selectedPaths = uiState.selectedItemPaths,
                                currentPath = uiState.currentPath,
                                currentFolderName = uiState.currentFolderName,
                                currentTreeUri = uiState.currentTreeUri,
                                canPaste = uiState.canPaste,
                                onItemClick = handleItemSelectionClick,
                                onItemDoubleClick = handleItemClick,
                                onItemSelect = { item -> viewModel.toggleSelect(item.path) },
                                onSelectionChange = { paths -> viewModel.selectPaths(paths) },
                                onMoveItemsToFolder = { dragged, target -> viewModel.moveItemsToFolder(dragged, target) },
                                onMoveItemsToDirectory = { items, path, name, treeUri -> viewModel.moveItemsToDirectory(items, path, name, treeUri) },
                                onOpenInNewTab = { item ->
                                    val targetPath = if (item.isShortcut) item.shortcutTargetPath ?: item.path else item.path
                                    val targetUri = if (item.isShortcut) item.shortcutTargetTreeUri ?: item.treeUri else item.treeUri
                                    viewModel.openNewTab(targetPath, targetUri)
                                },
                                onOpenInNewWindow = { item ->
                                    val targetPath = if (item.isShortcut) item.shortcutTargetPath ?: item.path else item.path
                                    val targetUri = if (item.isShortcut) item.shortcutTargetTreeUri ?: item.treeUri else item.treeUri
                                    openInNewWindow(targetPath, targetUri)
                                },
                                onCut = { item -> viewModel.cutItems(getTargetItems(item)) },
                                onCopy = { item -> viewModel.copyItems(getTargetItems(item)) },
                                onPasteInto = { item -> viewModel.paste(item.path, item.treeUri) },
                                onBackgroundPaste = { viewModel.paste() },
                                onPin = { item -> viewModel.togglePin(item.path, item.name, item.treeUri) },
                                onCreateShortcut = { item -> viewModel.createShortcut(item) },
                                onPinToDesktop = { item -> ShortcutHelper.createDesktopShortcut(context, item) },
                                onRename = { item -> renameTarget = item },
                                onDelete = { item ->
                                    val toDelete = getTargetItems(item)
                                    viewModel.selectPaths(toDelete.map { it.path }.toSet())
                                    deleteTargetCount = toDelete.size
                                },
                                onShare = { item -> FileOpener.shareFile(context, item) },
                                onProperties = { item -> propertiesTarget = item },
                                showVideoThumbnails = uiState.showVideoThumbnails,
                                onToggleVideoThumbnails = { viewModel.toggleVideoThumbnails() },
                                onNewFolder = {
                                    newItemTargetFolder = null
                                    showNewFolderDialog = true
                                },
                                onNewFile = {
                                    newItemTargetFolder = null
                                    showNewFileDialog = true
                                },
                                onNewFolderIn = { folder ->
                                    newItemTargetFolder = folder
                                    showNewFolderDialog = true
                                },
                                onNewFileIn = { folder ->
                                    newItemTargetFolder = folder
                                    showNewFileDialog = true
                                },
                                onRefresh = { viewModel.refresh() }
                            )
                        }
                    }
                }
            }
        }

        // 4. Windows 11 Bottom Status Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = uiState.statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Dialogs
    if (showNewFolderDialog) {
        val target = newItemTargetFolder
        NewItemDialog(
            isFolder = true,
            parentFolderName = target?.name,
            onDismiss = {
                showNewFolderDialog = false
                newItemTargetFolder = null
            },
            onConfirm = { name ->
                showNewFolderDialog = false
                val targetDir = target?.path ?: uiState.currentPath
                val targetTree = target?.treeUri ?: uiState.currentTreeUri
                viewModel.createDirectory(name, targetDir, targetTree)
                newItemTargetFolder = null
            }
        )
    }

    if (showNewFileDialog) {
        val target = newItemTargetFolder
        NewItemDialog(
            isFolder = false,
            parentFolderName = target?.name,
            onDismiss = {
                showNewFileDialog = false
                newItemTargetFolder = null
            },
            onConfirm = { name ->
                showNewFileDialog = false
                val targetDir = target?.path ?: uiState.currentPath
                val targetTree = target?.treeUri ?: uiState.currentTreeUri
                viewModel.createTextFile(name = name, targetDirPath = targetDir, targetTreeUri = targetTree)
                newItemTargetFolder = null
            }
        )
    }

    renameTarget?.let { item ->
        RenameDialog(
            currentItem = item,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                renameTarget = null
                viewModel.renameItem(item.path, newName)
            }
        )
    }

    deleteTargetCount?.let { count ->
        DeleteConfirmDialog(
            itemCount = count,
            onDismiss = { deleteTargetCount = null },
            onConfirm = {
                deleteTargetCount = null
                viewModel.deleteSelected()
            }
        )
    }

    propertiesTarget?.let { item ->
        PropertiesDialog(
            item = item,
            onDismiss = { propertiesTarget = null }
        )
    }

    uiState.transferProgress?.let { progress ->
        FileTransferDialog(
            progress = progress,
            onCancel = { viewModel.cancelTransfer() }
        )
    }
}

@Composable
private fun ReadOnlyDriveBanner(
    driveName: String,
    onGrantAccess: () -> Unit
) {
    Surface(
        color = Color(0xFF28251C),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFD89B00).copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Read-only drive",
                    tint = Color(0xFFFFB900),
                    modifier = Modifier.padding(end = 10.dp)
                )
                Text(
                    text = "$driveName is currently Read-Only. Grant write access to copy, move, delete, or create files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onGrantAccess,
                colors = ButtonDefaults.buttonColors(containerColor = Win11Blue),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = "Grant Access",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
            }
        }
    }
}
