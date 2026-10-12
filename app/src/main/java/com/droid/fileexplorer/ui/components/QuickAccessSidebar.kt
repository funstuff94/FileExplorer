package com.droid.fileexplorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droid.fileexplorer.model.DriveItem
import com.droid.fileexplorer.model.DriveType
import com.droid.fileexplorer.model.FileItem
import com.droid.fileexplorer.model.QuickAccessItem
import com.droid.fileexplorer.model.QuickAccessType
import com.droid.fileexplorer.theme.Win11Blue
import com.droid.fileexplorer.theme.Win11CloudBlue
import com.droid.fileexplorer.theme.Win11DriveBlue
import com.droid.fileexplorer.theme.Win11FolderYellow

@Composable
fun QuickAccessSidebar(
    quickAccessItems: List<QuickAccessItem>,
    drives: List<DriveItem>,
    cloudDrives: List<DriveItem>,
    currentPath: String,
    currentTreeUri: String?,
    onNavigate: (String, String?) -> Unit,
    onOpenInNewTab: (String, String?) -> Unit,
    onOpenInNewWindow: (String, String?) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onPinCurrentFolder: () -> Unit,
    onUnpin: (String) -> Unit,
    onAddCloudDrive: () -> Unit,
    onRemoveCloudDrive: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(260.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            )
            .verticalScroll(scrollState)
            .padding(vertical = 12.dp, horizontal = 8.dp)
    ) {
        // Section: Quick Access
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
        ) {
            SectionHeader(title = "Quick access", icon = Icons.Default.FolderSpecial)
            IconButton(
                onClick = onPinCurrentFolder,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Pin current folder to Quick access",
                    tint = Win11Blue,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        if (quickAccessItems.isEmpty()) {
            Text(
                text = "No pinned folders. Click + to pin the current folder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        } else {
            quickAccessItems.forEachIndexed { index, item ->
                val isSelected = (currentPath == item.path || currentPath.trimEnd('/') == item.path.trimEnd('/')) && currentTreeUri == item.treeUri
                SidebarQuickAccessRow(
                    item = item,
                    icon = getQuickAccessIcon(item.title, item.type),
                    iconTint = if (item.type == QuickAccessType.CLOUD) Win11CloudBlue else Win11FolderYellow,
                    isSelected = isSelected,
                    canMoveUp = index > 0,
                    canMoveDown = index < quickAccessItems.lastIndex,
                    onClick = { onNavigate(item.path, item.treeUri) },
                    onOpenInNewTab = { onOpenInNewTab(item.path, item.treeUri) },
                    onOpenInNewWindow = { onOpenInNewWindow(item.path, item.treeUri) },
                    onMoveUp = { onMoveUp(item.path) },
                    onMoveDown = { onMoveDown(item.path) },
                    onUnpin = { onUnpin(item.path) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(12.dp))

        // Section: This PC / Drives
        SectionHeader(title = "This PC", icon = Icons.Default.Computer)
        Spacer(modifier = Modifier.height(4.dp))

        drives.forEach { drive ->
            val isSelected = if (drive.treeUri != null) {
                currentTreeUri == drive.treeUri
            } else {
                currentTreeUri == null && (currentPath == drive.path || currentPath.trimEnd('/') == drive.path.trimEnd('/'))
            }
            DriveItemRow(
                drive = drive,
                isSelected = isSelected,
                onClick = { onNavigate(drive.path, drive.treeUri) },
                onOpenInNewTab = { onOpenInNewTab(drive.path, drive.treeUri) },
                onOpenInNewWindow = { onOpenInNewWindow(drive.path, drive.treeUri) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(12.dp))

        // Section: Cloud & Network Drives
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
        ) {
            SectionHeader(title = "Cloud Drives", icon = Icons.Default.Cloud)
            IconButton(
                onClick = onAddCloudDrive,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddCircleOutline,
                    contentDescription = "Add Cloud Drive",
                    tint = Win11Blue,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        if (cloudDrives.isEmpty()) {
            Surface(
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onAddCloudDrive() }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = null,
                        tint = Win11CloudBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "+ Connect Google Drive / Cloud",
                        style = MaterialTheme.typography.bodySmall,
                        color = Win11Blue,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            cloudDrives.forEach { drive ->
                val isSelected = currentTreeUri == drive.treeUri
                DriveCloudRow(
                    drive = drive,
                    isSelected = isSelected,
                    onClick = { onNavigate(drive.name, drive.treeUri) },
                    onOpenInNewTab = { onOpenInNewTab(drive.name, drive.treeUri) },
                    onOpenInNewWindow = { onOpenInNewWindow(drive.name, drive.treeUri) },
                    onRemove = { drive.treeUri?.let { onRemoveCloudDrive(it) } }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun SidebarQuickAccessRow(
    item: QuickAccessItem,
    icon: ImageVector,
    iconTint: Color,
    isSelected: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onOpenInNewTab: () -> Unit,
    onOpenInNewWindow: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onUnpin: () -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }

    Box {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp)
                .clip(RoundedCornerShape(6.dp))
                .contextClickable(
                    onClick = onClick,
                    onContextMenu = { showContextMenu = true }
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Move Up
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Move Up",
                        tint = if (canMoveUp) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        modifier = Modifier.size(12.dp)
                    )
                }

                // Move Down
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Move Down",
                        tint = if (canMoveDown) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        modifier = Modifier.size(12.dp)
                    )
                }

                // Unpin button
                IconButton(
                    onClick = onUnpin,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Unpin from Quick access",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        // Context Menu
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Open") },
                onClick = {
                    showContextMenu = false
                    onClick()
                }
            )
            DropdownMenuItem(
                text = { Text("Open in new tab") },
                leadingIcon = { Icon(Icons.Default.Tab, contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    showContextMenu = false
                    onOpenInNewTab()
                }
            )
            DropdownMenuItem(
                text = { Text("Open in new window") },
                leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    showContextMenu = false
                    onOpenInNewWindow()
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            DropdownMenuItem(
                text = { Text("Move up") },
                leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp)) },
                enabled = canMoveUp,
                onClick = {
                    showContextMenu = false
                    onMoveUp()
                }
            )
            DropdownMenuItem(
                text = { Text("Move down") },
                leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp)) },
                enabled = canMoveDown,
                onClick = {
                    showContextMenu = false
                    onMoveDown()
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            DropdownMenuItem(
                text = { Text("Unpin from Quick access") },
                leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    showContextMenu = false
                    onUnpin()
                }
            )
        }
    }
}

@Composable
private fun DriveCloudRow(
    drive: DriveItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onOpenInNewTab: () -> Unit,
    onOpenInNewWindow: () -> Unit,
    onRemove: () -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }

    Box {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp)
                .clip(RoundedCornerShape(6.dp))
                .contextClickable(
                    onClick = onClick,
                    onContextMenu = { showContextMenu = true }
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = Win11CloudBlue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = drive.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove Cloud Drive",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Open") },
                onClick = {
                    showContextMenu = false
                    onClick()
                }
            )
            DropdownMenuItem(
                text = { Text("Open in new tab") },
                leadingIcon = { Icon(Icons.Default.Tab, contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    showContextMenu = false
                    onOpenInNewTab()
                }
            )
            DropdownMenuItem(
                text = { Text("Open in new window") },
                leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    showContextMenu = false
                    onOpenInNewWindow()
                }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text("Disconnect Cloud Drive") },
                leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    showContextMenu = false
                    onRemove()
                }
            )
        }
    }
}

