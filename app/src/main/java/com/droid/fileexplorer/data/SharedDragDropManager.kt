package com.droid.fileexplorer.data

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import com.droid.fileexplorer.model.FileItem
import com.droid.fileexplorer.util.FileUriResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Cross-window and in-window drag and drop manager.
 * Preserves the active dragged items across separate Activity tasks and windows.
 */
object SharedDragDropManager {
    const val MIME_TYPE_FILE_ITEMS = "application/x-fileexplorer-item"
    const val CLIP_DATA_LABEL = "com.droid.fileexplorer.drag"

    var activeDraggedItems: List<FileItem> = emptyList()
        private set
    private var lastDraggedItems: List<FileItem> = emptyList()
    var sourceDirPath: String = ""
        private set
    var sourceTreeUri: String? = null
        private set

    private val _isDragging = MutableStateFlow(false)
    val isDragging: StateFlow<Boolean> = _isDragging.asStateFlow()

    fun startDrag(items: List<FileItem>, sourceDir: String, treeUri: String? = null) {
        activeDraggedItems = items
        lastDraggedItems = items
        sourceDirPath = sourceDir.trimEnd('/')
        sourceTreeUri = treeUri
        _isDragging.value = true
    }

    fun endDrag() {
        activeDraggedItems = emptyList()
        sourceDirPath = ""
        sourceTreeUri = null
        _isDragging.value = false
    }

    fun createClipData(
        context: Context,
        items: List<FileItem>,
        treeUri: String? = null
    ): ClipData {
        if (items.isEmpty()) {
            return ClipData.newPlainText(CLIP_DATA_LABEL, "")
        }

        return try {
            val effectiveTree = treeUri ?: sourceTreeUri
            val firstItem = items.first()
            val firstUri = if (!firstItem.isDirectory) {
                FileUriResolver.resolveFileUri(context, firstItem, effectiveTree)
            } else null
            val firstMime = if (!firstItem.isDirectory) {
                FileUriResolver.getMimeType(context, firstItem, firstUri)
            } else ""

            val mimeTypes = mutableListOf<String>()
            if (firstMime.isNotEmpty()) {
                mimeTypes.add(firstMime)
            }
            mimeTypes.add(MIME_TYPE_FILE_ITEMS)
            mimeTypes.add(ClipDescription.MIMETYPE_TEXT_PLAIN)
            if (firstUri != null) {
                mimeTypes.add(ClipDescription.MIMETYPE_TEXT_URILIST)
            }

            val clipDesc = ClipDescription(CLIP_DATA_LABEL, mimeTypes.toTypedArray())
            val firstClipItem = if (firstUri != null) {
                ClipData.Item(firstItem.path, null as Intent?, firstUri)
            } else {
                ClipData.Item(firstItem.path)
            }

            val clipData = ClipData(clipDesc, firstClipItem)

            for (i in 1 until items.size) {
                val item = items[i]
                val uri = if (!item.isDirectory) {
                    FileUriResolver.resolveFileUri(context, item, effectiveTree)
                } else null
                val clipItem = if (uri != null) {
                    ClipData.Item(item.path, null as Intent?, uri)
                } else {
                    ClipData.Item(item.path)
                }
                clipData.addItem(clipItem)
            }

            clipData
        } catch (_: Throwable) {
            createClipData(items)
        }
    }

    fun createClipData(items: List<FileItem>): ClipData {
        val pathsText = items.joinToString("\n") { it.path }
        return ClipData.newPlainText(CLIP_DATA_LABEL, pathsText)
    }

