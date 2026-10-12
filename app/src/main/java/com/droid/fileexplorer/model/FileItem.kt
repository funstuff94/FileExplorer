package com.droid.fileexplorer.model

enum class FileCategory {
    FOLDER,
    SHORTCUT,
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    CODE,
    ARCHIVE,
    APK,
    OTHER
}

data class FileItem(
    val id: String,
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val extension: String = "",
    val mimeType: String = "",
    val isPinned: Boolean = false,
    val treeUri: String? = null,
    val isCloud: Boolean = false,
    val childCount: Int = 0,
    val isShortcut: Boolean = false,
    val shortcutTargetPath: String? = null,
    val shortcutTargetTreeUri: String? = null,
    val shortcutTargetIsDirectory: Boolean = false
) {
    val category: FileCategory
        get() = when {
            isShortcut -> FileCategory.SHORTCUT
            isDirectory -> FileCategory.FOLDER
            extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg") -> FileCategory.IMAGE
            extension in listOf("mp4", "mkv", "webm", "avi", "mov", "3gp") -> FileCategory.VIDEO
            extension in listOf("mp3", "wav", "ogg", "m4a", "flac", "aac") -> FileCategory.AUDIO
            extension in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md") -> FileCategory.DOCUMENT
            extension in listOf("kt", "java", "py", "c", "cpp", "h", "json", "xml", "html", "css", "js", "ts", "sh", "gradle") -> FileCategory.CODE
            extension in listOf("zip", "rar", "tar", "gz", "7z", "bz2") -> FileCategory.ARCHIVE
            extension == "apk" -> FileCategory.APK
            else -> FileCategory.OTHER
        }

    val formattedSize: String
        get() = if (isDirectory) {
            val sizeStr = if (size > 0L) formatByteSize(size) else ""
            val countStr = if (childCount > 0) "$childCount items" else ""
            when {
                sizeStr.isNotEmpty() && countStr.isNotEmpty() -> "$sizeStr • $countStr"
                sizeStr.isNotEmpty() -> sizeStr
                countStr.isNotEmpty() -> countStr
                else -> "0 B"
            }
        } else {
            formatByteSize(size)
        }

    companion object {
        fun formatByteSize(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            if (bytes < 1024L) return "$bytes B"
            val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt().coerceIn(1, 6)
            val units = arrayOf("KB", "MB", "GB", "TB", "PB", "EB")
            val unit = units[exp - 1]
            val value = bytes / Math.pow(1024.0, exp.toDouble())
            return if (value >= 100.0) {
                String.format(java.util.Locale.US, "%.0f %s", value, unit)
            } else {
                String.format(java.util.Locale.US, "%.1f %s", value, unit)
            }
        }

        fun formatExactBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 bytes"
            val formatted = java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(bytes)
            return if (bytes == 1L) "$formatted byte" else "$formatted bytes"
        }
    }
}

data class TransferProgress(
    val operation: String = "Copying",
    val currentFileName: String = "",
    val currentItemIndex: Int = 1,
    val totalItems: Int = 1,
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Double = 0.0,
    val estimatedRemainingSeconds: Long = 0L,
    val sourcePath: String = "",
    val destPath: String = "",
    val isIndeterminate: Boolean = false
)

data class Breadcrumb(
    val title: String,
    val path: String,
    val treeUri: String? = null
)

enum class ViewMode {
    GRID,
    DETAILS
}

enum class SortField {
    NAME,
    DATE_MODIFIED,
    SIZE,
    TYPE
}

enum class SortDirection {
    ASCENDING,
    DESCENDING
}

data class SortOption(
    val field: SortField = SortField.NAME,
    val direction: SortDirection = SortDirection.ASCENDING
)

enum class DriveType {
    INTERNAL,
    LINUX_SHARED,
    SD_CARD,
    USB,
    CLOUD
}

data class DriveItem(
    val id: String,
    val name: String,
    val path: String,
    val totalBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val type: DriveType = DriveType.INTERNAL,
    val treeUri: String? = null
)

enum class QuickAccessType {
    PINNED,
    SYSTEM,
    DRIVE,
    CLOUD
}

data class QuickAccessItem(
    val id: String,
    val title: String,
    val path: String,
    val type: QuickAccessType = QuickAccessType.PINNED,
    val treeUri: String? = null,
    val isRemovable: Boolean = true
)

enum class ClipboardOperation {
    COPY,
    CUT
}

data class ClipboardState(
    val items: List<FileItem>,
    val operation: ClipboardOperation
)

data class ExplorerTab(
    val id: String,
    val title: String,
    val path: String,
    val treeUri: String? = null
)