@Composable
private fun DriveItemRow(
    drive: DriveItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onOpenInNewTab: () -> Unit,
    onOpenInNewWindow: () -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }

    val icon = when (drive.type) {
        DriveType.LINUX_SHARED -> Icons.Default.Terminal
        DriveType.SD_CARD -> Icons.Default.SdCard
        DriveType.USB -> Icons.Default.Usb
        DriveType.CLOUD -> Icons.Default.Cloud
        DriveType.INTERNAL -> Icons.Default.Computer
        DriveType.SMARTPHONE -> Icons.Default.Smartphone
    }

    val iconTint = when (drive.type) {
        DriveType.LINUX_SHARED -> Color(0xFF008272)
        DriveType.SD_CARD -> Color(0xFF8E562E)
        DriveType.USB -> Color(0xFF0078D4)
        DriveType.CLOUD -> Win11CloudBlue
        DriveType.INTERNAL -> Win11DriveBlue
        DriveType.SMARTPHONE -> Color(0xFF107C41)
    }

    Box {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(6.dp))
                .contextClickable(
                    onClick = onClick,
                    onContextMenu = { showContextMenu = true }
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = drive.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (drive.totalBytes > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val used = drive.totalBytes - drive.freeBytes
                    val progress = (used.toFloat() / drive.totalBytes.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Win11Blue,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${FileItem.formatByteSize(drive.freeBytes)} free of ${FileItem.formatByteSize(drive.totalBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Open") },
                onClick = {
                    showContextMenu = false
                    onClick()
                }
            )
            DropdownMenuItem(
                text = { Text("Open in new tab") },
                leadingIcon = { Icon(Icons.Default.Tab, contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    showContextMenu = false
                    onOpenInNewTab()
                }
            )
            DropdownMenuItem(
                text = { Text("Open in new window") },
                leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    showContextMenu = false
                    onOpenInNewWindow()
                }
            )
        }
    }
}

private fun getQuickAccessIcon(title: String, type: QuickAccessType): ImageVector {
    return when {
        type == QuickAccessType.CLOUD -> Icons.Default.Cloud
        title.contains("Download", ignoreCase = true) -> Icons.Default.Download
        title.contains("Document", ignoreCase = true) -> Icons.Default.Description
        title.contains("Picture", ignoreCase = true) || title.contains("DCIM", ignoreCase = true) -> Icons.Default.Image
        title.contains("Music", ignoreCase = true) -> Icons.Default.Audiotrack
        title.contains("Movie", ignoreCase = true) || title.contains("Video", ignoreCase = true) -> Icons.Default.Movie
        title.contains("Linux", ignoreCase = true) || title.contains("Apps", ignoreCase = true) -> Icons.Default.Terminal
        else -> Icons.Default.Folder
    }
}
