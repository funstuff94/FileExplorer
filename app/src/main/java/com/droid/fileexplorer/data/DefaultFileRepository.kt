package com.droid.fileexplorer.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.droid.fileexplorer.model.DriveItem
import com.droid.fileexplorer.model.DriveType
import com.droid.fileexplorer.model.FileItem
import com.droid.fileexplorer.model.QuickAccessItem
import com.droid.fileexplorer.model.QuickAccessType
import com.droid.fileexplorer.model.ViewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.webkit.MimeTypeMap
import com.droid.fileexplorer.util.FileUriResolver

class DefaultFileRepository(
    private val context: Context
) : FileRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences("file_explorer_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PINNED = "pinned_items_json"
        private const val KEY_PINNED_INITIALIZED = "pinned_initialized_v2"
        private const val KEY_CLOUD_DRIVES = "cloud_drives_json"
        private const val PREF_VOLUME_URI_PREFIX = "volume_tree_uri_"
    }

    init {
        // Clean up any accidental mappings for internal storage paths
        prefs.edit()
            .remove(PREF_VOLUME_URI_PREFIX + "/storage/emulated/10")
            .remove(PREF_VOLUME_URI_PREFIX + "/storage/emulated/0")
            .remove(PREF_VOLUME_URI_PREFIX + "/storage/emulated")
            .remove(PREF_VOLUME_URI_PREFIX + "/storage")
            .remove(PREF_VOLUME_URI_PREFIX + "/storage/self")
            .remove(PREF_VOLUME_URI_PREFIX + "10")
            .remove(PREF_VOLUME_URI_PREFIX + "0")
            .remove(PREF_VOLUME_URI_PREFIX + "internal")
            .remove(PREF_VOLUME_URI_PREFIX + "emulated")
            .remove(PREF_VOLUME_URI_PREFIX + "Apps")
            .remove(PREF_VOLUME_URI_PREFIX + "/storage/emulated/10/Apps")
            .apply()
    }

    private fun extractVolumeUuid(cleanPath: String): String? {
        val path = cleanPath.trimEnd('/')
        if (path.isEmpty() || path == "/storage" || path == "/storage/self" ||
            path.startsWith("/storage/emulated") || path.startsWith("/mnt/shared")) {
            return null
        }
        if (path.startsWith("content://")) {
            if (path.contains("com.android.externalstorage.documents")) {
                val decoded = try { Uri.decode(path) } catch (_: Exception) { path }
                val match = Regex("""(?:tree|document)/([A-Za-z0-9_-]+):""").find(decoded)
                val id = match?.groupValues?.getOrNull(1)
                return if (id != null && id != "primary") id else null
            }
            return null
        }
        if (path.startsWith("/storage/")) {
            val after = path.removePrefix("/storage/").trim('/')
            val seg = after.substringBefore('/')
            return if (seg == "emulated" || seg == "self" || seg == "10" || seg == "0" || seg == "Apps") null else seg
        }
        if (path.startsWith("/mnt/media_rw/")) {
            val after = path.removePrefix("/mnt/media_rw/").trim('/')
            val seg = after.substringBefore('/')
            return if (seg.isEmpty()) null else seg
        }
        val lastSeg = path.substringAfterLast('/')
        return if (lastSeg == "emulated" || lastSeg == "self" || lastSeg == "10" || lastSeg == "0" || lastSeg == "Apps" || lastSeg.contains(':')) null else lastSeg
    }

    private fun isExternalStoragePath(path: String): Boolean {
        val clean = path.trimEnd('/')
        if (clean.isEmpty() || clean == "/storage" || clean.startsWith("/storage/emulated") || clean.startsWith("/storage/self") || clean.startsWith("/mnt/shared")) {
            return false
        }
        return clean.startsWith("/mnt/media_rw") ||
               clean.startsWith("/mnt/pass_through") ||
               (clean.startsWith("/mnt/") && !clean.startsWith("/mnt/sdcard")) ||
               clean.startsWith("/storage/")
    }

    override suspend fun getPersistedTreeUriForPath(path: String): String? = withContext(Dispatchers.IO) {
        val cleanPath = path.trimEnd('/')
        // Internal storage (C:) and Linux shared paths must NEVER use an external tree URI!
        if (cleanPath.startsWith("/storage/emulated") || cleanPath.startsWith("/mnt/shared") || cleanPath == "/storage" || cleanPath == "/storage/self") {
            return@withContext null
        }
        val uuid = extractVolumeUuid(cleanPath)
        getPersistedTreeUriForVolume(uuid, cleanPath)
    }

    override suspend fun saveVolumeTreeUri(pathOrUuid: String, treeUri: String) = withContext(Dispatchers.IO) {
        val clean = pathOrUuid.trimEnd('/')
        if (clean.startsWith("/storage/emulated") || clean.startsWith("/mnt/shared") || clean == "/storage" || clean == "/storage/self") {
            return@withContext
        }
        val uuidFromPath = extractVolumeUuid(clean)
        val uuidFromUri = extractVolumeUuid(treeUri)
        val uuid = (uuidFromPath ?: uuidFromUri ?: clean).trim()
        if (uuid.isEmpty() || uuid == "10" || uuid == "0" || uuid == "emulated" || uuid == "Apps") {
            return@withContext
        }
        val editor = prefs.edit()
            .putString(PREF_VOLUME_URI_PREFIX + uuid, treeUri)
            .putString(PREF_VOLUME_URI_PREFIX + "/storage/$uuid", treeUri)
            .putString(PREF_VOLUME_URI_PREFIX + "/mnt/media_rw/$uuid", treeUri)
        if (clean.isNotEmpty() && clean != uuid) {
            editor.putString(PREF_VOLUME_URI_PREFIX + clean, treeUri)
        }
        if (treeUri.contains("com.android.mtp.documents") || clean.startsWith("mtp")) {
            editor.putString(PREF_VOLUME_URI_PREFIX + "mtp", treeUri)
        }
        editor.apply()
    }

    fun getPersistedTreeUriForVolume(uuid: String?, path: String): String? {
        val cleanPath = path.trimEnd('/')
        // Safety guard: NEVER match internal storage or emulated paths
        if (cleanPath.startsWith("/storage/emulated") || cleanPath.startsWith("/mnt/shared") || cleanPath == "/storage" || cleanPath == "/storage/self") {
            return null
        }
        val extractedUuid = (if (!uuid.isNullOrEmpty()) uuid else extractVolumeUuid(cleanPath) ?: "").trim()
        val isMtp = extractedUuid.startsWith("mtp") || cleanPath.startsWith("mtp") || cleanPath.contains("com.android.mtp.documents")

        // 1. Check SharedPreferences by UUID, Path, or MTP key
        if (extractedUuid.isNotEmpty() && extractedUuid != "emulated" && extractedUuid != "self") {
            val savedByUuid = prefs.getString(PREF_VOLUME_URI_PREFIX + extractedUuid, null)
            if (!savedByUuid.isNullOrEmpty()) return savedByUuid
        }

        val savedByPath = prefs.getString(PREF_VOLUME_URI_PREFIX + cleanPath, null)
        if (!savedByPath.isNullOrEmpty()) return savedByPath

        if (extractedUuid.isNotEmpty()) {
            val rootPath = "/storage/$extractedUuid"
            val savedByRootPath = prefs.getString(PREF_VOLUME_URI_PREFIX + rootPath, null)
            if (!savedByRootPath.isNullOrEmpty()) return savedByRootPath
        }

        if (isMtp) {
            val savedMtp = prefs.getString(PREF_VOLUME_URI_PREFIX + "mtp", null)
            if (!savedMtp.isNullOrEmpty()) return savedMtp
        }

        // 2. Check Android ContentResolver's persistedUriPermissions
        try {
            val perms = context.contentResolver.persistedUriPermissions
            for (perm in perms) {
                if (!perm.isReadPermission) continue
                val uriStr = perm.uri.toString()
                if (isMtp && uriStr.contains("com.android.mtp.documents")) {
                    return uriStr
                }
                val decoded = Uri.decode(uriStr)
                if (extractedUuid.isNotEmpty() && (uriStr.contains(extractedUuid, ignoreCase = true) || decoded.contains(extractedUuid, ignoreCase = true))) {
                    return uriStr
                }
            }
        } catch (_: Exception) {}

        return null
    }

    override suspend fun listFiles(path: String, treeUri: String?): List<FileItem> = withContext(Dispatchers.IO) {
        if (path.startsWith("content://")) {
            return@withContext listDocumentFiles(path)
        }

        val isExternal = isExternalStoragePath(path)
        val effectiveTreeUri = treeUri ?: if (isExternal) getPersistedTreeUriForPath(path) else null

        // 1. Try direct filesystem listing first
        val directory = File(path)
        val directFiles = if (directory.exists() && directory.isDirectory) {
            try { directory.listFiles() } catch (_: Exception) { null }
        } else null

        if (directFiles != null) {
            val pinnedSet = getPinnedPaths().map { it.trimEnd('/') }.toSet()
            return@withContext directFiles.map { file ->
                val isDir = file.isDirectory
                val childCount = if (isDir) file.listFiles()?.size ?: 0 else 0
                val dirSize = if (isDir) 0L else file.length()
                val cleanPath = file.absolutePath.trimEnd('/')

                var isShortcut = false
                var shortcutTargetPath: String? = null
                var shortcutTargetTreeUri: String? = null
                var shortcutTargetIsDir = false
                if (file.name.endsWith(".lnk", ignoreCase = true) && file.length() < 10000) {
                    try {
                        val content = file.readText()
                        val json = JSONObject(content)
                        if (json.optString("type") == "shortcut") {
                            isShortcut = true
                            shortcutTargetPath = json.optString("targetPath")
                            shortcutTargetTreeUri = json.optString("targetTreeUri").ifEmpty { null }
                            shortcutTargetIsDir = json.optBoolean("isDirectory", false)
                        }
                    } catch (_: Exception) {}
                }

                FileItem(
                    id = file.absolutePath,
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = isDir,
                    size = dirSize,
                    lastModified = file.lastModified(),
                    extension = if (isDir) "" else file.extension.lowercase(),
                    treeUri = if (isExternal) effectiveTreeUri else null,
                    isPinned = isDir && pinnedSet.contains(cleanPath),
                    childCount = childCount,
                    isShortcut = isShortcut,
                    shortcutTargetPath = shortcutTargetPath,
                    shortcutTargetTreeUri = shortcutTargetTreeUri,
                    shortcutTargetIsDirectory = shortcutTargetIsDir
                )
            }
        }

        // 2. If direct filesystem listing is blocked or not accessible (e.g. secondary external volume on Android 11+),
        // resolve via SAF DocumentFile for this path
        if (effectiveTreeUri != null) {
            val targetDoc = getDocumentFileForTarget(path, effectiveTreeUri)
            if (targetDoc != null && targetDoc.exists()) {
                val children = try { targetDoc.listFiles() } catch (_: Exception) { emptyArray() }
                val pinnedSet = getPinnedPaths().map { it.trimEnd('/') }.toSet()
                return@withContext children.mapNotNull { doc ->
                    val name = doc.name ?: return@mapNotNull null
                    val isDir = doc.isDirectory
                    val ext = if (isDir) "" else name.substringAfterLast('.', "").lowercase()
                    val cleanPath = "${path.trimEnd('/')}/$name"
                    val docChildren = if (isDir) try { doc.listFiles() } catch (_: Exception) { null } else null
                    val childCount = docChildren?.size ?: 0
                    val docSize = if (isDir) 0L else doc.length()

                    var isShortcut = false
                    var shortcutTargetPath: String? = null
                    var shortcutTargetTreeUri: String? = null
                    var shortcutTargetIsDir = false
                    if (name.endsWith(".lnk", ignoreCase = true) && doc.length() < 10000) {
                        try {
                            context.contentResolver.openInputStream(doc.uri)?.use { stream ->
                                val content = stream.bufferedReader().readText()
                                val json = JSONObject(content)
                                if (json.optString("type") == "shortcut") {
                                    isShortcut = true
                                    shortcutTargetPath = json.optString("targetPath")
                                    shortcutTargetTreeUri = json.optString("targetTreeUri").ifEmpty { null }
                                    shortcutTargetIsDir = json.optBoolean("isDirectory", false)
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    FileItem(
                        id = cleanPath,
                        name = name,
                        path = cleanPath,
                        isDirectory = isDir,
                        size = docSize,
                        lastModified = doc.lastModified(),
                        extension = ext,
                        mimeType = doc.type ?: "",
                        treeUri = effectiveTreeUri,
                        isCloud = false,
                        isPinned = isDir && pinnedSet.contains(cleanPath),
                        childCount = childCount,
                        isShortcut = isShortcut,
                        shortcutTargetPath = shortcutTargetPath,
                        shortcutTargetTreeUri = shortcutTargetTreeUri,
                        shortcutTargetIsDirectory = shortcutTargetIsDir
                    )
                }
            }
        }

        emptyList()
    }

    private fun listDocumentFiles(treeUriString: String): List<FileItem> {
        val uri = Uri.parse(treeUriString)
        val docFile = try {
            if (treeUriString.contains("/document/")) {
                val docId = DocumentsContract.getDocumentId(uri)
                val treeId = DocumentsContract.getTreeDocumentId(uri)
                val rootTreeUri = DocumentsContract.buildTreeDocumentUri(uri.authority, treeId)
                val docTreeUri = DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, docId)
                DocumentFile.fromTreeUri(context, docTreeUri) ?: DocumentFile.fromSingleUri(context, uri)
            } else {
                DocumentFile.fromTreeUri(context, uri) ?: DocumentFile.fromSingleUri(context, uri)
            }
        } catch (_: Exception) {
            try { DocumentFile.fromSingleUri(context, uri) } catch (_: Exception) { null }
        } ?: return emptyList()

        val children = docFile.listFiles()
        val pinnedSet = getPinnedPaths()
        val isExternalVolume = treeUriString.contains("com.android.externalstorage.documents")
        val isMtp = treeUriString.contains("com.android.mtp.documents") || treeUriString.startsWith("mtp://")
        val isCloud = !isExternalVolume && !isMtp
        val uuid = if (isExternalVolume) {
            try {
                val decoded = Uri.decode(treeUriString)
                val match = Regex("""(?:tree|document)/([A-Za-z0-9_-]+):""").find(decoded)
                val id = match?.groupValues?.getOrNull(1)
                if (id != null && id != "primary") id else null
            } catch (_: Exception) { null }
        } else null

        return children.mapNotNull { doc ->
            val name = doc.name ?: return@mapNotNull null
            val isDir = doc.isDirectory
            val ext = if (isDir) "" else name.substringAfterLast('.', "").lowercase()
            val uriStr = doc.uri.toString()
            val cleanPath = if (uuid != null) {
                val docUriStr = doc.uri.toString()
                val docDecoded = try { Uri.decode(docUriStr) } catch (_: Exception) { docUriStr }
                val rel = docDecoded.substringAfter("$uuid:", "").trim('/')
                if (rel.isNotEmpty()) "/storage/$uuid/$rel" else "/storage/$uuid/$name"
            } else {
                uriStr
            }
            val docChildren = if (isDir) try { doc.listFiles() } catch (_: Exception) { null } else null
            val childCount = docChildren?.size ?: 0
            val docSize = if (isDir) 0L else doc.length()

            var isShortcut = false
            var shortcutTargetPath: String? = null
            var shortcutTargetTreeUri: String? = null
            var shortcutTargetIsDir = false
            if (name.endsWith(".lnk", ignoreCase = true) && doc.length() < 10000) {
                try {
                    context.contentResolver.openInputStream(doc.uri)?.use { stream ->
                        val content = stream.bufferedReader().readText()
                        val json = JSONObject(content)
                        if (json.optString("type") == "shortcut") {
                            isShortcut = true
                            shortcutTargetPath = json.optString("targetPath")
                            shortcutTargetTreeUri = json.optString("targetTreeUri").ifEmpty { null }
                            shortcutTargetIsDir = json.optBoolean("isDirectory", false)
                        }
                    }
                } catch (_: Exception) {}
            }

            FileItem(
                id = cleanPath,
                name = name,
                path = cleanPath,
                isDirectory = isDir,
                size = docSize,
                lastModified = doc.lastModified(),
                extension = ext,
                mimeType = doc.type ?: "",
                treeUri = if (isExternalVolume || isMtp) treeUriString else uriStr,
                isCloud = isCloud,
                isPinned = isDir && (pinnedSet.contains(cleanPath) || pinnedSet.contains(uriStr)),
                childCount = childCount,
                isShortcut = isShortcut,
                shortcutTargetPath = shortcutTargetPath,
                shortcutTargetTreeUri = shortcutTargetTreeUri,
                shortcutTargetIsDirectory = shortcutTargetIsDir
            )
        }
    }

    override suspend fun getStorageDrives(): List<DriveItem> = withContext(Dispatchers.IO) {
        val drives = mutableListOf<DriveItem>()
        val addedPaths = mutableSetOf<String>()

        // 1. Current user storage (e.g. /storage/emulated/10 or /storage/emulated/0)
        val user10Dir = File("/storage/emulated/10")
        val primaryDir = if (user10Dir.exists() && user10Dir.canRead()) {
            user10Dir
        } else {
            Environment.getExternalStorageDirectory()
        }

        if (primaryDir.exists()) {
            val (total, free) = getSpaceInfo(primaryDir.absolutePath)
            addedPaths.add(primaryDir.absolutePath)
            drives.add(
                DriveItem(
                    id = "internal",
                    name = "Internal Storage (C:)",
                    path = primaryDir.absolutePath,
                    totalBytes = total,
                    freeBytes = free,
                    type = DriveType.INTERNAL
                )
            )
        }

        // 2. Linux VM shared workspace /storage/emulated/10/Apps
        val linuxAppsDir = File("/storage/emulated/10/Apps")
        if (linuxAppsDir.exists() && linuxAppsDir.canRead()) {
            val (total, free) = getSpaceInfo(linuxAppsDir.absolutePath)
            addedPaths.add(linuxAppsDir.absolutePath)
            drives.add(
                DriveItem(
                    id = "linux_apps",
                    name = "Linux Apps & Projects",
                    path = linuxAppsDir.absolutePath,
                    totalBytes = total,
                    freeBytes = free,
                    type = DriveType.LINUX_SHARED
                )
            )
        }

        var driveLetterCode = 'D'.code

        // 3. System StorageManager volumes (USB drives, Portable SSDs, SD Cards)
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        if (storageManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                for (volume in storageManager.storageVolumes) {
                    if (volume.isPrimary) continue
                    val state = volume.state
                    if (state != Environment.MEDIA_MOUNTED && state != Environment.MEDIA_MOUNTED_READ_ONLY) continue

                    val desc = volume.getDescription(context) ?: "External Drive"
                    val volumeDir: File? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        volume.directory
                    } else {
                        try {
                            volume.javaClass.getMethod("getPathFile").invoke(volume) as? File
                        } catch (_: Exception) {
                            try {
                                val p = volume.javaClass.getMethod("getPath").invoke(volume) as? String
                                p?.let { File(it) }
                            } catch (_: Exception) { null }
                        }
                    }

                    val rawPath = volumeDir?.absolutePath
                        ?: (if (volume.uuid != null) "/storage/${volume.uuid}" else continue)
                    val path = if (volume.uuid != null) {
                        "/storage/${volume.uuid}"
                    } else if (rawPath.startsWith("/mnt/media_rw/")) {
                        "/storage/" + rawPath.substringAfter("/mnt/media_rw/")
                    } else {
                        rawPath
                    }
                    val dirFile = File(path)
                    val rawDirFile = File(rawPath)
                    if (!dirFile.exists() && !rawDirFile.exists()) continue

                    val (total1, free1) = getSpaceInfo(path)
                    val (total2, free2) = getSpaceInfo(rawPath)
                    val total = if (total1 > 0L) total1 else total2
                    val free = if (total1 > 0L) free1 else free2
                    if (total <= 0L && !dirFile.canRead() && !rawDirFile.canRead()) continue

                    addedPaths.add(path)
                    if (rawPath != path) addedPaths.add(rawPath)

                    val isUsb = desc.contains("usb", ignoreCase = true) ||
                        desc.contains("drive", ignoreCase = true) ||
                        desc.contains("ssd", ignoreCase = true) ||
                        desc.contains("flash", ignoreCase = true) ||
                        path.contains("usb", ignoreCase = true)
                    val letter = (driveLetterCode++).toChar()
                    val treeUri = getPersistedTreeUriForVolume(volume.uuid, path)

                    drives.add(
                        DriveItem(
                            id = "vol_${volume.uuid ?: path.hashCode().toString()}",
                            name = "$desc ($letter:)",
                            path = path,
                            totalBytes = total,
                            freeBytes = free,
                            type = if (isUsb) DriveType.USB else DriveType.SD_CARD,
                            treeUri = treeUri
                        )
                    )
                }
            } catch (_: Exception) {}
        }

        // 4. Secondary external storage directories (e.g. USB or SD card via getExternalFilesDirs)
        try {
            val extDirs = context.getExternalFilesDirs(null)
            for (extDir in extDirs) {
                if (extDir == null) continue
                val fullPath = extDir.absolutePath
                if (fullPath.startsWith("/storage/") && !fullPath.startsWith("/storage/emulated/")) {
                    val parts = fullPath.split("/")
                    if (parts.size >= 3) {
                        val rootVolumePath = "/storage/${parts[2]}"
                        val rootDir = File(rootVolumePath)
                        if (rootDir.exists() && rootDir.canRead() && !addedPaths.contains(rootVolumePath)) {
                            val (total, free) = getSpaceInfo(rootVolumePath)
                            if (total > 0L) {
                                addedPaths.add(rootVolumePath)
                                val letter = (driveLetterCode++).toChar()
                                val treeUri = getPersistedTreeUriForVolume(parts[2], rootVolumePath)
                                drives.add(
                                    DriveItem(
                                        id = "ext_${parts[2]}",
                                        name = "USB Drive ($letter:)",
                                        path = rootVolumePath,
                                        totalBytes = total,
                                        freeBytes = free,
                                        type = DriveType.USB,
                                        treeUri = treeUri
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 5. Removable Volumes from /storage directly
        val storageRoot = File("/storage")
        if (storageRoot.exists() && storageRoot.isDirectory) {
            val subDirs = storageRoot.listFiles() ?: emptyArray()
            for (sub in subDirs) {
                if (sub.name != "emulated" && sub.name != "self" && sub.name != "knox" && !addedPaths.contains(sub.absolutePath)) {
                    if (!sub.isDirectory || !sub.canRead()) continue
                    val (total, free) = getSpaceInfo(sub.absolutePath)
                    // MUST have actual non-zero capacity to be considered a mounted filesystem
                    if (total <= 0L) continue

                    addedPaths.add(sub.absolutePath)
                    val isUsb = sub.name.contains("usb", ignoreCase = true)
                    val letter = (driveLetterCode++).toChar()
                    val treeUri = getPersistedTreeUriForVolume(sub.name, sub.absolutePath)
                    drives.add(
                        DriveItem(
                            id = "sd_${sub.name}",
                            name = if (isUsb) "USB Drive (${sub.name}) ($letter:)" else "External Drive (${sub.name}) ($letter:)",
                            path = sub.absolutePath,
                            totalBytes = total,
                            freeBytes = free,
                            type = if (isUsb) DriveType.USB else DriveType.SD_CARD,
                            treeUri = treeUri
                        )
                    )
                }
            }
        }

        // 6. Connected Android Smartphones (via USB Host and MTP DocumentsProvider)
        detectConnectedSmartphones(drives, addedPaths, driveLetterCode.toChar())

        drives
    }

    private fun detectConnectedSmartphones(
        drives: MutableList<DriveItem>,
        addedPaths: MutableSet<String>,
        startDriveLetter: Char
    ): Char {
        // 1. Scan UsbManager first for physically connected smartphones (Google Pixel, Samsung Galaxy, etc.)
        val connectedPhones = mutableListOf<Triple<String, String, Int>>() // phonePath, prettyName, deviceId
        try {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            if (usbManager != null) {
                val knownPhoneVendors = setOf(
                    0x18d1, // Google
                    0x04e8, // Samsung
                    0x2717, // Xiaomi
                    0x22d9, // OnePlus / Oppo
                    0x12d1, // Huawei
                    0x22b8, // Motorola
                    0x0fce, // Sony
                    0x1004, // LG
                    0x0bb4, // HTC
                    0x2d95, // Vivo
                    0x0b05, // Asus
                    0x17ef  // Lenovo
                )
                val phoneKeywords = listOf("phone", "pixel", "galaxy", "android", "redmi", "mi ", "xiaomi", "oneplus", "xperia", "moto", "huawei", "honor", "oppo", "vivo", "realme")

                for ((_, device) in usbManager.deviceList) {
                    val prod = (device.productName ?: "").lowercase()
                    val manu = (device.manufacturerName ?: "").lowercase()
                    var isMtpInterface = false
                    for (i in 0 until device.interfaceCount) {
                        val intf = device.getInterface(i)
                        if ((intf.interfaceClass == UsbConstants.USB_CLASS_STILL_IMAGE && intf.interfaceSubclass == 1) ||
                            (intf.interfaceClass == 255 && intf.interfaceSubclass == 255 && intf.interfaceProtocol == 0)
                        ) {
                            isMtpInterface = true
                            break
                        }
                    }

                    val isPhone = isMtpInterface ||
                        knownPhoneVendors.contains(device.vendorId) ||
                        phoneKeywords.any { prod.contains(it) || manu.contains(it) }

                    if (isPhone) {
                        val prettyName = when {
                            !device.productName.isNullOrBlank() -> device.productName!!
                            !device.manufacturerName.isNullOrBlank() -> "${device.manufacturerName} Smartphone"
                            else -> "Android Smartphone"
                        }
                        connectedPhones.add(Triple("mtp://${device.deviceId}", prettyName, device.deviceId))
                    }
                }
            }
        } catch (_: Exception) {}

        // CRITICAL GUARD: If no smartphone is physically connected to USB, do NOT add any phantom smartphone!
        if (connectedPhones.isEmpty()) {
            return startDriveLetter
        }

        var currentLetter = startDriveLetter
        val primaryPhoneName = connectedPhones.firstOrNull()?.second

        // 2. Check Android ContentResolver's persistedUriPermissions for MTP smartphones
        try {
            val perms = context.contentResolver.persistedUriPermissions
            for (perm in perms) {
                if (!perm.isReadPermission) continue
                val uriStr = perm.uri.toString()
                if (uriStr.contains("com.android.mtp.documents")) {
                    val rootDoc = try { DocumentFile.fromTreeUri(context, perm.uri) } catch (_: Exception) { null }
                    val isAccessible = try { rootDoc != null && rootDoc.exists() && rootDoc.canRead() } catch (_: Exception) { false }
                    if (!isAccessible) continue

                    val rawTitle = rootDoc?.name?.takeIf { it.isNotBlank() } ?: "Android Smartphone"
                    val phoneTitle = if (primaryPhoneName != null && (rawTitle == "Android Smartphone" || rawTitle.contains("Internal", ignoreCase = true) || rawTitle.contains("storage", ignoreCase = true))) {
                        "$primaryPhoneName ($rawTitle)"
                    } else if (primaryPhoneName != null && !rawTitle.contains(primaryPhoneName, ignoreCase = true)) {
                        "$primaryPhoneName - $rawTitle"
                    } else {
                        rawTitle
                    }

                    if (!addedPaths.contains(uriStr)) {
                        addedPaths.add(uriStr)
                        val letter = currentLetter++
                        drives.add(
                            DriveItem(
                                id = "mtp_perm_${uriStr.hashCode()}",
                                name = "$phoneTitle ($letter:)",
                                path = uriStr,
                                totalBytes = 0L,
                                freeBytes = 0L,
                                type = DriveType.SMARTPHONE,
                                treeUri = uriStr
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. Query com.android.mtp.documents roots if accessible
        try {
            val rootsUri = DocumentsContract.buildRootsUri("com.android.mtp.documents")
            context.contentResolver.query(rootsUri, null, null, null, null)?.use { cursor ->
                val titleCol = cursor.getColumnIndex(DocumentsContract.Root.COLUMN_TITLE)
                val docIdCol = cursor.getColumnIndex(DocumentsContract.Root.COLUMN_DOCUMENT_ID)
                val availCol = cursor.getColumnIndex(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES)
                val capCol = cursor.getColumnIndex(DocumentsContract.Root.COLUMN_CAPACITY_BYTES)
                while (cursor.moveToNext()) {
                    val rawTitle = if (titleCol != -1) cursor.getString(titleCol) else "Android Smartphone"
                    val title = if (primaryPhoneName != null && !rawTitle.contains(primaryPhoneName, ignoreCase = true)) {
                        "$primaryPhoneName - $rawTitle"
                    } else rawTitle
                    val docId = if (docIdCol != -1) cursor.getString(docIdCol) else null
                    val avail = if (availCol != -1) cursor.getLong(availCol) else 0L
                    val cap = if (capCol != -1) cursor.getLong(capCol) else 0L
                    val treeUri = if (docId != null) {
                        DocumentsContract.buildTreeDocumentUri("com.android.mtp.documents", docId).toString()
                    } else null
                    val path = treeUri ?: "mtp://phone"

                    val existing = drives.firstOrNull { it.type == DriveType.SMARTPHONE && (it.path == path || (treeUri != null && it.treeUri == treeUri)) }
                    if (existing != null) {
                        if (cap > 0L) {
                            val idx = drives.indexOf(existing)
                            drives[idx] = existing.copy(totalBytes = cap, freeBytes = avail)
                        }
                    } else if (!addedPaths.contains(path)) {
                        addedPaths.add(path)
                        val letter = currentLetter++
                        drives.add(
                            DriveItem(
                                id = "mtp_root_${docId ?: title}",
                                name = "$title ($letter:)",
                                path = path,
                                totalBytes = cap,
                                freeBytes = avail,
                                type = DriveType.SMARTPHONE,
                                treeUri = treeUri
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        // 4. If no MTP drive with granted permissions exists yet, add connected phone from UsbManager
        for ((phonePath, prettyName, deviceId) in connectedPhones) {
            val alreadyAdded = drives.any { it.type == DriveType.SMARTPHONE }
            if (!alreadyAdded && !addedPaths.contains(phonePath)) {
                val savedTreeUri = getPersistedTreeUriForVolume("phone_$deviceId", phonePath)
                    ?: getPersistedTreeUriForVolume(null, "mtp")
                val letter = currentLetter++
                addedPaths.add(phonePath)
                drives.add(
                    DriveItem(
                        id = "phone_$deviceId",
                        name = "$prettyName ($letter:)",
                        path = savedTreeUri ?: phonePath,
                        totalBytes = 0L,
                        freeBytes = 0L,
                        type = DriveType.SMARTPHONE,
                        treeUri = savedTreeUri
                    )
                )
            }
        }

        return currentLetter
    }

    override suspend fun getQuickAccessItems(): List<QuickAccessItem> = withContext(Dispatchers.IO) {
        val list = loadPinnedJsonList()

        // Seed with default directories on first run
        if (!prefs.contains(KEY_PINNED_INITIALIZED) && list.isEmpty()) {
            val userStorage = if (File("/storage/emulated/10").exists()) "/storage/emulated/10" else "/storage/emulated/0"
            val systemFolders = listOf(
                "Downloads" to "$userStorage/Download",
                "Documents" to "$userStorage/Documents",
                "Pictures" to "$userStorage/Pictures",
                "Music" to "$userStorage/Music",
                "Videos" to "$userStorage/Movies",
                "Camera (DCIM)" to "$userStorage/DCIM",
                "Linux Projects" to "$userStorage/Apps"
            )

            for ((title, path) in systemFolders) {
                val dir = File(path)
                if (dir.exists()) {
                    list.add(JSONObject().apply {
                        put("path", path)
                        put("name", title)
                        put("treeUri", "")
                    })
                }
            }
            savePinnedJsonList(list)
            prefs.edit().putBoolean(KEY_PINNED_INITIALIZED, true).apply()
        }

        list.map { obj ->
            val p = obj.getString("path")
            val name = obj.getString("name")
            val uri = obj.optString("treeUri").ifEmpty { null }
            QuickAccessItem(
                id = p,
                title = name,
                path = p,
                type = if (uri != null) QuickAccessType.CLOUD else QuickAccessType.PINNED,
                treeUri = uri,
                isRemovable = true // User can unpin any directory!
            )
        }
    }

    override suspend fun pinDirectory(path: String, name: String, treeUri: String?) = withContext(Dispatchers.IO) {
        val cleanPath = path.trimEnd('/')
        val list = loadPinnedJsonList()
        val displayName = if (name.isNotBlank()) name else (File(cleanPath).name.ifEmpty { cleanPath })

        // Check if already in list
        val existingIndex = list.indexOfFirst { it.optString("path").trimEnd('/') == cleanPath || (treeUri != null && it.optString("treeUri") == treeUri) }
        if (existingIndex < 0) {
            val obj = JSONObject().apply {
                put("path", cleanPath)
                put("name", displayName)
                put("treeUri", treeUri ?: "")
            }
            list.add(obj)
            savePinnedJsonList(list)
        }
    }

    override suspend fun unpinDirectory(path: String) = withContext(Dispatchers.IO) {
        val cleanPath = path.trimEnd('/')
        val list = loadPinnedJsonList()
        val filtered = list.filter {
            val p = it.optString("path").trimEnd('/')
            val uri = it.optString("treeUri")
            p != cleanPath && uri != path
        }
        savePinnedJsonList(filtered)
    }

    override suspend fun isPinned(path: String): Boolean = withContext(Dispatchers.IO) {
        val cleanPath = path.trimEnd('/')
        getPinnedPaths().any { it.trimEnd('/') == cleanPath || it == path }
    }

    override suspend fun createDirectory(parentPath: String, name: String, treeUri: String?): Boolean = withContext(Dispatchers.IO) {
        val effectiveTreeUri = treeUri ?: if (parentPath.startsWith("/storage/") && !parentPath.startsWith("/storage/emulated")) {
            getPersistedTreeUriForPath(parentPath)
        } else null

        if (effectiveTreeUri != null || parentPath.startsWith("content://")) {
            val parentDoc = getDocumentFileForTarget(parentPath, effectiveTreeUri, createIfMissing = true)
            if (parentDoc != null) {
                return@withContext parentDoc.createDirectory(name) != null
            }
        }
        val target = File(parentPath, name)
        target.mkdirs()
    }

    override suspend fun createTextFile(parentPath: String, name: String, content: String, treeUri: String?): Boolean = withContext(Dispatchers.IO) {
        val effectiveTreeUri = treeUri ?: if (parentPath.startsWith("/storage/") && !parentPath.startsWith("/storage/emulated")) {
            getPersistedTreeUriForPath(parentPath)
        } else null

        if (effectiveTreeUri != null || parentPath.startsWith("content://")) {
            val parentDoc = getDocumentFileForTarget(parentPath, effectiveTreeUri, createIfMissing = true)
            if (parentDoc != null) {
                val fileDoc = parentDoc.createFile("text/plain", name) ?: return@withContext false
                return@withContext try {
                    context.contentResolver.openOutputStream(fileDoc.uri)?.use { out ->
                        out.write(content.toByteArray())
                    }
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }
        try {
            val target = File(parentPath, name)
            target.writeText(content)
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun rename(path: String, newName: String, treeUri: String?): Boolean = withContext(Dispatchers.IO) {
        val effectiveTreeUri = treeUri ?: if (isExternalStoragePath(path)) {
            getPersistedTreeUriForPath(path)
        } else null

        if (effectiveTreeUri != null || path.startsWith("content://")) {
            val doc = if (path.startsWith("content://")) {
                DocumentFile.fromSingleUri(context, Uri.parse(path))
            } else if (effectiveTreeUri != null) {
                val uuid = extractVolumeUuid(path)
                if (uuid != null) {
                    try {
                        val docId = FileUriResolver.buildDocumentId(uuid, path)
                        val treeUriObj = Uri.parse(effectiveTreeUri)
                        val treeId = DocumentsContract.getTreeDocumentId(treeUriObj)
                        val rootTreeUri = DocumentsContract.buildTreeDocumentUri(treeUriObj.authority, treeId)
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, docId)
                        val directDoc = DocumentFile.fromSingleUri(context, docUri) ?: DocumentFile.fromTreeUri(context, docUri)
                        if (directDoc != null && directDoc.exists()) {
                            return@withContext directDoc.renameTo(newName)
                        }
                    } catch (_: Exception) {}
                }
                val parentPath = path.substringBeforeLast('/')
                val fileName = path.substringAfterLast('/')
                val parentDoc = getDocumentFileForTarget(parentPath, effectiveTreeUri)
                parentDoc?.findFile(fileName)
            } else null
            if (doc != null) {
                return@withContext doc.renameTo(newName)
            }
        }
        val file = File(path)
        val target = File(file.parentFile, newName)
        file.renameTo(target)
    }

    override suspend fun reorderQuickAccessItem(path: String, moveUp: Boolean) = withContext(Dispatchers.IO) {
        val cleanPath = path.trimEnd('/')
        val list = loadPinnedJsonList()
        val index = list.indexOfFirst {
            it.optString("path").trimEnd('/') == cleanPath || it.optString("treeUri") == path
        }
        if (index < 0) return@withContext

        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex in 0 until list.size) {
            val item = list.removeAt(index)
            list.add(targetIndex, item)
            savePinnedJsonList(list)
        }
    }

    private fun calculateFolderSize(dir: File, maxDepth: Int = 3): Long {
        if (maxDepth < 0) return 0L
        var size = 0L
        try {
            val children = dir.listFiles() ?: return 0L
            for (c in children) {
                if (c.isFile) {
                    size += c.length()
                } else if (c.isDirectory && maxDepth > 0) {
                    size += calculateFolderSize(c, maxDepth - 1)
                }
            }
        } catch (_: Exception) {}
        return size
    }

    private fun calculateDocFolderSize(docDir: DocumentFile, maxDepth: Int = 2): Long {
        if (maxDepth < 0) return 0L
        var size = 0L
        try {
            val children = docDir.listFiles()
            for (c in children) {
                if (!c.isDirectory) {
                    size += c.length()
                } else if (maxDepth > 0) {
                    size += calculateDocFolderSize(c, maxDepth - 1)
                }
            }
        } catch (_: Exception) {}
        return size
    }

    override suspend fun calculateItemSize(path: String, treeUri: String?): Long = withContext(Dispatchers.IO) {
        try {
            if (!path.startsWith("content://")) {
                val file = File(path)
                if (file.exists()) {
                    return@withContext if (file.isFile) file.length() else calculateFolderSize(file, maxDepth = 100)
                }
            }

            val isContent = path.startsWith("content://")
            val doc = if (isContent) {
                val uri = Uri.parse(path)
                DocumentFile.fromSingleUri(context, uri) ?: DocumentFile.fromTreeUri(context, uri)
            } else {
                val effectiveTree = treeUri ?: if (isExternalStoragePath(path)) getPersistedTreeUriForPath(path) else null
                if (effectiveTree != null) {
                    val uuid = extractVolumeUuid(path)
                    if (uuid != null) {
                        try {
                            val docId = FileUriResolver.buildDocumentId(uuid, path)
                            val treeUriObj = Uri.parse(effectiveTree)
                            val treeId = DocumentsContract.getTreeDocumentId(treeUriObj)
                            val rootTreeUri = DocumentsContract.buildTreeDocumentUri(treeUriObj.authority, treeId)
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, docId)
                            DocumentFile.fromSingleUri(context, docUri) ?: DocumentFile.fromTreeUri(context, docUri)
                        } catch (_: Exception) { null }
                    } else null
                } else null
            }

            if (doc != null && doc.exists()) {
                return@withContext if (!doc.isDirectory) doc.length() else calculateDocFolderSize(doc, maxDepth = 100)
            }
            0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return "application/octet-stream"
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        return mime ?: when (ext) {
            "apk" -> "application/vnd.android.package-archive"
            "zip" -> "application/zip"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            "7z" -> "application/x-7z-compressed"
            "rar" -> "application/x-rar-compressed"
            "json" -> "application/json"
            "txt", "log", "md" -> "text/plain"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            else -> "application/octet-stream"
        }
    }

    private fun getDocumentFileForTarget(destDirPath: String, destTreeUri: String?, createIfMissing: Boolean = false): DocumentFile? {
        // Case 1: If destDirPath is already a content:// URI
        if (destDirPath.startsWith("content://")) {
            val uri = Uri.parse(destDirPath)
            val doc = try {
                if (destDirPath.contains("/document/") && !destDirPath.contains("com.android.mtp.documents")) {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val treeId = DocumentsContract.getTreeDocumentId(uri)
                    val rootTreeUri = DocumentsContract.buildTreeDocumentUri(uri.authority, treeId)
                    val docTreeUri = DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, docId)
                    DocumentFile.fromTreeUri(context, docTreeUri) ?: DocumentFile.fromSingleUri(context, uri)
                } else {
                    DocumentFile.fromTreeUri(context, uri) ?: DocumentFile.fromSingleUri(context, uri)
                }
            } catch (_: Exception) {
                try { DocumentFile.fromSingleUri(context, uri) } catch (_: Exception) { null }
            }
            if (doc != null && doc.exists()) {
                return doc
            }
        }

        // Case 2: Filesystem path or mtp:// path on an external volume
        val effectiveTreeUri = destTreeUri
            ?: if (isExternalStoragePath(destDirPath)) {
                getPersistedTreeUriForVolume(null, destDirPath)
            } else null

        if (effectiveTreeUri != null) {
            val rootUri = Uri.parse(effectiveTreeUri)
            val rootDoc = try {
                DocumentFile.fromTreeUri(context, rootUri) ?: DocumentFile.fromSingleUri(context, rootUri)
            } catch (_: Exception) { null } ?: return null

            val uuid = extractVolumeUuid(destDirPath)
            val relative = when {
                destDirPath.startsWith("mtp://") -> {
                    val after = destDirPath.removePrefix("mtp://").trim('/')
                    if (after.contains('/')) {
                        after.substringAfter('/').trim('/')
                    } else {
                        ""
                    }
                }
                uuid != null && destDirPath.startsWith("/storage/$uuid") -> {
                    destDirPath.removePrefix("/storage/$uuid").trim('/')
                }
                uuid != null && destDirPath.startsWith("/mnt/media_rw/$uuid") -> {
                    destDirPath.removePrefix("/mnt/media_rw/$uuid").trim('/')
                }
                !destDirPath.startsWith("content://") && !destDirPath.startsWith("/") -> {
                    destDirPath.trim('/')
                }
                else -> ""
            }

            if (relative.isEmpty()) return rootDoc

            // 1. Try direct Document URI first (fastest and cleanest)
            if (uuid != null && !destDirPath.startsWith("mtp://")) {
                try {
                    val docId = FileUriResolver.buildDocumentId(uuid, destDirPath)
                    val treeId = DocumentsContract.getTreeDocumentId(rootUri)
                    val rootTreeUri = DocumentsContract.buildTreeDocumentUri(rootUri.authority, treeId)
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, docId)
                    val directDoc = DocumentFile.fromTreeUri(context, docUri)
                    if (directDoc != null && directDoc.exists() && directDoc.isDirectory) {
                        return directDoc
                    }
                } catch (_: Exception) {}
            }

            // 2. Traversal fallback
            var currentDoc = rootDoc
            val segments = relative.split("/").filter { it.isNotEmpty() }
            for (seg in segments) {
                val existing = try {
                    currentDoc.listFiles().firstOrNull { it.name.equals(seg, ignoreCase = true) && it.isDirectory }
                } catch (_: Exception) { null }
                val nextDoc = existing ?: (if (createIfMissing) currentDoc.createDirectory(seg) else null) ?: return null
                currentDoc = nextDoc
            }
            return currentDoc
        }
        return null
    }

    private suspend fun copyStreamInternal(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        pfd: android.os.ParcelFileDescriptor?,
        fileName: String,
        currentCopiedRef: LongArray,
        totalTransferBytes: Long,
        isCancelled: (() -> Boolean)?,
        onProgress: ((copiedBytes: Long, totalBytes: Long, currentFile: String) -> Unit)?
    ): Boolean = withContext(Dispatchers.IO) {
        val buffer = ByteArray(256 * 1024)
        var bytesRead: Int
        var lastUpdate = 0L

        while (input.read(buffer).also { bytesRead = it } != -1) {
            if (isCancelled?.invoke() == true) return@withContext false
            output.write(buffer, 0, bytesRead)
            currentCopiedRef[0] += bytesRead
            val now = System.currentTimeMillis()
            if (now - lastUpdate > 100) {
                lastUpdate = now
                onProgress?.invoke(currentCopiedRef[0], totalTransferBytes, fileName)
            }
        }
        output.flush()
        try {
            pfd?.fileDescriptor?.sync()
        } catch (_: Exception) {}
        onProgress?.invoke(currentCopiedRef[0], totalTransferBytes, fileName)
        true
    }

    private suspend fun copyFileToDoc(
        srcFile: File,
        destDoc: DocumentFile,
        currentCopiedRef: LongArray,
        totalBytes: Long,
        isCancelled: (() -> Boolean)?,
        onProgress: ((copiedBytes: Long, totalBytes: Long, currentFile: String) -> Unit)?
    ): Boolean {
        if (!srcFile.exists()) return false
        val fileName = srcFile.name
        val mimeType = getMimeType(fileName)

        destDoc.findFile(fileName)?.let { existing ->
            try { existing.delete() } catch (_: Exception) {}
        }

        val targetDoc = destDoc.createFile(mimeType, fileName)
            ?: destDoc.createFile("application/octet-stream", fileName)
            ?: destDoc.createFile("*/*", fileName)
            ?: return false

        val targetUri = targetDoc.uri
        return try {
            srcFile.inputStream().use { input ->
                context.contentResolver.openOutputStream(targetUri, "w")?.use { output ->
                    val ok = copyStreamInternal(input, output, null, fileName, currentCopiedRef, totalBytes, isCancelled, onProgress)
                    if (!ok) {
                        try { targetDoc.delete() } catch (_: Exception) {}
                        false
                    } else {
                        true
                    }
                } ?: run {
                    try { targetDoc.delete() } catch (_: Exception) {}
                    false
                }
            }
        } catch (e: Exception) {
            try { targetDoc.delete() } catch (_: Exception) {}
            false
        }
    }

    private suspend fun copyDirectoryToDoc(
        srcDir: File,
        destDoc: DocumentFile,
        currentCopiedRef: LongArray,
        totalBytes: Long,
        isCancelled: (() -> Boolean)?,
        onProgress: ((copiedBytes: Long, totalBytes: Long, currentFile: String) -> Unit)?
    ): Boolean {
        if (!srcDir.exists() || !srcDir.isDirectory) return false
        val dirName = srcDir.name

        val targetSubDoc = destDoc.findFile(dirName)?.takeIf { it.isDirectory }
            ?: destDoc.createDirectory(dirName)
            ?: return false

        val children = srcDir.listFiles() ?: return true
        for (child in children) {
            if (isCancelled?.invoke() == true) return false
            if (child.isDirectory) {
                val ok = copyDirectoryToDoc(child, targetSubDoc, currentCopiedRef, totalBytes, isCancelled, onProgress)
                if (!ok) return false
            } else {
                val ok = copyFileToDoc(child, targetSubDoc, currentCopiedRef, totalBytes, isCancelled, onProgress)
                if (!ok) return false
            }
        }
        return true
    }

    private suspend fun copyDocToFile(
        srcDoc: DocumentFile,
        destDir: File,
        currentCopiedRef: LongArray,
        totalBytes: Long,
        isCancelled: (() -> Boolean)?,
        onProgress: ((copiedBytes: Long, totalBytes: Long, currentFile: String) -> Unit)?
    ): Boolean {
        if (!destDir.exists()) destDir.mkdirs()
        val fileName = srcDoc.name ?: "file"
        var destFile = File(destDir, fileName)
        if (destFile.exists()) {
            val base = destFile.nameWithoutExtension
            val ext = if (destFile.extension.isNotEmpty()) ".${destFile.extension}" else ""
            var count = 1
            while (destFile.exists()) {
                destFile = File(destDir, "$base - Copy ($count)$ext")
                count++
            }
        }

        return try {
            val inStream = context.contentResolver.openInputStream(srcDoc.uri)
                ?: return false
            inStream.use { input ->
                destFile.outputStream().use { out ->
                    val ok = copyStreamInternal(input, out, null, fileName, currentCopiedRef, totalBytes, isCancelled, onProgress)
                    if (!ok) {
                        try { destFile.delete() } catch (_: Exception) {}
                        false
                    } else {
                        true
                    }
                }
            }
        } catch (e: Exception) {
            try { destFile.delete() } catch (_: Exception) {}
            false
        }
    }

    private suspend fun copyDocToDirectory(
        srcDoc: DocumentFile,
        destDir: File,
        currentCopiedRef: LongArray,
        totalBytes: Long,
        isCancelled: (() -> Boolean)?,
        onProgress: ((copiedBytes: Long, totalBytes: Long, currentFile: String) -> Unit)?
    ): Boolean {
        val dirName = srcDoc.name ?: "folder"
        val subDestDir = File(destDir, dirName)
        if (!subDestDir.exists()) subDestDir.mkdirs()

        val children = srcDoc.listFiles()
        for (child in children) {
            if (isCancelled?.invoke() == true) return false
            if (child.isDirectory) {
                val ok = copyDocToDirectory(child, subDestDir, currentCopiedRef, totalBytes, isCancelled, onProgress)
                if (!ok) return false
            } else {
                val ok = copyDocToFile(child, subDestDir, currentCopiedRef, totalBytes, isCancelled, onProgress)
                if (!ok) return false
            }
        }
        return true
    }

    private suspend fun copyDocStreamToDoc(
        srcDoc: DocumentFile,
        destDoc: DocumentFile,
        currentCopiedRef: LongArray,
        totalBytes: Long,
        isCancelled: (() -> Boolean)?,
        onProgress: ((copiedBytes: Long, totalBytes: Long, currentFile: String) -> Unit)?
    ): Boolean {
        val fileName = srcDoc.name ?: "file"
        val mimeType = srcDoc.type ?: getMimeType(fileName)
        destDoc.findFile(fileName)?.let { existing ->
            try { existing.delete() } catch (_: Exception) {}
        }
        val targetDoc = destDoc.createFile(mimeType, fileName)
            ?: destDoc.createFile("application/octet-stream", fileName)
            ?: destDoc.createFile("*/*", fileName)
            ?: return false

        return try {
            val inStream = context.contentResolver.openInputStream(srcDoc.uri)
                ?: return false
            inStream.use { input ->
                context.contentResolver.openOutputStream(targetDoc.uri, "w")?.use { output ->
                    val ok = copyStreamInternal(input, output, null, fileName, currentCopiedRef, totalBytes, isCancelled, onProgress)
                    if (!ok) {
                        try { targetDoc.delete() } catch (_: Exception) {}
                        false
                    } else {
                        true
                    }
                } ?: run {
                    try { targetDoc.delete() } catch (_: Exception) {}
                    false
                }
            }
        } catch (e: Exception) {
            try { targetDoc.delete() } catch (_: Exception) {}
            false
        }
    }

    private suspend fun copyDocToDoc(
        srcDoc: DocumentFile,
        destDoc: DocumentFile,
        currentCopiedRef: LongArray,
        totalBytes: Long,
        isCancelled: (() -> Boolean)?,
        onProgress: ((copiedBytes: Long, totalBytes: Long, currentFile: String) -> Unit)?
    ): Boolean {
        if (srcDoc.isDirectory) {
            val dirName = srcDoc.name ?: "folder"
            val subDestDoc = destDoc.findFile(dirName)?.takeIf { it.isDirectory }
                ?: destDoc.createDirectory(dirName)
                ?: return false

            val children = srcDoc.listFiles()
            for (child in children) {
                if (isCancelled?.invoke() == true) return false
                if (child.isDirectory) {
                    val ok = copyDocToDoc(child, subDestDoc, currentCopiedRef, totalBytes, isCancelled, onProgress)
                    if (!ok) return false
                } else {
                    val ok = copyDocStreamToDoc(child, subDestDoc, currentCopiedRef, totalBytes, isCancelled, onProgress)
                    if (!ok) return false
                }
            }
            return true
        } else {
            return copyDocStreamToDoc(srcDoc, destDoc, currentCopiedRef, totalBytes, isCancelled, onProgress)
        }
    }

    private suspend fun copyFileToFile(
        srcFile: File,
        destDir: File,
        currentCopiedRef: LongArray,
        totalBytes: Long,
        isCancelled: (() -> Boolean)?,
        onProgress: ((copiedBytes: Long, totalBytes: Long, currentFile: String) -> Unit)?
    ): Boolean {
        if (!destDir.exists()) destDir.mkdirs()
        var destFile = File(destDir, srcFile.name)
        if (destFile.exists()) {
            val base = srcFile.nameWithoutExtension
            val ext = if (srcFile.extension.isNotEmpty()) ".${srcFile.extension}" else ""
            var counter = 1
            while (destFile.exists()) {
                destFile = File(destDir, "$base - Copy ($counter)$ext")
                counter++
            }
        }

        try {
            srcFile.inputStream().use { input ->
                destFile.outputStream().use { out ->
                    val ok = copyStreamInternal(input, out, null, srcFile.name, currentCopiedRef, totalBytes, isCancelled, onProgress)
                    if (!ok) {
                        try { destFile.delete() } catch (_: Exception) {}
                        return false
                    }
                }
            }
            return true
        } catch (e: Exception) {
            try { destFile.delete() } catch (_: Exception) {}
            return false
        }
    }

    private suspend fun copyDirectoryToDirectory(
        srcDir: File,
        destDir: File,
        currentCopiedRef: LongArray,
        totalBytes: Long,
        isCancelled: (() -> Boolean)?,
        onProgress: ((copiedBytes: Long, totalBytes: Long, currentFile: String) -> Unit)?
    ): Boolean {
        val subDestDir = File(destDir, srcDir.name)
        if (!subDestDir.exists()) subDestDir.mkdirs()

        val children = srcDir.listFiles() ?: return true
        for (child in children) {
            if (isCancelled?.invoke() == true) return false
            if (child.isDirectory) {
                val ok = copyDirectoryToDirectory(child, subDestDir, currentCopiedRef, totalBytes, isCancelled, onProgress)
                if (!ok) return false
            } else {
                val ok = copyFileToFile(child, subDestDir, currentCopiedRef, totalBytes, isCancelled, onProgress)
                if (!ok) return false
            }
        }
        return true
    }

    override suspend fun copyItem(
        sourcePath: String,
        destDirPath: String,
        sourceTreeUri: String?,
        destTreeUri: String?,
        onProgress: ((copiedBytes: Long, totalBytes: Long, currentFile: String) -> Unit)?,
        isCancelled: (() -> Boolean)?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val totalBytes = calculateItemSize(sourcePath, sourceTreeUri)
            val currentCopiedRef = LongArray(1) { 0L }

            val effectiveDestTree = destTreeUri
                ?: if (isExternalStoragePath(destDirPath)) getPersistedTreeUriForPath(destDirPath) else null
            val targetDoc = getDocumentFileForTarget(destDirPath, effectiveDestTree, createIfMissing = true)

            val isSrcContentUri = sourcePath.startsWith("content://")
            val srcFile = if (!isSrcContentUri) File(sourcePath) else null

            if (srcFile != null && srcFile.exists()) {
                if (targetDoc != null) {
                    if (srcFile.isDirectory) {
                        return@withContext copyDirectoryToDoc(srcFile, targetDoc, currentCopiedRef, totalBytes, isCancelled, onProgress)
                    } else {
                        return@withContext copyFileToDoc(srcFile, targetDoc, currentCopiedRef, totalBytes, isCancelled, onProgress)
                    }
                } else {
                    val destDir = File(destDirPath)
                    if (!destDir.exists()) destDir.mkdirs()
                    if (srcFile.isDirectory) {
                        return@withContext copyDirectoryToDirectory(srcFile, destDir, currentCopiedRef, totalBytes, isCancelled, onProgress)
                    } else {
                        return@withContext copyFileToFile(srcFile, destDir, currentCopiedRef, totalBytes, isCancelled, onProgress)
                    }
                }
            } else {
                val effectiveSrcTree = sourceTreeUri
                    ?: if (isExternalStoragePath(sourcePath)) getPersistedTreeUriForPath(sourcePath) else null

                val srcDoc = if (isSrcContentUri) {
                    val uri = Uri.parse(sourcePath)
                    DocumentFile.fromSingleUri(context, uri) ?: DocumentFile.fromTreeUri(context, uri)
                } else if (effectiveSrcTree != null) {
                    val uuid = extractVolumeUuid(sourcePath)
                    if (uuid != null) {
                        try {
                            val docId = FileUriResolver.buildDocumentId(uuid, sourcePath)
                            val treeUriObj = Uri.parse(effectiveSrcTree)
                            val treeId = DocumentsContract.getTreeDocumentId(treeUriObj)
                            val rootTreeUri = DocumentsContract.buildTreeDocumentUri(treeUriObj.authority, treeId)
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, docId)
                            DocumentFile.fromSingleUri(context, docUri) ?: DocumentFile.fromTreeUri(context, docUri)
                        } catch (_: Exception) { null }
                    } else null
                } else null

                if (srcDoc == null || !srcDoc.exists()) return@withContext false

                if (targetDoc != null) {
                    if (srcDoc.isDirectory) {
                        return@withContext copyDocToDoc(srcDoc, targetDoc, currentCopiedRef, totalBytes, isCancelled, onProgress)
                    } else {
                        return@withContext copyDocStreamToDoc(srcDoc, targetDoc, currentCopiedRef, totalBytes, isCancelled, onProgress)
                    }
                } else {
                    val destDir = File(destDirPath)
                    if (!destDir.exists()) destDir.mkdirs()
                    if (srcDoc.isDirectory) {
                        return@withContext copyDocToDirectory(srcDoc, destDir, currentCopiedRef, totalBytes, isCancelled, onProgress)
                    } else {
                        return@withContext copyDocToFile(srcDoc, destDir, currentCopiedRef, totalBytes, isCancelled, onProgress)
                    }
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun moveItem(
        sourcePath: String,
        destDirPath: String,
        sourceTreeUri: String?,
        destTreeUri: String?,
        onProgress: ((copiedBytes: Long, totalBytes: Long, currentFile: String) -> Unit)?,
        isCancelled: (() -> Boolean)?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val isSrcDoc = sourceTreeUri != null || sourcePath.startsWith("content://") || isExternalStoragePath(sourcePath)
            val isDestDoc = destTreeUri != null || destDirPath.startsWith("content://") || isExternalStoragePath(destDirPath)

            if (!isSrcDoc && !isDestDoc) {
                val src = File(sourcePath)
                if (!src.exists()) return@withContext false
                val destDir = File(destDirPath)
                if (!destDir.exists()) destDir.mkdirs()

                val destFile = File(destDir, src.name)
                if (src.renameTo(destFile)) {
                    val size = if (destFile.isFile) destFile.length() else calculateFolderSize(destFile)
                    onProgress?.invoke(size, size, src.name)
                    return@withContext true
                }
            }

            // Cross-filesystem or DocumentFile move: copy then delete source
            val copied = copyItem(sourcePath, destDirPath, sourceTreeUri, destTreeUri, onProgress, isCancelled)
            if (copied) {
                if (isCancelled?.invoke() == true) return@withContext false
                delete(sourcePath, sourceTreeUri)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun createShortcut(
        targetItem: FileItem,
        destDirPath: String,
        destTreeUri: String?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val baseName = if (targetItem.isDirectory) {
                targetItem.name
            } else {
                targetItem.name.substringBeforeLast('.')
            }
            val shortcutBase = "$baseName - Shortcut"
            val json = JSONObject().apply {
                put("type", "shortcut")
                put("targetPath", targetItem.path)
                put("targetTreeUri", targetItem.treeUri ?: "")
                put("targetName", targetItem.name)
                put("isDirectory", targetItem.isDirectory)
                put("createdAt", System.currentTimeMillis())
            }

            if (destTreeUri != null || destDirPath.startsWith("content://")) {
                val destUri = Uri.parse(destTreeUri ?: destDirPath)
                val parentDoc = DocumentFile.fromTreeUri(context, destUri) ?: return@withContext false
                val fileDoc = parentDoc.createFile("application/json", "$shortcutBase.lnk") ?: return@withContext false
                context.contentResolver.openOutputStream(fileDoc.uri)?.use { out ->
                    out.write(json.toString(2).toByteArray())
                }
                return@withContext true
            }

            val destDir = File(destDirPath)
            if (!destDir.exists()) {
                destDir.mkdirs()
            }
            var candidate = File(destDir, "$shortcutBase.lnk")
            var count = 2
            while (candidate.exists()) {
                candidate = File(destDir, "$shortcutBase ($count).lnk")
                count++
            }
            candidate.writeText(json.toString(2))
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun delete(path: String, treeUri: String?): Boolean = withContext(Dispatchers.IO) {
        if (path.startsWith("content://")) {
            val doc = DocumentFile.fromSingleUri(context, Uri.parse(path)) ?: return@withContext false
            return@withContext doc.delete()
        }
        val file = File(path)
        if (file.exists() && file.delete()) {
            return@withContext true
        }
        if (file.exists() && file.isDirectory && file.deleteRecursively()) {
            return@withContext true
        }
        val effectiveTreeUri = treeUri ?: if (isExternalStoragePath(path)) {
            getPersistedTreeUriForPath(path)
        } else null

        if (effectiveTreeUri != null) {
            val uuid = extractVolumeUuid(path)
            if (uuid != null) {
                try {
                    val docId = FileUriResolver.buildDocumentId(uuid, path)
                    val treeUriObj = Uri.parse(effectiveTreeUri)
                    val treeId = DocumentsContract.getTreeDocumentId(treeUriObj)
                    val rootTreeUri = DocumentsContract.buildTreeDocumentUri(treeUriObj.authority, treeId)
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, docId)
                    val doc = DocumentFile.fromSingleUri(context, docUri) ?: DocumentFile.fromTreeUri(context, docUri)
                    if (doc != null && doc.exists() && doc.delete()) {
                        return@withContext true
                    }
                } catch (_: Exception) {}
            }

            val parentPath = path.substringBeforeLast('/')
            val fileName = path.substringAfterLast('/')
            val parentDoc = getDocumentFileForTarget(parentPath, effectiveTreeUri)
            val fileDoc = parentDoc?.findFile(fileName)
            if (fileDoc != null && fileDoc.delete()) {
                return@withContext true
            }
        }
        false
    }

    override suspend fun addCloudDrive(name: String, treeUri: String) = withContext(Dispatchers.IO) {
        val list = loadCloudDrivesJsonList()
        if (list.none { it.optString("treeUri") == treeUri }) {
            val obj = JSONObject().apply {
                put("name", name)
                put("treeUri", treeUri)
            }
            list.add(obj)
            saveCloudDrivesJsonList(list)
        }
    }

    override suspend fun removeCloudDrive(treeUri: String) = withContext(Dispatchers.IO) {
        val list = loadCloudDrivesJsonList()
        val filtered = list.filter { it.optString("treeUri") != treeUri }
        saveCloudDrivesJsonList(filtered)
    }

    override suspend fun getCloudDrives(): List<DriveItem> = withContext(Dispatchers.IO) {
        val list = loadCloudDrivesJsonList()
        list.map { obj ->
            val name = obj.getString("name")
            val uri = obj.getString("treeUri")
            DriveItem(
                id = uri,
                name = name,
                path = name,
                type = DriveType.CLOUD,
                treeUri = uri
            )
        }
    }

    private fun getPinnedPaths(): Set<String> {
        val set = mutableSetOf<String>()
        val rawJson = prefs.getString(KEY_PINNED, null) ?: return emptySet()
        try {
            val array = JSONArray(rawJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                set.add(obj.getString("path"))
                val uri = obj.optString("treeUri")
                if (uri.isNotEmpty()) {
                    set.add(uri)
                }
            }
        } catch (_: Exception) {}
        return set
    }

    private fun loadPinnedJsonList(): MutableList<JSONObject> {
        val list = mutableListOf<JSONObject>()
        val raw = prefs.getString(KEY_PINNED, null) ?: return list
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                list.add(array.getJSONObject(i))
            }
        } catch (_: Exception) {}
        return list
    }

    private fun savePinnedJsonList(list: List<JSONObject>) {
        val array = JSONArray()
        list.forEach { array.put(it) }
        prefs.edit().putString(KEY_PINNED, array.toString()).apply()
    }

    private fun loadCloudDrivesJsonList(): MutableList<JSONObject> {
        val list = mutableListOf<JSONObject>()
        val raw = prefs.getString(KEY_CLOUD_DRIVES, null) ?: return list
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                list.add(array.getJSONObject(i))
            }
        } catch (_: Exception) {}
        return list
    }

    private fun saveCloudDrivesJsonList(list: List<JSONObject>) {
        val array = JSONArray()
        list.forEach { array.put(it) }
        prefs.edit().putString(KEY_CLOUD_DRIVES, array.toString()).apply()
    }

    private fun getSpaceInfo(path: String): Pair<Long, Long> {
        return try {
            val stat = StatFs(path)
            Pair(stat.totalBytes, stat.availableBytes)
        } catch (_: Exception) {
            Pair(0L, 0L)
        }
    }

    override suspend fun exists(path: String, treeUri: String?): Boolean = withContext(Dispatchers.IO) {
        val clean = path.trimEnd('/')
        if (clean.isEmpty() || clean == "/storage/emulated/10" || clean == "/storage/emulated/0" || clean == "/storage/self/primary" || clean == "/storage") {
            return@withContext true
        }
        if (treeUri != null || clean.startsWith("content://")) {
            return@withContext true
        }
        val drives = getStorageDrives()
        if (drives.any { it.path.trimEnd('/') == clean || clean.startsWith(it.path.trimEnd('/') + "/") }) {
            return@withContext true
        }
        val f = File(clean)
        if (f.exists()) return@withContext true
        val persistedUri = getPersistedTreeUriForPath(clean)
        if (persistedUri != null) return@withContext true
        false
    }

    override fun isVideoThumbnailsEnabled(): Boolean {
        return prefs.getBoolean("show_video_thumbnails", true)
    }

    override fun setVideoThumbnailsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("show_video_thumbnails", enabled).apply()
    }

    override fun getViewMode(): ViewMode {
        val modeName = prefs.getString("view_mode", ViewMode.GRID.name) ?: ViewMode.GRID.name
        return try {
            ViewMode.valueOf(modeName)
        } catch (_: Exception) {
            ViewMode.GRID
        }
    }

    override fun setViewMode(mode: ViewMode) {
        prefs.edit().putString("view_mode", mode.name).apply()
    }
}
