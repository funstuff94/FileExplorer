package com.droid.fileexplorer.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import com.droid.fileexplorer.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Memory-cached, background video thumbnail loader.
 * Supports direct filesystem paths, secondary external storage, and SAF/MTP content URIs.
 */
object VideoThumbnailLoader {

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceIn(1024 * 16, 1024 * 64) // 16MB to 64MB cache
    private val decodeSemaphore = Semaphore(2)

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun getCached(path: String): Bitmap? {
        return try { memoryCache.get(path) } catch (_: Throwable) { null }
    }

    suspend fun loadThumbnail(context: Context, item: FileItem): Bitmap? = withContext(Dispatchers.IO) {
        val path = if (item.isShortcut && !item.shortcutTargetPath.isNullOrEmpty()) {
            item.shortcutTargetPath
        } else {
            item.path
        }

        val cached = getCached(path)
        if (cached != null) return@withContext cached

        decodeSemaphore.withPermit {
            val recheck = getCached(path)
            if (recheck != null) return@withPermit recheck

            var bitmap: Bitmap? = null

            // 1. Direct file on local storage
            if (!path.startsWith("content://")) {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            bitmap = ThumbnailUtils.createVideoThumbnail(file, Size(256, 256), null)
                        } catch (_: Throwable) {}
                    }
                    if (bitmap == null) {
                        try {
                            @Suppress("DEPRECATION")
                            bitmap = ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND)
                        } catch (_: Throwable) {}
                    }
                }
            }

            // 2. Direct Content URI (SAF / MTP)
            if (bitmap == null && path.startsWith("content://")) {
                val uri = Uri.parse(path)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        bitmap = context.contentResolver.loadThumbnail(uri, Size(256, 256), null)
                    } catch (_: Throwable) {}
                }
            }

            // 3. MediaMetadataRetriever (universal fallback for MP4, MKV, AVI, MOV, WEBM, etc.)
            if (bitmap == null) {
                var retriever: MediaMetadataRetriever? = null
                try {
                    retriever = MediaMetadataRetriever()
                    var setSuccess = false
                    if (path.startsWith("content://")) {
                        retriever.setDataSource(context, Uri.parse(path))
                        setSuccess = true
                    } else {
                        val file = File(path)
                        if (file.exists() && file.canRead()) {
                            retriever.setDataSource(path)
                            setSuccess = true
                        } else {
                            val treeUriStr = item.shortcutTargetTreeUri ?: item.treeUri
                            if (treeUriStr != null) {
                                val uuid = FileUriResolver.extractVolumeUuid(path)
                                if (uuid != null) {
                                    val docId = FileUriResolver.buildDocumentId(uuid, path)
                                    val treeUriObj = Uri.parse(treeUriStr)
                                    val treeId = DocumentsContract.getTreeDocumentId(treeUriObj)
                                    val rootTreeUri = DocumentsContract.buildTreeDocumentUri(treeUriObj.authority, treeId)
                                    val docUri = DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, docId)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        try {
                                            bitmap = context.contentResolver.loadThumbnail(docUri, Size(256, 256), null)
                                        } catch (_: Throwable) {}
                                    }
                                    if (bitmap == null) {
                                        retriever.setDataSource(context, docUri)
                                        setSuccess = true
                                    }
                                }
                            }
                        }
                    }

                    if (bitmap == null && setSuccess) {
                        bitmap = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: retriever.frameAtTime
                    }
                } catch (_: Throwable) {
                } finally {
                    try { retriever?.release() } catch (_: Throwable) {}
                }
            }

            if (bitmap != null) {
                val scaled = scaleDown(bitmap!!, 256)
                try { memoryCache.put(path, scaled) } catch (_: Throwable) {}
                return@withPermit scaled
            }

            null
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap
        val ratio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int
        if (width > height) {
            targetWidth = maxDim
            targetHeight = (maxDim / ratio).toInt().coerceAtLeast(1)
        } else {
            targetHeight = maxDim
            targetWidth = (maxDim * ratio).toInt().coerceAtLeast(1)
        }
        return try {
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        } catch (_: Throwable) {
            bitmap
        }
    }

    fun clearCache() {
        memoryCache.evictAll()
    }
}
