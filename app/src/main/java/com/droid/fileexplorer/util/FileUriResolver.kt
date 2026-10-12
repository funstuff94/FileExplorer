package com.droid.fileexplorer.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.droid.fileexplorer.model.FileItem
import java.io.File
import java.net.URLDecoder

/**
 * Utility for resolving cross-app sharable and draggable URIs on Android.
 * Properly maps secondary external storage (USB/SD Card / D: drive) to SAF Document URIs
 * so Android's drag-and-drop security framework (DRAG_FLAG_GLOBAL_URI_READ) grants
 * transient read permissions to recipient apps like MusicPlayer.
 */
object FileUriResolver {

    private const val PREFS_NAME = "file_explorer_prefs"
    private const val PREF_VOLUME_URI_PREFIX = "volume_tree_uri_"

    /**
     * Extracts the volume UUID from a given file path or content URI.
     * Returns null for internal/emulated storage paths.
     */
    fun extractVolumeUuid(pathOrUri: String): String? {
        val clean = pathOrUri.trimEnd('/')
        if (clean.isEmpty() || clean == "/storage" || clean == "/storage/self" ||
            clean.startsWith("/storage/emulated") || clean.startsWith("/mnt/shared")
        ) {
            return null
        }

        if (clean.startsWith("content://")) {
            if (clean.contains("com.android.externalstorage.documents")) {
                val decoded = try {
                    URLDecoder.decode(clean, "UTF-8")
                } catch (_: Exception) {
                    clean
                }
                val match = Regex("""(?:tree|document)/([A-Za-z0-9_-]+):""").find(decoded)
                val id = match?.groupValues?.getOrNull(1)
                return if (id != null && id != "primary") id else null
            }
            return null
        }

        if (clean.startsWith("/storage/")) {
            val after = clean.removePrefix("/storage/").trim('/')
            val seg = after.substringBefore('/')
            return if (seg == "emulated" || seg == "self" || seg == "10" || seg == "0" || seg == "Apps" || seg.isEmpty()) {
                null
            } else {
                seg
            }
        }

        if (clean.startsWith("/mnt/media_rw/")) {
            val after = clean.removePrefix("/mnt/media_rw/").trim('/')
            val seg = after.substringBefore('/')
            return if (seg.isEmpty()) null else seg
        }

        return null
    }

    /**
     * Checks if the path is located on secondary external storage (e.g. USB drive or SD card).
     */
    fun isExternalStoragePath(path: String): Boolean {
        val clean = path.trimEnd('/')
        return (!clean.startsWith("/storage/emulated") && !clean.startsWith("/mnt/shared") &&
                (clean.startsWith("/storage/") || clean.startsWith("/mnt/media_rw/")) &&
                extractVolumeUuid(clean) != null)
    }

    /**
     * Builds the relative path of a file within its storage volume.
     */
    fun buildRelativePath(uuid: String, path: String): String {
        val clean = path.trimEnd('/')
        return when {
            clean.startsWith("/storage/$uuid") -> clean.removePrefix("/storage/$uuid").trim('/')
            clean.startsWith("/mnt/media_rw/$uuid") -> clean.removePrefix("/mnt/media_rw/$uuid").trim('/')
            !clean.startsWith("/") && !clean.startsWith("content://") -> clean.trim('/')
            else -> ""
        }
    }

    /**
     * Builds the SAF document ID for a given volume UUID and file path.
     */
    fun buildDocumentId(uuid: String, path: String): String {
        val rel = buildRelativePath(uuid, path)
        return if (rel.isEmpty()) "$uuid:" else "$uuid:$rel"
    }

