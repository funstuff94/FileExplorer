package com.droid.fileexplorer.data

import com.droid.fileexplorer.model.DriveItem
import com.droid.fileexplorer.model.FileItem
import com.droid.fileexplorer.model.QuickAccessItem

interface FileRepository {
    suspend fun listFiles(path: String, treeUri: String? = null): List<FileItem>
    suspend fun getStorageDrives(): List<DriveItem>
    suspend fun getQuickAccessItems(): List<QuickAccessItem>
    suspend fun pinDirectory(path: String, name: String, treeUri: String? = null)
    suspend fun unpinDirectory(path: String)
    suspend fun isPinned(path: String): Boolean
    suspend fun reorderQuickAccessItem(path: String, moveUp: Boolean)
    suspend fun createDirectory(parentPath: String, name: String, treeUri: String? = null): Boolean
    suspend fun createTextFile(parentPath: String, name: String, content: String = "", treeUri: String? = null): Boolean
    suspend fun rename(path: String, newName: String, treeUri: String? = null): Boolean
    suspend fun delete(path: String, treeUri: String? = null): Boolean
    suspend fun copyItem(
        sourcePath: String,
        destDirPath: String,
        sourceTreeUri: String? = null,
        destTreeUri: String? = null,
        onProgress: ((copiedBytes: Long, totalBytes: Long, currentFile: String) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ): Boolean
    suspend fun moveItem(
        sourcePath: String,
        destDirPath: String,
        sourceTreeUri: String? = null,
        destTreeUri: String? = null,
        onProgress: ((copiedBytes: Long, totalBytes: Long, currentFile: String) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ): Boolean
    suspend fun calculateItemSize(path: String, treeUri: String? = null): Long = 0L
    suspend fun createShortcut(targetItem: FileItem, destDirPath: String, destTreeUri: String? = null): Boolean
    suspend fun addCloudDrive(name: String, treeUri: String)
    suspend fun removeCloudDrive(treeUri: String)
    suspend fun getCloudDrives(): List<DriveItem>
    suspend fun getPersistedTreeUriForPath(path: String): String? = null
    suspend fun saveVolumeTreeUri(pathOrUuid: String, treeUri: String) {}
    suspend fun exists(path: String, treeUri: String? = null): Boolean = true
}
