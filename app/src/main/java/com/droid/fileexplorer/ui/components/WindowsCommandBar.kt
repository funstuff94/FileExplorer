package com.droid.fileexplorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.droid.fileexplorer.model.SortDirection
import com.droid.fileexplorer.model.SortField
import com.droid.fileexplorer.model.SortOption
import com.droid.fileexplorer.model.ViewMode
import com.droid.fileexplorer.theme.Win11Blue

@Composable
fun WindowsCommandBar(
    viewMode: ViewMode,
    sortOption: SortOption,
    hasSelection: Boolean,
    canPin: Boolean,
    isPinned: Boolean,
    canPaste: Boolean,
    showVideoThumbnails: Boolean = true,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onTogglePin: () -> Unit,
    onViewModeChange: (ViewMode) -> Unit,
    onSortChange: (SortOption) -> Unit,
    onToggleVideoThumbnails: () -> Unit = {},
    onNewFolder: () -> Unit,
    onNewFile: () -> Unit,
    onOpenNewWindow: () -> Unit = {},
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showNewMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showViewMenu by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            // New Dropdown Button
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { showNewMenu = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New",
                        tint = Win11Blue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "New",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showNewMenu,
                    onDismissRequest = { showNewMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Folder") },
                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                        onClick = {
                            showNewMenu = false
                            onNewFolder()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Text Document") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null) },
                        onClick = {
                            showNewMenu = false
                            onNewFile()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("New Window (Ctrl+N)") },
                        leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
                        onClick = {
                            showNewMenu = false
                            onOpenNewWindow()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))
            VerticalDivider(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.width(4.dp))

            // Cut Button
            CommandBarIcon(
                icon = Icons.Default.ContentCut,
                label = "Cut",
                enabled = hasSelection,
                onClick = onCut
            )

            // Copy Button
            CommandBarIcon(
                icon = Icons.Default.ContentCopy,
                label = "Copy",
                enabled = hasSelection,
                onClick = onCopy
            )

            // Paste Button
            CommandBarIcon(
                icon = Icons.Default.ContentPaste,
                label = "Paste",
                enabled = canPaste,
                tint = if (canPaste) Win11Blue else null,
                onClick = onPaste
            )

            // Rename Button
            CommandBarIcon(
                icon = Icons.Default.DriveFileRenameOutline,
                label = "Rename",
                enabled = hasSelection,
                onClick = onRename
            )

            // Share Button
            CommandBarIcon(
                icon = Icons.Default.Share,
                label = "Share",
                enabled = hasSelection,
                onClick = onShare
            )

            // Delete Button
            CommandBarIcon(
                icon = Icons.Default.Delete,
                label = "Delete",
                enabled = hasSelection,
                onClick = onDelete
            )

            // Pin / Unpin from Quick Access Button
            CommandBarIcon(
                icon = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                label = if (isPinned) "Unpin from Quick access" else "Pin to Quick access",
                enabled = canPin,
                tint = if (isPinned) Win11Blue else null,
                onClick = onTogglePin
            )

            Spacer(modifier = Modifier.width(4.dp))
            VerticalDivider(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.width(4.dp))

            // Sort Dropdown
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { showSortMenu = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Sort",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Sort", style = MaterialTheme.typography.bodyMedium)
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    SortMenuItem(
                        title = "Name",
                        selected = sortOption.field == SortField.NAME,
                        onClick = {
                            showSortMenu = false
                            onSortChange(sortOption.copy(field = SortField.NAME))
                        }
                    )
                    SortMenuItem(
                        title = "Date modified",
                        selected = sortOption.field == SortField.DATE_MODIFIED,
                        onClick = {
                            showSortMenu = false
                            onSortChange(sortOption.copy(field = SortField.DATE_MODIFIED))
                        }
                    )
                    SortMenuItem(
                        title = "Type",
                        selected = sortOption.field == SortField.TYPE,
                        onClick = {
                            showSortMenu = false
                            onSortChange(sortOption.copy(field = SortField.TYPE))
                        }
                    )
                    SortMenuItem(
                        title = "Size",
                        selected = sortOption.field == SortField.SIZE,
                        onClick = {
                            showSortMenu = false
                            onSortChange(sortOption.copy(field = SortField.SIZE))
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    SortMenuItem(
                        title = "Ascending",
                        selected = sortOption.direction == SortDirection.ASCENDING,
                        onClick = {
                            showSortMenu = false
                            onSortChange(sortOption.copy(direction = SortDirection.ASCENDING))
                        }
                    )
                    SortMenuItem(
                        title = "Descending",
                        selected = sortOption.direction == SortDirection.DESCENDING,
                        onClick = {
                            showSortMenu = false
                            onSortChange(sortOption.copy(direction = SortDirection.DESCENDING))
                        }
                    )
                }
            }

            // View Dropdown
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { showViewMenu = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (viewMode == ViewMode.DETAILS) Icons.Default.TableRows else Icons.Default.GridView,
                        contentDescription = "View",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "View", style = MaterialTheme.typography.bodyMedium)
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showViewMenu,
                    onDismissRequest = { showViewMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Extra large icons") },
                        leadingIcon = {
                            if (viewMode == ViewMode.EXTRA_LARGE) Icon(Icons.Default.Check, contentDescription = null, tint = Win11Blue)
                            else Icon(Icons.Default.GridView, contentDescription = null)
                        },
                        onClick = {
                            showViewMenu = false
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
                            showViewMenu = false
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
                            showViewMenu = false
                            onViewModeChange(ViewMode.DETAILS)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = { Text(if (showVideoThumbnails) "Hide video thumbnails" else "Show video thumbnails") },
                        leadingIcon = {
                            if (showVideoThumbnails) Icon(Icons.Default.Check, contentDescription = null, tint = Win11Blue)
                            else Icon(Icons.Default.VideocamOff, contentDescription = null)
                        },
                        onClick = {
                            showViewMenu = false
                            onToggleVideoThumbnails()
                        }
                    )
                }
            }

            VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp))

            // Video Thumbnails Toggle Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onToggleVideoThumbnails() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = if (showVideoThumbnails) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    contentDescription = if (showVideoThumbnails) "Hide video thumbnails" else "Show video thumbnails",
                    tint = if (showVideoThumbnails) Win11Blue else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Video preview",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (showVideoThumbnails) Win11Blue else MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Select All
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onSelectAll() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = "Select All",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Select all", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CommandBarIcon(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color? = null,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(34.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint ?: if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SortMenuItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(title) },
        leadingIcon = {
            if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = Win11Blue)
            else Box(modifier = Modifier.size(24.dp))
        },
        onClick = onClick
    )
}
