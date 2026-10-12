package com.droid.fileexplorer.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.droid.fileexplorer.model.FileItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object FileOpener {

    fun openFile(context: Context, item: FileItem) {
        val ext = item.extension.lowercase()
        val isApk = ext == "apk" || item.mimeType == "application/vnd.android.package-archive"

        if (isApk) {
            installApk(context, item)
            return
        }

        try {
            val uri: Uri = FileUriResolver.resolveFileUri(context, item) ?: run {
                Toast.makeText(context, "Cannot locate file: ${item.name}", Toast.LENGTH_SHORT).show()
                return
            }

            val mimeType = FileUriResolver.getMimeType(context, item, uri)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                try {
                    val chooser = Intent.createChooser(intent, "Open with").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                } catch (e: Exception) {
                    Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun installApk(context: Context, item: FileItem) {
        // 1. Check unknown app sources permission on Android 8.0+ (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                try {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    Toast.makeText(
                        context,
                        "Please allow 'File Explorer' to install unknown apps, then tap the APK again",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Cannot open installation settings: ${e.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }
        }

        // 2. Direct FileProvider for local internal storage files
        val localFile = File(item.path)
        val isExternalDrive = FileUriResolver.isExternalStoragePath(item.path)
        if (!isExternalDrive && localFile.exists() && localFile.canRead()) {
            try {
                val apkUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    localFile
                )
                launchPackageInstaller(context, apkUri)
                return
            } catch (_: Exception) {
                // Fall through to cache preparation
            }
        }

        // 3. For USB / SD card (secondary storage), SAF URIs, or FileProvider fallback:
        // Copy to app cache directory and serve via FileProvider
        Toast.makeText(context, "Preparing ${item.name} for installation...", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val cacheUri = withContext(Dispatchers.IO) {
                    prepareApkInCache(context, item)
                }
                if (cacheUri != null) {
                    launchPackageInstaller(context, cacheUri)
                } else {
                    Toast.makeText(context, "Failed to prepare APK for installation", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error preparing APK: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun prepareApkInCache(context: Context, item: FileItem): Uri? {
        val cacheDir = File(context.cacheDir, "apk_install").apply {
            if (!exists()) mkdirs()
        }
        // Clean previous cached APKs to prevent storage bloat
        cacheDir.listFiles()?.forEach {
            try { it.delete() } catch (_: Exception) {}
        }

        val safeName = if (item.name.endsWith(".apk", ignoreCase = true)) item.name else "${item.name}.apk"
        val cacheFile = File(cacheDir, safeName)

        val localFile = File(item.path)
        if (localFile.exists() && localFile.canRead()) {
            localFile.inputStream().use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            val sourceUri = FileUriResolver.resolveFileUri(context, item) ?: Uri.parse(item.path)
            val stream = context.contentResolver.openInputStream(sourceUri)
                ?: throw IllegalStateException("Cannot open file stream for ${item.path}")
            stream.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheFile
        )
    }

    private fun launchPackageInstaller(context: Context, apkUri: Uri) {
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Grant URI permission explicitly to any resolving package installers
        try {
            val handlers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentActivities(
                    installIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(installIntent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            for (handler in handlers) {
                context.grantUriPermission(
                    handler.activityInfo.packageName,
                    apkUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        } catch (_: Exception) {}

        try {
            context.startActivity(installIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No package installer app found on device", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot install APK: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFile(context: Context, item: FileItem) {
        try {
            val uri: Uri = FileUriResolver.resolveFileUri(context, item) ?: run {
                Toast.makeText(context, "Cannot locate file: ${item.name}", Toast.LENGTH_SHORT).show()
                return
            }

            val mimeType = FileUriResolver.getMimeType(context, item, uri)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share ${item.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot share file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
