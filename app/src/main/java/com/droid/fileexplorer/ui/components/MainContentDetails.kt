package com.droid.fileexplorer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import android.view.View
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.platform.LocalView
import com.droid.fileexplorer.data.FileDragShadowBuilder
import com.droid.fileexplorer.data.SharedDragDropManager
import com.droid.fileexplorer.model.FileItem
import com.droid.fileexplorer.theme.Win11Blue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.platform.LocalDensity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainContentDetails(
    items: List<FileItem>,
    selectedPaths: Set<String>,
    currentPath: String = "",
    currentFolderName: String = "",
    currentTreeUri: String? = null,
    canPaste: Boolean,
    onItemClick: (item: FileItem, isCtrl: Boolean, isShift: Boolean) -> Unit,
    onItemDoubleClick: (FileItem) -> Unit,
    onItemSelect: (FileItem) -> Unit,
    onSelectionChange: (Set<String>) -> Unit,
    onMoveItemsToFolder: (List<FileItem>, FileItem) -> Unit = { _, _ -> },
    onMoveItemsToDirectory: (List<FileItem>, String, String, String?) -> Unit = { _, _, _, _ -> },
    onOpenInNewTab: (FileItem) -> Unit,
    onOpenInNewWindow: (FileItem) -> Unit,
    onCut: (FileItem) -> Unit,
    onCopy: (FileItem) -> Unit,
    onPasteInto: (FileItem) -> Unit,
    onBackgroundPaste: () -> Unit,
    onPin: (FileItem) -> Unit,
    onCreateShortcut: (FileItem) -> Unit,
    onPinToDesktop: (FileItem) -> Unit,
    onRename: (FileItem) -> Unit,
    onDelete: (FileItem) -> Unit,
    onShare: (FileItem) -> Unit,
    onProperties: (FileItem) -> Unit,
    onNewFolder: () -> Unit = {},
    onNewFile: () -> Unit = {},
    onNewFolderIn: (FileItem) -> Unit = {},
    onNewFileIn: (FileItem) -> Unit = {},
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showBackgroundMenu by remember { mutableStateOf(false) }
    var backgroundMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    val density = LocalDensity.current

    val currentItems by rememberUpdatedState(items)
    val currentSelectedPaths by rememberUpdatedState(selectedPaths)
    val currentOnSelectionChange by rememberUpdatedState(onSelectionChange)
    val currentOnMoveItemsToFolder by rememberUpdatedState(onMoveItemsToFolder)
    val currentOnMoveItemsToDirectory by rememberUpdatedState(onMoveItemsToDirectory)
    val view = LocalView.current

    // Marquee selection & File Drag-and-Drop state
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    var isDraggingSelection by remember { mutableStateOf(false) }
    var containerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var lastDragEndTime by remember { mutableStateOf(0L) }
    val itemBounds = remember { mutableStateMapOf<String, Rect>() }
    val iconBounds = remember { mutableStateMapOf<String, Rect>() }

    var isDragOverWindow by remember { mutableStateOf(false) }
    var dragHoveredFolder by remember { mutableStateOf<FileItem?>(null) }
    val isDraggingActive by SharedDragDropManager.isDragging.collectAsState()

    androidx.compose.runtime.LaunchedEffect(items.firstOrNull()?.path?.substringBeforeLast('/')) {
        itemBounds.clear()
        iconBounds.clear()
    }

    val dndTarget = remember {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {}

            override fun onEntered(event: DragAndDropEvent) {
                isDragOverWindow = true
            }

            override fun onMoved(event: DragAndDropEvent) {
                val androidEvent = event.toAndroidDragEvent()
                val posInRoot = Offset(androidEvent.x, androidEvent.y)
                val localPos = containerCoordinates?.let { coords ->
                    if (coords.isAttached) coords.windowToLocal(posInRoot) else posInRoot
                } ?: posInRoot

                val activeItems = SharedDragDropManager.activeDraggedItems
                val draggedPaths = activeItems.map { it.path }.toSet()

                dragHoveredFolder = currentItems.firstOrNull { candidate ->
                    candidate.isDirectory &&
                        !draggedPaths.contains(candidate.path) &&
                        !activeItems.any { dragged ->
                            dragged.isDirectory && candidate.path.startsWith(dragged.path.trimEnd('/') + "/")
                        } &&
                        itemBounds[candidate.path]?.contains(localPos) == true
                }
            }

            override fun onExited(event: DragAndDropEvent) {
                isDragOverWindow = false
                dragHoveredFolder = null
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                val targetFolder = dragHoveredFolder
                val itemsToMove = SharedDragDropManager.activeDraggedItems.ifEmpty {
                    SharedDragDropManager.extractItemsFromClipData(event.toAndroidDragEvent().clipData)
                }

                if (itemsToMove.isNotEmpty()) {
                    if (targetFolder != null) {
                        currentOnMoveItemsToFolder(itemsToMove, targetFolder)
                    } else {
                        currentOnMoveItemsToDirectory(itemsToMove, currentPath, currentFolderName, currentTreeUri)
                    }
                }

                isDragOverWindow = false
                dragHoveredFolder = null
                SharedDragDropManager.endDrag()
                return true
            }

            override fun onEnded(event: DragAndDropEvent) {
                isDragOverWindow = false
                dragHoveredFolder = null
                SharedDragDropManager.endDrag()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .dragAndDropTarget(
                shouldStartDragAndDrop = { true },
                target = dndTarget
            )
            .onGloballyPositioned { containerCoordinates = it }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragStart = offset
                        dragCurrent = offset

                        // Windows 11 Behavior:
                        // 1. If user clicks and drags ANY already selected item (by icon, text, or row), drag all selected items!
                        // 2. If user specifically drags an unselected item's icon, select and drag that item.
                        // 3. Otherwise (drag starts in empty background or unselected item space) -> Marquee Selection!
                        val hitSelectedItem = currentItems.firstOrNull { item ->
                            currentSelectedPaths.contains(item.path) && itemBounds[item.path]?.contains(offset) == true
                        }
                        val hitAnyIcon = currentItems.firstOrNull { item ->
                            iconBounds[item.path]?.contains(offset) == true
                        }

                        val toDrag = when {
                            hitSelectedItem != null -> {
                                currentItems.filter { currentSelectedPaths.contains(it.path) }
                            }
                            hitAnyIcon != null -> {
                                currentOnSelectionChange(setOf(hitAnyIcon.path))
                                listOf(hitAnyIcon)
                            }
                            else -> null
                        }

                        if (toDrag != null && toDrag.isNotEmpty()) {
                            SharedDragDropManager.startDrag(toDrag, currentPath, currentTreeUri)
                            val clipData = SharedDragDropManager.createClipData(view.context, toDrag, currentTreeUri)
                            val shadow = FileDragShadowBuilder(view.context, toDrag)
                            val flags = View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ
                            view.startDragAndDrop(clipData, shadow, toDrag, flags)
                            isDraggingSelection = false
                        } else {
                            isDraggingSelection = false
                        }
                    },
                    onDrag = { change, dragAmount ->
                        val start = dragStart ?: return@detectDragGestures
                        val current = (dragCurrent ?: start) + dragAmount
                        dragCurrent = current

                        if (!SharedDragDropManager.isDragging.value) {
                            val distance = (current - start).getDistance()
                            if (distance > 8f) {
                                isDraggingSelection = true
                                change.consume()
                                val left = minOf(start.x, current.x)
                                val top = minOf(start.y, current.y)
                                val right = maxOf(start.x, current.x)
                                val bottom = maxOf(start.y, current.y)
                                val selRect = Rect(left, top, right, bottom)

                                val matching = itemBounds.filter { (_, rect) ->
                                    selRect.overlaps(rect)
                                }.keys.toSet()

                                currentOnSelectionChange(matching)
                            }
                        }
                    },
                    onDragEnd = {
                        lastDragEndTime = System.currentTimeMillis()
                        isDraggingSelection = false
                        dragStart = null
                        dragCurrent = null
                    },
                    onDragCancel = {
                        lastDragEndTime = System.currentTimeMillis()
                        isDraggingSelection = false
                        dragStart = null
                        dragCurrent = null
                    }
                )
            }
            .contextClickable(
                onClick = {
                    if (System.currentTimeMillis() - lastDragEndTime > 300L) {
                        onSelectionChange(emptySet())
                    }
                },
                onContextMenuWithOffset = { offset ->
                    backgroundMenuOffset = with(density) {
                        DpOffset(offset.x.toDp(), offset.y.toDp())
                    }
                    showBackgroundMenu = true
                }
            )
    ) {
        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FolderOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "This folder is empty",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val dateFormat = remember { SimpleDateFormat("MM/dd/yyyy h:mm a", Locale.getDefault()) }

            Column(modifier = Modifier.fillMaxSize()) {
                // Table Header
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp)
                    ) {
                        Spacer(modifier = Modifier.width(32.dp)) // Checkbox spacing
                        Text(
                            text = "Name",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(2.5f)
                        )
                        Text(
                            text = "Date modified",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1.5f)
                        )
                        Text(
                            text = "Type",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Size",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(32.dp)) // Options spacing
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .contextClickable(
                            onClick = {
                                if (System.currentTimeMillis() - lastDragEndTime > 300L) {
                                    onSelectionChange(emptySet())
                                }
                            },
                            onContextMenuWithOffset = { offset ->
                                backgroundMenuOffset = with(density) {
                                    DpOffset(offset.x.toDp(), offset.y.toDp())
                                }
                                showBackgroundMenu = true
                            }
                        )
                ) {
                    items(items, key = { it.id }) { item ->
                        val isSelected = selectedPaths.contains(item.path)
                        val isBeingDragged = isDraggingActive && SharedDragDropManager.activeDraggedItems.any { it.path == item.path }
                        val isDropTarget = dragHoveredFolder?.path == item.path
                        var showContextMenu by remember { mutableStateOf(false) }

                        val rowColor = when {
                            isDropTarget -> Win11Blue.copy(alpha = 0.22f)
                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                            else -> Color.Transparent
                        }
                        val rowAlpha = if (isBeingDragged) 0.45f else 1f

                        Box(
                            modifier = Modifier.onGloballyPositioned { coords ->
                                containerCoordinates?.let { parent ->
                                    if (parent.isAttached && coords.isAttached) {
                                        itemBounds[item.path] = parent.localBoundingBoxOf(coords)
                                    }
                                }
                            }
                        ) {
                            Surface(
                                color = rowColor,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .alpha(rowAlpha)
                                    .border(
                                        width = if (isDropTarget) 1.5.dp else 0.dp,
                                        color = if (isDropTarget) Win11Blue else Color.Transparent,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .contextClickable(
                                        onClickWithModifiers = { isCtrl, isShift -> onItemClick(item, isCtrl, isShift) },
                                        onDoubleClick = { onItemDoubleClick(item) },
                                        onContextMenu = { showContextMenu = true }
                                    )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp)
                                ) {
                                    // Checkbox with generous touch/click target
                                    IconButton(
                                        onClick = { onItemSelect(item) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) Win11Blue else Color.Transparent)
                                                .border(
                                                    1.5.dp,
                                                    if (isSelected) Win11Blue else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Name Column with icon
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(2.5f)
                                    ) {
                                        Box(
                                            modifier = Modifier.onGloballyPositioned { coords ->
                                                containerCoordinates?.let { parent ->
                                                    if (parent.isAttached && coords.isAttached) {
                                                        iconBounds[item.path] = parent.localBoundingBoxOf(coords)
                                                    }
                                                }
                                            }
                                        ) {
                                            FileTypeIcon(item = item, size = 22.dp)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (item.isPinned) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.PushPin,
                                                contentDescription = "Pinned",
                                                tint = Win11Blue,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                        if (isDropTarget) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                color = Win11Blue,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "Drop to move here",
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Date modified
                                    Text(
                                        text = if (item.lastModified > 0) dateFormat.format(Date(item.lastModified)) else "-",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1.5f)
                                    )

                                    // Type
                                    Text(
                                        text = if (item.isShortcut) "Shortcut" else if (item.isDirectory) "File folder" else item.category.name.lowercase().replaceFirstChar { it.uppercase() } + " File",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )

                                    // Size
                                    Text(
                                        text = item.formattedSize,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )

                                    // 3-Dots Button on the row
                                    IconButton(
                                        onClick = { showContextMenu = true },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "More Options",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            // Context Menu
                            DropdownMenu(
                                expanded = showContextMenu,
                                onDismissRequest = { showContextMenu = false }
                            ) {
                                val isApk = !item.isDirectory && (item.extension.equals("apk", ignoreCase = true) || item.mimeType == "application/vnd.android.package-archive")
                                DropdownMenuItem(
                                    text = { Text(if (item.isDirectory) "Open Folder" else if (isApk) "Install" else "Open") },
                                    onClick = {
                                        showContextMenu = false
                                        onItemDoubleClick(item)
                                    }
                                )

                                if (item.isDirectory || (item.isShortcut && item.shortcutTargetIsDirectory)) {
                                    DropdownMenuItem(
                                        text = { Text("Open in new tab") },
                                        leadingIcon = { Icon(Icons.Default.Tab, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            showContextMenu = false
                                            onOpenInNewTab(item)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Open in new window") },
                                        leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            showContextMenu = false
                                            onOpenInNewWindow(item)
                                        }
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                DropdownMenuItem(
                                    text = { Text("Cut") },
                                    leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        showContextMenu = false
                                        onCut(item)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy") },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        showContextMenu = false
                                        onCopy(item)
                                    }
                                )

                                if (item.isDirectory && canPaste) {
                                    DropdownMenuItem(
                                        text = { Text("Paste into folder") },
                                        leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null, tint = Win11Blue, modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            showContextMenu = false
                                            onPasteInto(item)
                                        }
                                    )
                                }

                                if (item.isDirectory) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    DropdownMenuItem(
                                        text = { Text("New Folder") },
                                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = Win11Blue, modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            showContextMenu = false
                                            onNewFolderIn(item)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("New Text Document") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null, tint = Win11Blue, modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            showContextMenu = false
                                            onNewFileIn(item)
                                        }
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                // Create shortcut in current folder (Windows behavior)
                                DropdownMenuItem(
                                    text = { Text("Create shortcut") },
                                    leadingIcon = { Icon(Icons.Default.NorthEast, contentDescription = null, tint = Win11Blue, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        showContextMenu = false
                                        onCreateShortcut(item)
                                    }
                                )

                                // Pin shortcut to Android home screen
                                DropdownMenuItem(
                                    text = { Text("Pin shortcut to Home screen") },
                                    leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        showContextMenu = false
                                        onPinToDesktop(item)
                                    }
                                )

                                if (item.isDirectory) {
                                    DropdownMenuItem(
                                        text = { Text(if (item.isPinned) "Unpin from Quick access" else "Pin to Quick access") },
                                        leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            showContextMenu = false
                                            onPin(item)
                                        }
                                    )
                                }

                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        showContextMenu = false
                                        onRename(item)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        showContextMenu = false
                                        onShare(item)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        showContextMenu = false
                                        onDelete(item)
                                    }
                                )

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                DropdownMenuItem(
                                    text = { Text("Properties") },
                                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        showContextMenu = false
                                        onProperties(item)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Marquee Selection Rectangle Overlay
        if (isDraggingSelection && dragStart != null && dragCurrent != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val start = dragStart!!
                val current = dragCurrent!!
                val left = minOf(start.x, current.x)
                val top = minOf(start.y, current.y)
                val width = maxOf(start.x, current.x) - left
                val height = maxOf(start.y, current.y) - top
                val size = Size(width, height)
                val topLeft = Offset(left, top)

                drawRect(
                    color = Win11Blue.copy(alpha = 0.22f),
                    topLeft = topLeft,
                    size = size
                )
                drawRect(
                    color = Win11Blue.copy(alpha = 0.85f),
                    topLeft = topLeft,
                    size = size,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        // Drop highlight overlay when dragging over this directory (e.g. from another window or different directory)
        if (isDragOverWindow && dragHoveredFolder == null && SharedDragDropManager.sourceDirPath != currentPath.trimEnd('/')) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .border(2.dp, Win11Blue.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                    .background(Win11Blue.copy(alpha = 0.08f))
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                    border = BorderStroke(1.dp, Win11Blue.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                            contentDescription = null,
                            tint = Win11Blue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val count = SharedDragDropManager.activeDraggedItems.size
                        val label = if (count > 1) {
                            "Move $count items to '$currentFolderName'"
                        } else {
                            "Move to '$currentFolderName'"
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Background Context Menu
        DropdownMenu(
            expanded = showBackgroundMenu,
            onDismissRequest = { showBackgroundMenu = false },
            offset = backgroundMenuOffset
        ) {
            DropdownMenuItem(
                text = { Text("New Folder") },
                leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = Win11Blue) },
                onClick = {
                    showBackgroundMenu = false
                    onNewFolder()
                }
            )
            DropdownMenuItem(
                text = { Text("New Text Document") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null, tint = Win11Blue) },
                onClick = {
                    showBackgroundMenu = false
                    onNewFile()
                }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text("Paste") },
                leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
                enabled = canPaste,
                onClick = {
                    showBackgroundMenu = false
                    onBackgroundPaste()
                }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text("Refresh") },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                onClick = {
                    showBackgroundMenu = false
                    onRefresh()
                }
            )
        }
    }
}
