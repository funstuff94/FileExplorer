package com.droid.fileexplorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.droid.fileexplorer.model.FileCategory
import com.droid.fileexplorer.model.FileItem
import com.droid.fileexplorer.theme.Win11Blue
import com.droid.fileexplorer.theme.Win11FolderYellow

@Composable
fun FileTypeIcon(
    item: FileItem,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val isFolder = item.isDirectory || (item.isShortcut && item.shortcutTargetIsDirectory)

    val (icon, tint, bgColor) = when {
        isFolder -> Triple(Icons.Default.Folder, Win11FolderYellow, Color.Transparent)
        item.category == FileCategory.IMAGE -> Triple(Icons.Default.Image, Color(0xFF0078D4), Color(0x180078D4))
        item.category == FileCategory.VIDEO -> Triple(Icons.Default.Videocam, Color(0xFFD83B01), Color(0x18D83B01))
        item.category == FileCategory.AUDIO -> Triple(Icons.Default.Audiotrack, Color(0xFFE3008C), Color(0x18E3008C))
        item.category == FileCategory.DOCUMENT -> Triple(Icons.Default.Description, Color(0xFF107C41), Color(0x18107C41))
        item.category == FileCategory.CODE -> Triple(Icons.Default.Code, Color(0xFF008272), Color(0x18008272))
        item.category == FileCategory.ARCHIVE -> Triple(Icons.Default.Archive, Color(0xFF8E562E), Color(0x188E562E))
        item.category == FileCategory.APK -> Triple(Icons.Default.Android, Color(0xFF3DDC84), Color(0x183DDC84))
        else -> Triple(Icons.Default.InsertDriveFile, Color(0xFF797775), Color(0x18797775))
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (isFolder && !item.isShortcut) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(size)
            )
        } else if (isFolder && item.isShortcut) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(size)
            )
            // Windows 11 Shortcut Arrow Badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(size * 0.44f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White)
                    .border(0.5.dp, Color(0xFFCCCCCC), RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NorthEast,
                    contentDescription = "Shortcut",
                    tint = Win11Blue,
                    modifier = Modifier.size(size * 0.32f)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(size * 0.65f)
                )
            }

            if (item.isShortcut) {
                // Windows 11 Shortcut Arrow Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .size(size * 0.44f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White)
                        .border(0.5.dp, Color(0xFFCCCCCC), RoundedCornerShape(3.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NorthEast,
                        contentDescription = "Shortcut",
                        tint = Win11Blue,
                        modifier = Modifier.size(size * 0.32f)
                    )
                }
            }
        }
    }
}
