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
    val shortcutTargetIsDirectory: Boolean = false,
    val shortcutTargetExtension: String = "",
    val shortcutTargetFileType: String = "",
    val shortcutTargetSize: Long = -1L
) {
    val displayExtension: String
        get() = if (isShortcut) {
            if (shortcutTargetIsDirectory) "" else shortcutTargetExtension.ifEmpty {
                val tName = shortcutTargetPath?.substringAfterLast('/') ?: name.removeSuffix(".lnk").removeSuffix(" - Shortcut")
                if (tName.contains('.')) tName.substringAfterLast('.').lowercase() else ""
            }
        } else {
            extension
        }

    val displayFileType: String
        get() {
            if (isShortcut) {
                if (shortcutTargetIsDirectory) return "File folder"
                if (shortcutTargetFileType.isNotBlank() &&
                    !shortcutTargetFileType.contains("lnk", ignoreCase = true) &&
                    !shortcutTargetFileType.contains("shortcut", ignoreCase = true)
                ) {
                    return shortcutTargetFileType
                }
                return resolveFileTypeDescription(displayExtension, isDirectory = false)
            }
            return resolveFileTypeDescription(extension, isDirectory = isDirectory)
        }

    val effectiveCategory: FileCategory
        get() = when {
            isDirectory || (isShortcut && shortcutTargetIsDirectory) -> FileCategory.FOLDER
            displayExtension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg") -> FileCategory.IMAGE
            displayExtension in listOf("mp4", "mkv", "webm", "avi", "mov", "3gp") -> FileCategory.VIDEO
            displayExtension in listOf("mp3", "wav", "ogg", "m4a", "flac", "aac") -> FileCategory.AUDIO
            displayExtension in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md") -> FileCategory.DOCUMENT
            displayExtension in listOf("kt", "java", "py", "c", "cpp", "h", "json", "xml", "html", "css", "js", "ts", "sh", "gradle") -> FileCategory.CODE
            displayExtension in listOf("zip", "rar", "tar", "gz", "7z", "bz2") -> FileCategory.ARCHIVE
            displayExtension == "apk" -> FileCategory.APK
            else -> if (isShortcut) FileCategory.SHORTCUT else FileCategory.OTHER
        }

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
        get() = if (isDirectory || (isShortcut && shortcutTargetIsDirectory)) {
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
        fun resolveFileTypeDescription(extension: String, isDirectory: Boolean): String {
            if (isDirectory) return "File folder"
            val ext = extension.trim().trimStart('.').lowercase()
            return when (ext) {
                // Images
                "png" -> "PNG File"
                "jpg", "jpeg" -> "JPG File"
                "gif" -> "GIF File"
                "webp" -> "WEBP File"
                "bmp" -> "BMP File"
                "svg" -> "SVG File"
                "ico" -> "Icon File"
                "heic", "heif" -> "HEIC Image"
                "tif", "tiff" -> "TIFF Image"

                // Documents
                "pdf" -> "PDF Document"
                "txt" -> "Text Document"
                "doc", "docx" -> "Word Document"
                "xls", "xlsx" -> "Excel Spreadsheet"
                "ppt", "pptx" -> "PowerPoint Presentation"
                "csv" -> "CSV Document"
                "rtf" -> "Rich Text Document"
                "md" -> "Markdown Document"
                "epub", "mobi" -> "E-Book"

                // Video
                "mp4" -> "MP4 Video"
                "mkv" -> "MKV Video"
                "avi" -> "AVI Video"
                "mov" -> "MOV Video"
                "webm" -> "WEBM Video"
                "3gp" -> "3GP Video"
                "flv", "wmv", "m4v" -> "Video File"

                // Audio
                "mp3" -> "MP3 Audio"
                "wav" -> "WAV Audio"
                "flac" -> "FLAC Audio"
                "m4a", "aac" -> "AAC Audio"
                "ogg", "opus" -> "OGG Audio"
                "mid", "midi" -> "MIDI File"

                // Code / Developer
                "kt", "kts" -> "Kotlin Source File"
                "java" -> "Java Source File"
                "py" -> "Python File"
                "c", "h" -> "C Source File"
                "cpp", "hpp", "cc", "cxx" -> "C++ Source File"
                "cs" -> "C# Source File"
                "js" -> "JavaScript File"
                "ts" -> "TypeScript File"
                "html", "htm" -> "HTML Document"
                "css" -> "CSS Stylesheet"
                "json" -> "JSON File"
                "xml" -> "XML File"
                "yaml", "yml" -> "YAML File"
                "sh", "bash" -> "Shell Script"
                "gradle" -> "Gradle Script"
                "sql" -> "SQL File"

                // Archives
                "zip" -> "ZIP Archive"
                "rar" -> "RAR Archive"
                "7z" -> "7Z Archive"
                "tar" -> "TAR Archive"
                "gz", "tgz" -> "GZ Archive"
                "bz2" -> "BZ2 Archive"
                "xz" -> "XZ Archive"

                // Executables / Installers
                "apk" -> "APK File"
                "exe" -> "Application"
                "dmg" -> "Mac Disk Image"
                "iso" -> "ISO Disk Image"
                "deb", "rpm" -> "Linux Package"

                // Default
                "" -> "File"
                else -> "${ext.uppercase()} File"
            }
        }
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
    EXTRA_LARGE,
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
    CLOUD,
    SMARTPHONE
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