    fun extractItemsFromClipData(clipData: ClipData?): List<FileItem> {
        if (activeDraggedItems.isNotEmpty()) {
            return activeDraggedItems
        }
        if (lastDraggedItems.isNotEmpty()) {
            val items = lastDraggedItems
            lastDraggedItems = emptyList()
            return items
        }
        if (clipData == null || clipData.itemCount == 0) return emptyList()

        val seenPaths = mutableSetOf<String>()
        val result = mutableListOf<FileItem>()
        val fallbackTree = sourceTreeUri
        for (i in 0 until clipData.itemCount) {
            val item = clipData.getItemAt(i)
            val text = item.text?.toString()
            val itemUriStr = item.uri?.toString()
            val candidates = if (!text.isNullOrBlank()) {
                text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            } else if (!itemUriStr.isNullOrBlank()) {
                listOf(itemUriStr)
            } else {
                emptyList()
            }
            for (p in candidates) {
                if (!seenPaths.add(p)) continue
                val file = File(p)
                val isDir = file.isDirectory
                val name = file.name.ifEmpty { p.substringAfterLast('/') }
                val ext = if (isDir) "" else name.substringAfterLast('.', "")
                result.add(
                    FileItem(
                        id = p,
                        name = name,
                        path = p,
                        isDirectory = isDir,
                        size = if (isDir) 0L else try { file.length() } catch (_: Exception) { 0L },
                        extension = ext,
                        treeUri = fallbackTree ?: itemUriStr?.takeIf { it.startsWith("content://") }
                    )
                )
            }
        }
        return result
    }
}

/**
 * Windows 11 style floating drag shadow rendered natively by Android OS WindowManager
 * across all split-screen and multi-window application windows.
 */
class FileDragShadowBuilder(
    context: Context,
    private val items: List<FileItem>
) : View.DragShadowBuilder() {

    private val density = context.resources.displayMetrics.density
    private val width = (200 * density).toInt()
    private val height = (46 * density).toInt()

    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        outShadowSize.set(width, height)
        // Position touch point near top-left of the badge so cursor does not obscure drop target
        outShadowTouchPoint.set((24 * density).toInt(), (23 * density).toInt())
    }

    override fun onDrawShadow(canvas: Canvas) {
        val radius = 8f * density
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())

        // 1. Fluent card background (semi-transparent dark with soft rounded corners)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xF020242A.toInt() // 94% opacity dark slate
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, radius, radius, bgPaint)

        // 2. Windows 11 accent outline
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0078D4.toInt() // Windows 11 Fluent Blue
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }
        canvas.drawRoundRect(rect, radius, radius, borderPaint)

        // 3. Icon thumbnail
        val firstItem = items.firstOrNull()
        val isDir = firstItem?.isDirectory == true
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDir) 0xFFFFB900.toInt() else 0xFF2383E2.toInt() // Yellow folder / Blue file
            style = Paint.Style.FILL
        }
        val iconLeft = 10f * density
        val iconTop = 13f * density
        val iconSize = 20f * density
        val iconRect = RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
        canvas.drawRoundRect(iconRect, 4f * density, 4f * density, iconPaint)

        // 4. File / Folder Name Text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 12.5f * density
            isFakeBoldText = true
        }
        val name = firstItem?.name ?: "Item"
        val maxTextWidth = if (items.size > 1) width - (76f * density) else width - (40f * density)
        val truncatedName = TextUtils.ellipsize(name, TextPaint(textPaint), maxTextWidth, TextUtils.TruncateAt.END).toString()
        val textY = (height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(truncatedName, iconLeft + iconSize + (8f * density), textY, textPaint)

        // 5. Badge counter pill (+N) if multiple items dragged
        if (items.size > 1) {
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF0078D4.toInt()
                style = Paint.Style.FILL
            }
            val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                textSize = 11f * density
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            }
            val badgeText = "+${items.size}"
            val badgeWidth = 28f * density
            val badgeHeight = 20f * density
            val badgeRight = width - (10f * density)
            val badgeLeft = badgeRight - badgeWidth
            val badgeTop = (height - badgeHeight) / 2f
            val badgeRect = RectF(badgeLeft, badgeTop, badgeRight, badgeTop + badgeHeight)
            canvas.drawRoundRect(badgeRect, badgeHeight / 2f, badgeHeight / 2f, badgePaint)

            val badgeTextY = badgeTop + (badgeHeight / 2f) - ((badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f)
            canvas.drawText(badgeText, badgeRect.centerX(), badgeTextY, badgeTextPaint)
        }
    }
}