    /**
     * Retrieves the persisted SAF tree URI for an external volume from preferences or contentResolver.
     */
    fun getPersistedTreeUriForVolume(context: Context, uuid: String?, path: String): String? {
        val cleanPath = path.trimEnd('/')
        if (cleanPath.startsWith("/storage/emulated") || cleanPath.startsWith("/mnt/shared") ||
            cleanPath == "/storage" || cleanPath == "/storage/self"
        ) {
            return null
        }
        val extractedUuid = (if (!uuid.isNullOrEmpty()) uuid else extractVolumeUuid(cleanPath) ?: "").trim()
        if (extractedUuid.isEmpty() || extractedUuid == "emulated" || extractedUuid == "self" ||
            extractedUuid == "10" || extractedUuid == "0" || extractedUuid == "Apps"
        ) {
            return null
        }

        // 1. Check SharedPreferences by UUID or Path
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedByUuid = prefs.getString(PREF_VOLUME_URI_PREFIX + extractedUuid, null)
            if (!savedByUuid.isNullOrEmpty()) return savedByUuid

            val savedByPath = prefs.getString(PREF_VOLUME_URI_PREFIX + cleanPath, null)
            if (!savedByPath.isNullOrEmpty()) return savedByPath

            val rootPath = "/storage/$extractedUuid"
            val savedByRootPath = prefs.getString(PREF_VOLUME_URI_PREFIX + rootPath, null)
            if (!savedByRootPath.isNullOrEmpty()) return savedByRootPath
        } catch (_: Exception) {}

        // 2. Check Android ContentResolver's persistedUriPermissions strictly by UUID
        try {
            val perms = context.contentResolver.persistedUriPermissions
            for (perm in perms) {
                if (!perm.isReadPermission) continue
                val uriStr = perm.uri.toString()
                val decoded = Uri.decode(uriStr)
                if (uriStr.contains(extractedUuid, ignoreCase = true) || decoded.contains(extractedUuid, ignoreCase = true)) {
                    return uriStr
                }
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * Resolves the best possible Android Uri for a FileItem or path to share/drag across apps.
     * For external storage (D: drive), returns the SAF Document URI so recipient apps obtain read grants.
     * For internal storage (C: drive), returns the FileProvider content URI.
     */
    fun resolveFileUri(context: Context, item: FileItem, treeUri: String? = null): Uri? {
        return resolveFileUri(context, item.path, item.treeUri ?: treeUri, item.isDirectory)
    }

    fun resolveFileUri(
        context: Context,
        path: String,
        treeUri: String? = null,
        isDirectory: Boolean = false
    ): Uri? {
        // Case 1: Already a content URI
        if (path.startsWith("content://")) {
            return try { Uri.parse(path) } catch (_: Exception) { null }
        }

        // Case 2: treeUri is already a document URI
        if (treeUri != null && treeUri.contains("/document/")) {
            return try { Uri.parse(treeUri) } catch (_: Exception) { null }
        }

        val cleanPath = path.trimEnd('/')

        // Case 3: External storage (USB / SD card / D: drive)
        if (isExternalStoragePath(cleanPath)) {
            val uuid = extractVolumeUuid(cleanPath)
            if (uuid != null) {
                val effectiveTreeUri = treeUri?.takeIf { it.startsWith("content://") }
                    ?: getPersistedTreeUriForVolume(context, uuid, cleanPath)

                if (effectiveTreeUri != null) {
                    try {
                        val parsedTree = Uri.parse(effectiveTreeUri)
                        val docId = buildDocumentId(uuid, cleanPath)
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(parsedTree, docId)
                        if (docUri != null) {
                            return docUri
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        // Case 4: FileProvider for internal storage or fallback
        val file = File(cleanPath)
        try {
            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (_: Exception) {
            return try {
                Uri.fromFile(file)
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Resolves the MIME type for a file item or content URI.
     */
    fun getMimeType(context: Context, item: FileItem, uri: Uri? = null): String {
        if (item.mimeType.isNotBlank()) return item.mimeType
        if (uri != null && uri.scheme.equals("content", ignoreCase = true)) {
            try {
                val type = context.contentResolver.getType(uri)
                if (!type.isNullOrBlank()) return type
            } catch (_: Exception) {}
        }
        val ext = item.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        return mime ?: when (ext) {
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "m4a", "aac" -> "audio/mp4"
            "flac" -> "audio/flac"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "apk" -> "application/vnd.android.package-archive"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "kt", "java", "py", "sh", "json", "xml", "md", "txt", "gradle" -> "text/plain"
            else -> "*/*"
        }
    }
}
