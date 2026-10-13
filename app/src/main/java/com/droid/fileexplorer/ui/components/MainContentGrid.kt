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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.platform.LocalDensity
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
import com.droid.fileexplorer.model.ViewMode
import com.droid.fileexplorer.theme.Win11Blue
import kotlin.math.roundToInt

@Composable
fun MainContentGrid(
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
    showVideoThumbnails: Boolean = true,
    onToggleVideoThumbnails: () -> Unit = {},
    viewMode: ViewMode = ViewMode.GRID,
    onViewModeChange: (ViewMode) -> Unit = {},
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
                detectPrimaryDragGestures(
                    onDragStart = { offset ->
                        val containerWidth = containerCoordinates?.size?.width ?: Int.MAX_VALUE
                        val scrollbarZonePx = with(density) { 24.dp.toPx() }
                        if (offset.x >= containerWidth - scrollbarZonePx) {
                            return@detectPrimaryDragGestures
                        }

                        dragStart = offset
                        dragCurrent = offset

                        // Windows 11 Behavior:
                        // 1. If user clicks and drags ANY already selected item (by icon, text, or card), drag all selected items!
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
                        val start = dragStart ?: return@detectPrimaryDragGestures
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
            val gridState = rememberLazyGridState()

            androidx.compose.runtime.LaunchedEffect(currentPath) {
                gridState.scrollToItem(0)
            }

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = if (viewMode == ViewMode.EXTRA_LARGE) 180.dp else 120.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(if (viewMode == ViewMode.EXTRA_LARGE) 12.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(if (viewMode == ViewMode.EXTRA_LARGE) 12.dp else 8.dp),
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

                    GridItemCard(
                        item = item,
                        isSelected = isSelected,
                        isBeingDragged = isBeingDragged,
                        isDropTarget = isDropTarget,
                        canPaste = canPaste,
                        viewMode = viewMode,
                        onPositioned = { coords ->
                            containerCoordinates?.let { parent ->
                                if (parent.isAttached && coords.isAttached) {
                                    itemBounds[item.path] = parent.localBoundingBoxOf(coords)
                                }
                            }
                        },
                        onIconPositioned = { coords ->
                            containerCoordinates?.let { parent ->
                                if (parent.isAttached && coords.isAttached) {
                                    iconBounds[item.path] = parent.localBoundingBoxOf(coords)
                                }
                            }
                        },
                        onClick = { isCtrl, isShift -> onItemClick(item, isCtrl, isShift) },
                        onDoubleClick = { onItemDoubleClick(item) },
                        onSelectToggle = { onItemSelect(item) },
                        onOpenInNewTab = { onOpenInNewTab(item) },
                        onOpenInNewWindow = { onOpenInNewWindow(item) },
                        onCut = { onCut(item) },
                        onCopy = { onCopy(item) },
                        onPasteInto = { onPasteInto(item) },
                        onPin = { onPin(item) },
                        onCreateShortcut = { onCreateShortcut(item) },
                        onPinToDesktop = { onPinToDesktop(item) },
                        onRename = { onRename(item) },
                        onDelete = { onDelete(item) },
                        onShare = { onShare(item) },
                        onProperties = { onProperties(item) },
                        showVideoThumbnails = showVideoThumbnails,
                        onToggleVideoThumbnails = onToggleVideoThumbnails,
                        onContextMenu = {
                            if (!selectedPaths.contains(item.path)) {
                                onSelectionChange(setOf(item.path))
                            }
                        },
                        onNewFolderIn = onNewFolderIn,
                        onNewFileIn = onNewFileIn
                    )
                }
            }

            val gridScrollbarAdapter = rememberLazyGridScrollbarAdapter(gridState)
            VerticalScrollbarSlider(
                adapter = gridScrollbarAdapter,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
            )
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

        // Background Context Menu (right click on empty space)
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
                text = { Text(if (showVideoThumbnails) "Hide video thumbnails" else "Show video thumbnails") },
                leadingIcon = {
                    Icon(
                        imageVector = if (showVideoThumbnails) Icons.Default.VideocamOff else Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Win11Blue,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    showBackgroundMenu = false
                    onToggleVideoThumbnails()
                }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text("Extra large icons") },
                leadingIcon = {
                    if (viewMode == ViewMode.EXTRA_LARGE) Icon(Icons.Default.Check, contentDescription = null, tint = Win11Blue)
                    else Icon(Icons.Default.GridView, contentDescription = null)
                },
                onClick = {
                    showBackgroundMenu = false
                    onViewModeChange(ViewMode.EXTRA_LARGE)
                }
            )
            DropdownMenuItem(
                text = { Text("Large icons") },
                leadingIcon = {
                    if (viewMode == ViewMode.GRID) Icon(Icons.Default.Check, contentDescription = null, tint = Win11Blue)
                    else Icon(Icons.Default.GridView, contentDescription = null)
                },
                onClick = {
                    showBackgroundMenu = false
                    onViewModeChange(ViewMode.GRID)
                }
            )
            DropdownMenuItem(
                text = { Text("Details") },
                leadingIcon = {
                    if (viewMode == ViewMode.DETAILS) Icon(Icons.Default.Check, contentDescription = null, tint = Win11Blue)
                    else Icon(Icons.Default.TableRows, contentDescription = null)
                },
                onClick = {
                    showBackgroundMenu = false
                    onViewModeChange(ViewMode.DETAILS)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridItemCard(
    item: FileItem,
    isSelected: Boolean,
    isBeingDragged: Boolean = false,
    isDropTarget: Boolean = false,
    canPaste: Boolean,
    onPositioned: (LayoutCoordinates) -> Unit,
    onIconPositioned: (LayoutCoordinates) -> Unit,
    onClick: (isCtrl: Boolean, isShift: Boolean) -> Unit,
    onDoubleClick: () -> Unit,
    onSelectToggle: () -> Unit,
    onOpenInNewTab: () -> Unit,
    onOpenInNewWindow: () -> Unit,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onPasteInto: () -> Unit,
    onPin: () -> Unit,
    onCreateShortcut: () -> Unit,
    onPinToDesktop: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onProperties: () -> Unit,
    showVideoThumbnails: Boolean = true,
    onToggleVideoThumbnails: () -> Unit = {},
    viewMode: ViewMode = ViewMode.GRID,
    onContextMenu: (() -> Unit)? = null,
    onNewFolderIn: (FileItem) -> Unit = {},
    onNewFileIn: (FileItem) -> Unit = {}
) {
    var showContextMenu by remember { mutableStateOf(false) }

    val isExtraLarge = viewMode == ViewMode.EXTRA_LARGE
    val cardHeight = if (isExtraLarge) 205.dp else 130.dp
    val iconSize = if (isExtraLarge) 96.dp else 44.dp

    val cardColor = when {
        isDropTarget -> Win11Blue.copy(alpha = 0.22f)
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        isDropTarget -> Win11Blue
        isSelected -> Win11Blue
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }
    val borderWidth = if (isDropTarget) 2.dp else 1.dp
    val cardAlpha = if (isBeingDragged) 0.45f else 1f

    Box(modifier = Modifier.onGloballyPositioned(onPositioned)) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = cardColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .alpha(cardAlpha)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = borderWidth,
                    color = borderColor,
                    shape = RoundedCornerShape(8.dp)
                )
                .contextClickable(
                    onClickWithModifiers = onClick,
                    onDoubleClick = onDoubleClick,
                    onContextMenu = {
                        onContextMenu?.invoke()
                        showContextMenu = true
                    }
                )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isExtraLarge) 8.dp else 6.dp)
            ) {
                // Top Badges Row: Checkbox, Pin, and Options (3-dots)
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Selection Checkbox with generous touch/click target
                    IconButton(
                        onClick = onSelectToggle,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
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
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.isPinned) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                tint = Win11Blue,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // 3-Dots Button on the card for 1-click options
                        IconButton(
                            onClick = { showContextMenu = true },
                            modifier = Modifier.size(22.dp)
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

                Spacer(modifier = Modifier.height(if (isExtraLarge) 4.dp else 2.dp))

                // File/Folder Icon
                Box(modifier = Modifier.onGloballyPositioned(onIconPositioned)) {
                    FileTypeIcon(item = item, size = iconSize, showVideoThumbnail = showVideoThumbnails)
                }

                Spacer(modifier = Modifier.height(if (isExtraLarge) 8.dp else 6.dp))

                // File Name
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = if (isExtraLarge) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                // Subtitle (Item count, Shortcut, Size, or Drop hint)
                Text(
                    text = when {
                        isDropTarget -> "Drop to move here"
                        item.isShortcut -> "Shortcut • ${item.formattedSize}"
                        else -> item.formattedSize
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDropTarget) Win11Blue else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isDropTarget) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
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
                    onDoubleClick()
                }
            )

            if (item.isDirectory || (item.isShortcut && item.shortcutTargetIsDirectory)) {
                DropdownMenuItem(
                    text = { Text("Open in new tab") },
                    leadingIcon = { Icon(Icons.Default.Tab, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        showContextMenu = false
                        onOpenInNewTab()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Open in new window") },
                    leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        showContextMenu = false
                        onOpenInNewWindow()
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            DropdownMenuItem(
                text = { Text("Cut") },
                leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    showContextMenu = false
                    onCut()
                }
            )
            DropdownMenuItem(
                text = { Text("Copy") },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    showContextMenu = false
                    onCopy()
                }
            )

            if (item.isDirectory && canPaste) {
                DropdownMenuItem(
                    text = { Text("Paste into folder") },
                    leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null, tint = Win11Blue, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        showContextMenu = false
                        onPasteInto()
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
                    onCreateShortcut()
                }
            )

            // Pin shortcut to Android home screen
            DropdownMenuItem(
                text = { Text("Pin shortcut to Home screen") },
                leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    showContextMenu = false
                    onPinToDesktop()
                }
            )

            if (item.isDirectory) {
                DropdownMenuItem(
                    text = { Text(if (item.isPinned) "Unpin from Quick access" else "Pin to Quick access") },
                    leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        showContextMenu = false
                        onPin()
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    showContextMenu = false
                    onRename()
                }
            )
            DropdownMenuItem(
                text = { Text("Share") },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    showContextMenu = false
                    onShare()
                }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    showContextMenu = false
                    onDelete()
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            DropdownMenuItem(
                text = { Text(if (showVideoThumbnails) "Hide video thumbnails" else "Show video thumbnails") },
                leadingIcon = {
                    Icon(
                        imageVector = if (showVideoThumbnails) Icons.Default.VideocamOff else Icons.Default.Videocam,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    showContextMenu = false
                    onToggleVideoThumbnails()
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            DropdownMenuItem(
                text = { Text("Properties") },
                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    showContextMenu = false
                    onProperties()
                }
            )
        }
    }
}
