package com.droid.fileexplorer

import com.droid.fileexplorer.data.FileRepository
import com.droid.fileexplorer.data.SharedClipboardManager
import com.droid.fileexplorer.model.ClipboardOperation
import com.droid.fileexplorer.model.DriveItem
import com.droid.fileexplorer.model.DriveType
import com.droid.fileexplorer.model.FileItem
import com.droid.fileexplorer.model.QuickAccessItem
import com.droid.fileexplorer.model.QuickAccessType
import com.droid.fileexplorer.model.SortDirection
import com.droid.fileexplorer.model.SortField
import com.droid.fileexplorer.model.SortOption
import com.droid.fileexplorer.model.ViewMode
import com.droid.fileexplorer.viewmodel.FileExplorerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeFileRepository : FileRepository {
    val filesMap = mutableMapOf<String, MutableList<FileItem>>()
    val pinned = mutableListOf<QuickAccessItem>()
    val drives = mutableListOf<DriveItem>()
    val cloudDrives = mutableListOf<DriveItem>()

    init {
        val rootPath = "/storage/emulated/10"
        drives.add(DriveItem("internal", "Internal Storage", rootPath, 128_000_000_000L, 64_000_000_000L, DriveType.INTERNAL))
        drives.add(DriveItem("linux", "Linux Shared", "/storage/emulated/10/Apps", 128_000_000_000L, 64_000_000_000L, DriveType.LINUX_SHARED))

        pinned.add(QuickAccessItem("docs", "Documents", "$rootPath/Documents", QuickAccessType.SYSTEM, isRemovable = true))
        pinned.add(QuickAccessItem("downloads", "Downloads", "$rootPath/Download", QuickAccessType.SYSTEM, isRemovable = true))

        filesMap[rootPath] = mutableListOf(
            FileItem("1", "Documents", "$rootPath/Documents", isDirectory = true, isPinned = true),
            FileItem("2", "Download", "$rootPath/Download", isDirectory = true, isPinned = true),
            FileItem("3", "notes.txt", "$rootPath/notes.txt", isDirectory = false, size = 1024L, extension = "txt"),
            FileItem("4", "image.png", "$rootPath/image.png", isDirectory = false, size = 2048L, extension = "png")
        )

        filesMap["$rootPath/Documents"] = mutableListOf(
            FileItem("10", "report.pdf", "$rootPath/Documents/report.pdf", isDirectory = false, size = 5000L, extension = "pdf"),
            FileItem("11", "data.csv", "$rootPath/Documents/data.csv", isDirectory = false, size = 3000L, extension = "csv")
        )
    }

    override suspend fun listFiles(path: String, treeUri: String?): List<FileItem> {
        val list = filesMap[path] ?: emptyList()
        val pinnedPaths = pinned.map { it.path }.toSet()
        return list.map { it.copy(isPinned = pinnedPaths.contains(it.path)) }
    }

    override suspend fun getStorageDrives(): List<DriveItem> = drives
    override suspend fun getQuickAccessItems(): List<QuickAccessItem> = pinned.toList()

    override suspend fun pinDirectory(path: String, name: String, treeUri: String?) {
        if (pinned.none { it.path == path }) {
            pinned.add(QuickAccessItem(path, name, path, QuickAccessType.PINNED, isRemovable = true))
        }
    }

    override suspend fun unpinDirectory(path: String) {
        pinned.removeAll { it.path == path }
    }

    override suspend fun isPinned(path: String): Boolean = pinned.any { it.path == path }

    override suspend fun reorderQuickAccessItem(path: String, moveUp: Boolean) {
        val index = pinned.indexOfFirst { it.path == path }
        if (index == -1) return
        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex in pinned.indices) {
            val item = pinned.removeAt(index)
            pinned.add(targetIndex, item)
        }
    }

    override suspend fun createDirectory(parentPath: String, name: String, treeUri: String?): Boolean {
        val list = filesMap.getOrPut(parentPath) { mutableListOf() }
        list.add(FileItem(name, name, "$parentPath/$name", isDirectory = true))
        return true
    }

    override suspend fun createTextFile(parentPath: String, name: String, content: String, treeUri: String?): Boolean {
        val list = filesMap.getOrPut(parentPath) { mutableListOf() }
        list.add(FileItem(name, name, "$parentPath/$name", isDirectory = false, size = content.length.toLong(), extension = name.substringAfterLast('.', "")))
        return true
    }

    override suspend fun rename(path: String, newName: String, treeUri: String?): Boolean = true

    override suspend fun delete(path: String, treeUri: String?): Boolean {
        filesMap.values.forEach { list -> list.removeAll { it.path == path } }
        return true
    }

    override suspend fun copyItem(
        sourcePath: String,
        destDirPath: String,
        sourceTreeUri: String?,
        destTreeUri: String?,
        onProgress: ((Long, Long, String) -> Unit)?,
        isCancelled: (() -> Boolean)?
    ): Boolean {
        val name = sourcePath.substringAfterLast('/')
        val destList = filesMap.getOrPut(destDirPath) { mutableListOf() }
        destList.add(FileItem(name, name, "$destDirPath/$name", isDirectory = false))
        onProgress?.invoke(100L, 100L, name)
        return true
    }

    override suspend fun moveItem(
        sourcePath: String,
        destDirPath: String,
        sourceTreeUri: String?,
        destTreeUri: String?,
        onProgress: ((Long, Long, String) -> Unit)?,
        isCancelled: (() -> Boolean)?
    ): Boolean {
        copyItem(sourcePath, destDirPath, sourceTreeUri, destTreeUri, onProgress, isCancelled)
        delete(sourcePath, sourceTreeUri)
        return true
    }

    override suspend fun calculateItemSize(path: String, treeUri: String?): Long = 1024L

    override suspend fun createShortcut(targetItem: FileItem, destDirPath: String, destTreeUri: String?): Boolean {
        val name = "${targetItem.name} - Shortcut.lnk"
        val list = filesMap.getOrPut(destDirPath) { mutableListOf() }
        list.add(
            FileItem(
                id = "$destDirPath/$name",
                name = name,
                path = "$destDirPath/$name",
                isDirectory = targetItem.isDirectory,
                isShortcut = true,
                shortcutTargetPath = targetItem.path,
                shortcutTargetIsDirectory = targetItem.isDirectory
            )
        )
        return true
    }

    override suspend fun addCloudDrive(name: String, treeUri: String) {
        cloudDrives.add(DriveItem(treeUri, name, treeUri, type = DriveType.CLOUD, treeUri = treeUri))
    }

    override suspend fun removeCloudDrive(treeUri: String) {
        cloudDrives.removeAll { it.treeUri == treeUri }
    }

    override suspend fun getCloudDrives(): List<DriveItem> = cloudDrives

    val volumeTreeUris = mutableMapOf<String, String>()
    override suspend fun getPersistedTreeUriForPath(path: String): String? = volumeTreeUris[path]
    override suspend fun saveVolumeTreeUri(pathOrUuid: String, treeUri: String) {
        volumeTreeUris[pathOrUuid] = treeUri
    }

    override suspend fun exists(path: String, treeUri: String?): Boolean {
        val clean = path.trimEnd('/')
        if (clean.isEmpty() || clean == "/storage/emulated/10" || clean == "/storage/emulated/0" || clean == "/mnt/shared") return true
        if (treeUri != null) return true
        if (drives.any { it.path.trimEnd('/') == clean }) return true
        if (pinned.any { it.path.trimEnd('/') == clean }) return true
        if (filesMap.containsKey(clean)) return true
        if (filesMap.values.flatten().any { it.path.trimEnd('/') == clean }) return true
        return false
    }

    var darkModePref: Boolean? = null
    override fun getDarkModePreference(): Boolean? = darkModePref
    override fun setDarkModePreference(isDark: Boolean) {
        darkModePref = isDark
    }

    private var videoThumbnailsEnabled = true
    override fun isVideoThumbnailsEnabled(): Boolean = videoThumbnailsEnabled
    override fun setVideoThumbnailsEnabled(enabled: Boolean) {
        videoThumbnailsEnabled = enabled
    }

    private var currentViewMode = ViewMode.GRID
    override fun getViewMode(): ViewMode = currentViewMode
    override fun setViewMode(mode: ViewMode) {
        currentViewMode = mode
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FileExplorerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeFileRepository
    private lateinit var viewModel: FileExplorerViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        SharedClipboardManager.clearClipboard()
        repository = FakeFileRepository()
        viewModel = FileExplorerViewModel(repository)
    }

    @After
    fun tearDown() {
        SharedClipboardManager.clearClipboard()
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialization_loadsPrimaryDriveAndFiles() = runTest(testDispatcher) {
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("/storage/emulated/10", state.currentPath)
        assertEquals(4, state.items.size)
        assertEquals(2, state.drives.size)
        assertTrue(state.quickAccessItems.isNotEmpty())
        assertEquals(1, state.tabs.size)
    }

    @Test
    fun testNavigation_andBreadcrumbs() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.navigateTo("/storage/emulated/10/Documents")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("/storage/emulated/10/Documents", state.currentPath)
        assertEquals(2, state.items.size)
        assertTrue(state.breadcrumbs.any { it.title == "Documents" })

        // Back navigation
        viewModel.navigateBack()
        advanceUntilIdle()
        assertEquals("/storage/emulated/10", viewModel.uiState.value.currentPath)

        // Forward navigation
        viewModel.navigateForward()
        advanceUntilIdle()
        assertEquals("/storage/emulated/10/Documents", viewModel.uiState.value.currentPath)
    }

    @Test
    fun testSearchAndFilter() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.setSearchQuery("notes")
        advanceUntilIdle()

        val filtered = viewModel.uiState.value.filteredItems
        assertEquals(1, filtered.size)
        assertEquals("notes.txt", filtered[0].name)
    }

    @Test
    fun testSorting_foldersFirstAndAlphabetical() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.setSortOption(SortOption(SortField.NAME, SortDirection.ASCENDING))
        advanceUntilIdle()

        val items = viewModel.uiState.value.filteredItems
        assertTrue("Folders must be first", items[0].isDirectory && items[1].isDirectory)
        assertEquals("Documents", items[0].name)
        assertEquals("Download", items[1].name)
    }

    @Test
    fun testPinAndUnpin() = runTest(testDispatcher) {
        advanceUntilIdle()

        assertFalse(viewModel.isPathPinned("/storage/emulated/10/CustomFolder"))
        viewModel.pinDirectory("/storage/emulated/10/CustomFolder", "CustomFolder")
        advanceUntilIdle()

        assertTrue(viewModel.isPathPinned("/storage/emulated/10/CustomFolder"))

        viewModel.unpinDirectory("/storage/emulated/10/CustomFolder")
        advanceUntilIdle()

        assertFalse(viewModel.isPathPinned("/storage/emulated/10/CustomFolder"))
    }

    @Test
    fun testPinSelectedDirectories() = runTest(testDispatcher) {
        advanceUntilIdle()

        val docPath = "/storage/emulated/10/Documents"
        viewModel.toggleSelect(docPath)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasSelectedDirectories)

        viewModel.unpinDirectory(docPath)
        advanceUntilIdle()
        assertFalse(viewModel.isPathPinned(docPath))

        viewModel.togglePinSelected()
        advanceUntilIdle()
        assertTrue(viewModel.isPathPinned(docPath))
    }

    @Test
    fun testSelection() = runTest(testDispatcher) {
        advanceUntilIdle()

        val path = "/storage/emulated/10/notes.txt"
        viewModel.toggleSelect(path)
        assertTrue(viewModel.uiState.value.selectedItemPaths.contains(path))

        viewModel.selectAll()
        assertEquals(4, viewModel.uiState.value.selectedItemPaths.size)

        viewModel.clearSelection()
        assertTrue(viewModel.uiState.value.selectedItemPaths.isEmpty())
    }

    @Test
    fun testTabManagement_openCloseSwitch() = runTest(testDispatcher) {
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.tabs.size)
        val tab1Id = viewModel.uiState.value.tabs[0].id

        // Open new tab
        viewModel.openNewTab("/storage/emulated/10/Documents")
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.tabs.size)
        val tab2Id = viewModel.uiState.value.activeTabId
        assertEquals("/storage/emulated/10/Documents", viewModel.uiState.value.currentPath)

        // Switch back to tab 1
        viewModel.switchTab(tab1Id)
        advanceUntilIdle()
        assertEquals(tab1Id, viewModel.uiState.value.activeTabId)
        assertEquals("/storage/emulated/10", viewModel.uiState.value.currentPath)

        // Close tab 2
        viewModel.closeTab(tab2Id)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.tabs.size)
        assertEquals(tab1Id, viewModel.uiState.value.tabs[0].id)
    }

    @Test
    fun testClipboard_copyAndCutAndPaste() = runTest(testDispatcher) {
        advanceUntilIdle()

        val noteFile = viewModel.uiState.value.items.first { it.name == "notes.txt" }
        viewModel.copyItems(listOf(noteFile))
        assertTrue(viewModel.uiState.value.canPaste)
        assertEquals(ClipboardOperation.COPY, viewModel.uiState.value.clipboardState?.operation)

        // Paste into Documents
        viewModel.paste("/storage/emulated/10/Documents")
        advanceUntilIdle()

        val destFiles = repository.listFiles("/storage/emulated/10/Documents")
        assertTrue(destFiles.any { it.name == "notes.txt" })

        // Cut items
        val imgFile = viewModel.uiState.value.items.first { it.name == "image.png" }
        viewModel.cutItems(listOf(imgFile))
        assertEquals(ClipboardOperation.CUT, viewModel.uiState.value.clipboardState?.operation)

        viewModel.paste("/storage/emulated/10/Documents")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canPaste) // Clipboard cleared after CUT paste
    }

    @Test
    fun testQuickAccessReordering() = runTest(testDispatcher) {
        advanceUntilIdle()

        val initialItems = viewModel.uiState.value.quickAccessItems
        assertTrue(initialItems.size >= 2)
        val firstPath = initialItems[0].path
        val secondPath = initialItems[1].path

        // Move second item up
        viewModel.moveQuickAccessUp(secondPath)
        advanceUntilIdle()

        val reordered = viewModel.uiState.value.quickAccessItems
        assertEquals(secondPath, reordered[0].path)
        assertEquals(firstPath, reordered[1].path)

        // Move first item down
        viewModel.moveQuickAccessDown(secondPath)
        advanceUntilIdle()

        val reorderedBack = viewModel.uiState.value.quickAccessItems
        assertEquals(firstPath, reorderedBack[0].path)
        assertEquals(secondPath, reorderedBack[1].path)
    }

    @Test
    fun testIntentPathHandling() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.handleIntentPath("/storage/emulated/10/Documents", null)
        advanceUntilIdle()

        assertEquals("/storage/emulated/10/Documents", viewModel.uiState.value.currentPath)
    }

    @Test
    fun testCreateShortcut_inCurrentDirectory() = runTest(testDispatcher) {
        advanceUntilIdle()

        val docItem = viewModel.uiState.value.items.first { it.name == "Documents" }
        viewModel.createShortcut(docItem)
        advanceUntilIdle()

        val items = repository.listFiles("/storage/emulated/10")
        val shortcut = items.firstOrNull { it.name == "Documents - Shortcut.lnk" }
        assertNotNull(shortcut)
        assertTrue(shortcut!!.isShortcut)
        assertEquals("/storage/emulated/10/Documents", shortcut.shortcutTargetPath)
    }

    @Test
    fun testSelectPaths_forMarqueeDragSelection() = runTest(testDispatcher) {
        advanceUntilIdle()

        val paths = setOf("/storage/emulated/10/notes.txt", "/storage/emulated/10/image.png")
        viewModel.selectPaths(paths)
        assertEquals(paths, viewModel.uiState.value.selectedItemPaths)
    }

    @Test
    fun testExternalVolumeRequiresAccessWhenNoTreeUri() = runTest(testDispatcher) {
        advanceUntilIdle()

        val extPath = "/storage/3C8F-12E3"
        repository.drives.add(DriveItem("usb1", "External Drive (D:)", extPath, 4_000_000_000_000L, 2_000_000_000_000L, DriveType.USB, treeUri = null))

        viewModel.navigateTo(extPath, null)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.needsVolumeAccess)
        assertEquals(extPath, viewModel.uiState.value.volumeAccessPath)

        val sampleTreeUri = "content://com.android.externalstorage.documents/tree/3C8F-12E3%3A"
        repository.filesMap[extPath] = mutableListOf(
            FileItem("doc1", "Backup2026", extPath, isDirectory = true),
            FileItem("doc2", "photo.jpg", extPath, isDirectory = false, size = 1048576L)
        )

        viewModel.attachVolumeUri(extPath, sampleTreeUri)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.needsVolumeAccess)
        assertEquals(sampleTreeUri, viewModel.uiState.value.currentTreeUri)
        assertEquals(2, viewModel.uiState.value.items.size)
    }

    @Test
    fun testNavigateUp_fromSubfolder() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.navigateTo("/storage/emulated/10/Documents")
        advanceUntilIdle()
        assertEquals("/storage/emulated/10/Documents", viewModel.uiState.value.currentPath)

        viewModel.navigateUp()
        advanceUntilIdle()
        assertEquals("/storage/emulated/10", viewModel.uiState.value.currentPath)
    }

    @Test
    fun testMoveItemsToFolder_singleAndMultipleFiles() = runTest(testDispatcher) {
        advanceUntilIdle()

        val notes = viewModel.uiState.value.items.first { it.name == "notes.txt" }
        val image = viewModel.uiState.value.items.first { it.name == "image.png" }
        val docFolder = viewModel.uiState.value.items.first { it.name == "Documents" }

        viewModel.moveItemsToFolder(listOf(notes, image), docFolder)
        advanceUntilIdle()

        // Verify items were moved to Documents
        val rootItems = repository.listFiles("/storage/emulated/10")
        assertFalse(rootItems.any { it.name == "notes.txt" })
        assertFalse(rootItems.any { it.name == "image.png" })

        val docItems = repository.listFiles("/storage/emulated/10/Documents")
        assertTrue(docItems.any { it.name == "notes.txt" })
        assertTrue(docItems.any { it.name == "image.png" })
    }

    @Test
    fun testMoveItemsToFolder_preventsMovingFolderIntoItself() = runTest(testDispatcher) {
        advanceUntilIdle()

        val docFolder = viewModel.uiState.value.items.first { it.name == "Documents" }
        viewModel.moveItemsToFolder(listOf(docFolder), docFolder)
        advanceUntilIdle()

        val rootItems = repository.listFiles("/storage/emulated/10")
        assertTrue(rootItems.any { it.name == "Documents" })
    }

    @Test
    fun testOnPermissionResult_doesNotResetCurrentDirectoryWhenAlreadyInitialized() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.navigateTo("/storage/emulated/10/Documents")
        advanceUntilIdle()
        assertEquals("/storage/emulated/10/Documents", viewModel.uiState.value.currentPath)

        // Simulate returning to the app from opening an external file (onResume permission check)
        viewModel.onPermissionResult(true)
        advanceUntilIdle()

        // Verify current path is preserved and NOT reset back to root
        assertEquals("/storage/emulated/10/Documents", viewModel.uiState.value.currentPath)
    }

    @Test
    fun testCrossWindow_cutInWindow1_pasteInWindow2_syncsBothWindows() = runTest(testDispatcher) {
        advanceUntilIdle()

        // Create window 2
        val viewModel2 = FileExplorerViewModel(repository)
        advanceUntilIdle()

        // Window 1 is in /storage/emulated/10
        assertEquals("/storage/emulated/10", viewModel.uiState.value.currentPath)

        // Window 2 navigates to /storage/emulated/10/Documents
        viewModel2.navigateTo("/storage/emulated/10/Documents")
        advanceUntilIdle()
        assertEquals("/storage/emulated/10/Documents", viewModel2.uiState.value.currentPath)

        // Window 1 cuts notes.txt
        val noteFile = viewModel.uiState.value.items.first { it.name == "notes.txt" }
        viewModel.cutItems(listOf(noteFile))
        advanceUntilIdle()

        // Verify shared clipboard is active in BOTH windows
        assertTrue(viewModel.uiState.value.canPaste)
        assertTrue(viewModel2.uiState.value.canPaste)
        assertEquals(ClipboardOperation.CUT, viewModel2.uiState.value.clipboardState?.operation)

        // Window 2 pastes into its current directory (/storage/emulated/10/Documents)
        viewModel2.paste()
        advanceUntilIdle()

        // Verify clipboard cleared in both windows
        assertFalse(viewModel2.uiState.value.canPaste)
        assertFalse(viewModel.uiState.value.canPaste)

        // Verify file was moved from Window 1 to Window 2
        assertFalse(viewModel.uiState.value.items.any { it.name == "notes.txt" })
        assertTrue(viewModel2.uiState.value.items.any { it.name == "notes.txt" })

        // Verify both windows remained in their own directories
        assertEquals("/storage/emulated/10", viewModel.uiState.value.currentPath)
        assertEquals("/storage/emulated/10/Documents", viewModel2.uiState.value.currentPath)
    }

    @Test
    fun testCrossWindow_createFileInWindow2_refreshesWindow1() = runTest(testDispatcher) {
        advanceUntilIdle()

        // Window 2 is in same directory as Window 1 (/storage/emulated/10)
        val viewModel2 = FileExplorerViewModel(repository)
        advanceUntilIdle()

        assertEquals("/storage/emulated/10", viewModel.uiState.value.currentPath)
        assertEquals("/storage/emulated/10", viewModel2.uiState.value.currentPath)
        assertEquals(4, viewModel.uiState.value.items.size)

        // Window 2 creates a new file
        viewModel2.createTextFile("newfile.txt", "hello")
        advanceUntilIdle()

        // Window 1 should automatically refresh and show newfile.txt
        assertTrue(viewModel.uiState.value.items.any { it.name == "newfile.txt" })
    }

    @Test
    fun testNavigateToPathInput_fullPath() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.navigateToPathInput("/storage/emulated/10/Documents")
        advanceUntilIdle()

        assertEquals("/storage/emulated/10/Documents", viewModel.uiState.value.currentPath)
    }

    @Test
    fun testNavigateToPathInput_windowsDriveLetter() = runTest(testDispatcher) {
        advanceUntilIdle()

        // Windows style backslashes and drive letter C:\Documents
        viewModel.navigateToPathInput("C:\\Documents")
        advanceUntilIdle()

        assertEquals("/storage/emulated/10/Documents", viewModel.uiState.value.currentPath)

        // C: drive root
        viewModel.navigateToPathInput("C:")
        advanceUntilIdle()
        assertEquals("/storage/emulated/10", viewModel.uiState.value.currentPath)
    }

    @Test
    fun testNavigateToPathInput_folderName() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.navigateToPathInput("Documents")
        advanceUntilIdle()

        assertEquals("/storage/emulated/10/Documents", viewModel.uiState.value.currentPath)
    }

    @Test
    fun testNavigateToPathInput_thisPC() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.navigateTo("/storage/emulated/10/Documents")
        advanceUntilIdle()
        assertEquals("/storage/emulated/10/Documents", viewModel.uiState.value.currentPath)

        viewModel.navigateToPathInput("This PC")
        advanceUntilIdle()

        assertEquals("/storage/emulated/10", viewModel.uiState.value.currentPath)
    }

    @Test
    fun testCrossWindow_dragAndDrop_moveItemsToDirectory() = runTest(testDispatcher) {
        advanceUntilIdle()

        // Window 1 is in /storage/emulated/10
        val notesItem = viewModel.uiState.value.items.first { it.name == "notes.txt" }

        // Window 2 is in /storage/emulated/10/Documents
        val viewModel2 = FileExplorerViewModel(repository)
        advanceUntilIdle()
        viewModel2.navigateTo("/storage/emulated/10/Documents")
        advanceUntilIdle()

        // User drags notes.txt from Window 1 to Window 2's directory
        viewModel2.moveItemsToDirectory(
            items = listOf(notesItem),
            targetDirPath = "/storage/emulated/10/Documents",
            targetDirName = "Documents"
        )
        advanceUntilIdle()

        // Verify file is now in Window 2 and removed from Window 1
        assertFalse(viewModel.uiState.value.items.any { it.name == "notes.txt" })
        assertTrue(viewModel2.uiState.value.items.any { it.name == "notes.txt" })
        assertEquals("/storage/emulated/10", viewModel.uiState.value.currentPath)
        assertEquals("/storage/emulated/10/Documents", viewModel2.uiState.value.currentPath)
    }

    @Test
    fun testCrossWindow_dragAndDrop_multipleItems() = runTest(testDispatcher) {
        advanceUntilIdle()

        // Window 1 is in /storage/emulated/10
        val notesItem = viewModel.uiState.value.items.first { it.name == "notes.txt" }
        val imageItem = viewModel.uiState.value.items.first { it.name == "image.png" }

        // Window 2 is in /storage/emulated/10/Documents
        val viewModel2 = FileExplorerViewModel(repository)
        advanceUntilIdle()
        viewModel2.navigateTo("/storage/emulated/10/Documents")
        advanceUntilIdle()

        // User drags both files to Window 2
        viewModel2.moveItemsToDirectory(
            items = listOf(notesItem, imageItem),
            targetDirPath = "/storage/emulated/10/Documents",
            targetDirName = "Documents"
        )
        advanceUntilIdle()

        // Verify both files moved
        assertFalse(viewModel.uiState.value.items.any { it.name == "notes.txt" })
        assertFalse(viewModel.uiState.value.items.any { it.name == "image.png" })
        assertTrue(viewModel2.uiState.value.items.any { it.name == "notes.txt" })
        assertTrue(viewModel2.uiState.value.items.any { it.name == "image.png" })
    }

    @Test
    fun testDragAndDrop_sameDirectory_noOp() = runTest(testDispatcher) {
        advanceUntilIdle()

        val notesItem = viewModel.uiState.value.items.first { it.name == "notes.txt" }
        val countBefore = viewModel.uiState.value.items.size

        // Dropping into same directory where it already lives
        viewModel.moveItemsToDirectory(
            items = listOf(notesItem),
            targetDirPath = "/storage/emulated/10",
            targetDirName = "Internal Storage"
        )
        advanceUntilIdle()

        assertEquals(countBefore, viewModel.uiState.value.items.size)
        assertTrue(viewModel.uiState.value.items.any { it.name == "notes.txt" })
    }

    @Test
    fun testNavigateToPathInput_nonExistentPath_showsStatus() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.navigateToPathInput("/storage/emulated/10/DoesNotExistXYZ")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.statusText.contains("does not exist"))
    }

    @Test
    fun testNavigateToExternalDrive_withoutPermission_requestsVolumeAccess() = runTest(testDispatcher) {
        val usbPath = "/storage/C874-FBC6"
        val usbDrive = DriveItem("usb1", "USB Drive (D:)", usbPath, 32_000_000_000L, 16_000_000_000L, DriveType.USB)
        repository.drives.add(usbDrive)
        repository.filesMap[usbPath] = mutableListOf(
            FileItem("usb_file_1", "video.mp4", "$usbPath/video.mp4", isDirectory = false, size = 50_000_000L)
        )

        viewModel.navigateTo(usbPath)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(usbPath, state.currentPath)
        assertTrue(state.needsVolumeAccess)
        assertEquals("USB Drive (D:)", state.volumeAccessName)
        assertEquals(usbPath, state.volumeAccessPath)
    }

    @Test
    fun testPasteToExternalDrive_withoutWritePermission_requestsVolumeAccess() = runTest(testDispatcher) {
        val usbPath = "/storage/C874-FBC6"
        val usbDrive = DriveItem("usb1", "USB Drive (D:)", usbPath, 32_000_000_000L, 16_000_000_000L, DriveType.USB)
        repository.drives.add(usbDrive)
        repository.filesMap[usbPath] = mutableListOf()

        // 1. Copy item from internal storage
        advanceUntilIdle()
        val notes = viewModel.uiState.value.items.first { it.name == "notes.txt" }
        viewModel.copyItems(listOf(notes))
        advanceUntilIdle()

        // 2. Navigate to external USB drive
        viewModel.navigateTo(usbPath)
        advanceUntilIdle()

        // 3. Attempt paste without granted tree URI
        viewModel.paste()
        advanceUntilIdle()

        // 4. Should not fail silently; must request volume access
        val state = viewModel.uiState.value
        assertTrue(state.needsVolumeAccess)
        assertEquals("USB Drive (D:)", state.volumeAccessName)
        assertTrue(state.statusText.contains("Write permission required"))
    }

    @Test
    fun testPasteToExternalDrive_withTreeUri_transfersSuccessfully() = runTest(testDispatcher) {
        val usbPath = "/storage/C874-FBC6"
        val treeUri = "content://com.android.externalstorage.documents/tree/C874-FBC6%3A"
        val usbDrive = DriveItem("usb1", "USB Drive (D:)", usbPath, 32_000_000_000L, 16_000_000_000L, DriveType.USB, treeUri = treeUri)
        repository.drives.add(usbDrive)
        repository.filesMap[usbPath] = mutableListOf()
        repository.saveVolumeTreeUri(usbPath, treeUri)

        // 1. Copy item
        advanceUntilIdle()
        val notes = viewModel.uiState.value.items.first { it.name == "notes.txt" }
        viewModel.copyItems(listOf(notes))
        advanceUntilIdle()

        // 2. Navigate to USB with treeUri
        viewModel.navigateTo(usbPath, treeUri)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isReadOnlyVolume)

        // 3. Paste
        viewModel.paste()
        advanceUntilIdle()

        // 4. File should be copied to destination
        val destFiles = repository.filesMap[usbPath]
        assertNotNull(destFiles)
        assertTrue(destFiles!!.any { it.name == "notes.txt" })
    }

    @Test
    fun testNavigateToSmartphoneDrive_withoutPermission_requestsVolumeAccess() = runTest(testDispatcher) {
        val phonePath = "mtp://1/Internal storage"
        val phoneDrive = DriveItem("phone1", "Google Pixel 8", phonePath, 128_000_000_000L, 64_000_000_000L, DriveType.SMARTPHONE)
        repository.drives.add(phoneDrive)

        viewModel.navigateTo(phonePath)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(phonePath, state.currentPath)
        assertTrue(state.needsVolumeAccess)
        assertEquals("Google Pixel 8", state.volumeAccessName)
        assertEquals(phonePath, state.volumeAccessPath)
    }

    @Test
    fun testAttachVolumeUri_forSmartphone_immediatelyNavigatesAndUpdatesDriveWithoutRestart() = runTest(testDispatcher) {
        val phonePath = "mtp://1/Internal storage"
        val phoneDrive = DriveItem("phone1", "Google Pixel 8", phonePath, 128_000_000_000L, 64_000_000_000L, DriveType.SMARTPHONE)
        repository.drives.add(phoneDrive)

        viewModel.navigateTo(phonePath)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.needsVolumeAccess)

        val grantedMtpUri = "content://com.android.mtp.documents/tree/1"
        repository.filesMap[grantedMtpUri] = mutableListOf(
            FileItem("dcim", "DCIM", "$grantedMtpUri/DCIM", isDirectory = true),
            FileItem("doc", "Download", "$grantedMtpUri/Download", isDirectory = true)
        )

        // Grant access via SAF
        viewModel.attachVolumeUri(phonePath, grantedMtpUri)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.needsVolumeAccess)
        assertEquals(grantedMtpUri, state.currentPath)
        assertEquals(grantedMtpUri, state.currentTreeUri)
        assertEquals(2, state.items.size)
    }

    @Test
    fun testToggleVideoThumbnails_togglesStateAndPersists() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showVideoThumbnails)
        assertTrue(repository.isVideoThumbnailsEnabled())

        viewModel.toggleVideoThumbnails()
        assertFalse(viewModel.uiState.value.showVideoThumbnails)
        assertFalse(repository.isVideoThumbnailsEnabled())

        viewModel.toggleVideoThumbnails()
        assertTrue(viewModel.uiState.value.showVideoThumbnails)
        assertTrue(repository.isVideoThumbnailsEnabled())
    }

    @Test
    fun testSetShowVideoThumbnails_setsExplicitState() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.setShowVideoThumbnails(false)
        assertFalse(viewModel.uiState.value.showVideoThumbnails)
        assertFalse(repository.isVideoThumbnailsEnabled())

        viewModel.setShowVideoThumbnails(true)
        assertTrue(viewModel.uiState.value.showVideoThumbnails)
        assertTrue(repository.isVideoThumbnailsEnabled())
    }

    @Test
    fun testSetViewMode_extraLarge_updatesStateAndPersists() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertEquals(ViewMode.GRID, viewModel.uiState.value.viewMode)

        viewModel.setViewMode(ViewMode.EXTRA_LARGE)
        assertEquals(ViewMode.EXTRA_LARGE, viewModel.uiState.value.viewMode)
        assertEquals(ViewMode.EXTRA_LARGE, repository.getViewMode())

        viewModel.setViewMode(ViewMode.DETAILS)
        assertEquals(ViewMode.DETAILS, viewModel.uiState.value.viewMode)
        assertEquals(ViewMode.DETAILS, repository.getViewMode())

        viewModel.setViewMode(ViewMode.GRID)
        assertEquals(ViewMode.GRID, viewModel.uiState.value.viewMode)
        assertEquals(ViewMode.GRID, repository.getViewMode())
    }

    @Test
    fun testRefreshDrives_updatesDrivesInUiState() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.drives.size)

        // Simulate connecting a USB drive
        val usbDrive = DriveItem("usb1", "USB Drive (D:)", "/storage/ABCD-1234", 16_000_000_000L, 8_000_000_000L, DriveType.USB)
        repository.drives.add(usbDrive)

        viewModel.refreshDrives()
        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.drives.size)
        assertTrue(viewModel.uiState.value.drives.any { it.id == "usb1" })

        // Simulate disconnecting the USB drive
        repository.drives.remove(usbDrive)
        viewModel.refreshDrives()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.drives.size)
        assertFalse(viewModel.uiState.value.drives.any { it.id == "usb1" })
    }

    @Test
    fun testRefreshDrives_whenExternalDriveDisconnected_navigatesBackToInternal() = runTest(testDispatcher) {
        advanceUntilIdle()
        val usbDrive = DriveItem("usb1", "USB Drive (D:)", "/storage/ABCD-1234", 16_000_000_000L, 8_000_000_000L, DriveType.USB, treeUri = "content://usb")
        repository.drives.add(usbDrive)
        repository.filesMap["/storage/ABCD-1234"] = mutableListOf(
            FileItem("doc", "notes.txt", "/storage/ABCD-1234/notes.txt", isDirectory = false)
        )

        // Navigate to the USB drive
        viewModel.navigateTo("/storage/ABCD-1234", "content://usb")
        advanceUntilIdle()
        assertEquals("/storage/ABCD-1234", viewModel.uiState.value.currentPath)

        // Disconnect USB drive
        repository.drives.remove(usbDrive)
        viewModel.refreshDrives()
        advanceUntilIdle()

        // Should automatically fall back to internal storage
        assertEquals("/storage/emulated/10", viewModel.uiState.value.currentPath)
    }

    @Test
    fun testDeviceMonitoring_detectsConnectedAndDisconnectedDrives() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.startDeviceMonitoring()

        val usbDrive = DriveItem("usb2", "Flash Drive (E:)", "/storage/5678-EFGH", 32_000_000_000L, 16_000_000_000L, DriveType.USB)
        repository.drives.add(usbDrive)

        // Advance coroutine delay (1500ms)
        testScheduler.advanceTimeBy(1600)
        testScheduler.runCurrent()

        assertTrue(viewModel.uiState.value.drives.any { it.id == "usb2" })

        repository.drives.remove(usbDrive)
        testScheduler.advanceTimeBy(1600)
        testScheduler.runCurrent()

        assertFalse(viewModel.uiState.value.drives.any { it.id == "usb2" })
        viewModel.stopDeviceMonitoring()
    }

    @Test
    fun testDarkMode_toggleUpdatesStateAndRepository() = runTest(testDispatcher) {
        // Initial state when no pref stored
        assertEquals(null, viewModel.uiState.value.isDarkMode)

        // Toggle when system is dark (true) -> should switch to light (false)
        viewModel.toggleDarkMode(systemInDarkTheme = true)
        assertEquals(false, viewModel.uiState.value.isDarkMode)
        assertEquals(false, repository.darkModePref)

        // Toggle again -> should switch to dark (true)
        viewModel.toggleDarkMode(systemInDarkTheme = true)
        assertEquals(true, viewModel.uiState.value.isDarkMode)
        assertEquals(true, repository.darkModePref)
    }

    @Test
    fun testDarkMode_initialPreferenceLoadedFromRepository() = runTest(testDispatcher) {
        repository.darkModePref = true
        val newVm = FileExplorerViewModel(repository)
        assertEquals(true, newVm.uiState.value.isDarkMode)
    }
}

