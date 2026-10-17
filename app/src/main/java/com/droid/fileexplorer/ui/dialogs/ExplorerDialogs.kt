package com.droid.fileexplorer.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import android.net.Uri
import com.droid.fileexplorer.model.FileItem
import com.droid.fileexplorer.model.TransferProgress
import com.droid.fileexplorer.theme.Win11Blue
import com.droid.fileexplorer.theme.Win11Red
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NewItemDialog(
    isFolder: Boolean,
    parentFolderName: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val defaultText = if (isFolder) "New folder" else "New Text Document.txt"
    val selectionEnd = if (isFolder) defaultText.length else defaultText.lastIndexOf('.').let { if (it > 0) it else defaultText.length }
    var textValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = defaultText,
                selection = TextRange(0, selectionEnd)
            )
        )
    }
    val focusRequester = remember { FocusRequester() }

    val handleConfirm = {
        val trimmed = textValue.text.trim()
        if (trimmed.isNotBlank()) {
            val finalName = if (!isFolder && !trimmed.contains('.')) "$trimmed.txt" else trimmed
            onConfirm(finalName)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val titleText = if (parentFolderName != null) {
                if (isFolder) "Create New Folder in '$parentFolderName'" else "Create New File in '$parentFolderName'"
            } else {
                if (isFolder) "Create New Folder" else "Create New File"
            }
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                Text(
                    text = if (isFolder) "Enter folder name:" else "Enter file name:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    singleLine = true,
                    shape = RoundedCornerShape(6.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { handleConfirm() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { handleConfirm() },
                colors = ButtonDefaults.buttonColors(containerColor = Win11Blue),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(8.dp)
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
fun RenameDialog(
    currentItem: FileItem,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentItem.name) }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Rename ${if (currentItem.isDirectory) "Folder" else "File"}",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank() && name != currentItem.name) onConfirm(name.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = Win11Blue),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(8.dp)
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
fun DeleteConfirmDialog(
    itemCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete Items",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = "Are you sure you want to permanently delete $itemCount item(s)? This action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Win11Red),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(8.dp)
    )
}

data class FolderStats(
    val totalBytes: Long,
    val fileCount: Int,
    val folderCount: Int,
    val isComplete: Boolean
)

@Composable
fun PropertiesDialog(
    item: FileItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("MMMM d, yyyy, h:mm:ss a", Locale.getDefault()) }

    var folderStats by remember(item.id) {
        mutableStateOf<FolderStats?>(
            if (item.isDirectory && item.size > 0L) {
                FolderStats(item.size, item.childCount, 0, false)
            } else null
        )
    }

    LaunchedEffect(item.id) {
        if (item.isDirectory) {
            withContext(Dispatchers.IO) {
                val isDoc = item.treeUri != null || item.path.startsWith("content://")
                var totalBytes = 0L
                var filesCount = 0
                var foldersCount = 0
                var lastUpdate = System.currentTimeMillis()

                if (!isDoc) {
                    val root = File(item.path)
                    fun scanDir(dir: File) {
                        val list = dir.listFiles() ?: return
                        for (f in list) {
                            if (f.isDirectory) {
                                foldersCount++
                                val now = System.currentTimeMillis()
                                if (now - lastUpdate > 200) {
                                    lastUpdate = now
                                    folderStats = FolderStats(totalBytes, filesCount, foldersCount, false)
                                }
                                scanDir(f)
                            } else {
                                filesCount++
                                totalBytes += f.length()
                                val now = System.currentTimeMillis()
                                if (now - lastUpdate > 200) {
                                    lastUpdate = now
                                    folderStats = FolderStats(totalBytes, filesCount, foldersCount, false)
                                }
                            }
                        }
                    }
                    scanDir(root)
                    folderStats = FolderStats(totalBytes, filesCount, foldersCount, true)
                } else {
                    val uri = Uri.parse(item.treeUri ?: item.path)
                    val doc = DocumentFile.fromTreeUri(context, uri) ?: DocumentFile.fromSingleUri(context, uri)
                    fun scanDoc(d: DocumentFile) {
                        val list = d.listFiles()
                        for (c in list) {
                            if (c.isDirectory) {
                                foldersCount++
                                val now = System.currentTimeMillis()
                                if (now - lastUpdate > 200) {
                                    lastUpdate = now
                                    folderStats = FolderStats(totalBytes, filesCount, foldersCount, false)
                                }
                                scanDoc(c)
                            } else {
                                filesCount++
                                totalBytes += c.length()
                                val now = System.currentTimeMillis()
                                if (now - lastUpdate > 200) {
                                    lastUpdate = now
                                    folderStats = FolderStats(totalBytes, filesCount, foldersCount, false)
                                }
                            }
                        }
                    }
                    doc?.let { scanDoc(it) }
                    folderStats = FolderStats(totalBytes, filesCount, foldersCount, true)
                }
            }
        }
    }

    val displaySizeText = if (item.isDirectory) {
        val stats = folderStats
        when {
            stats != null && stats.isComplete -> "${FileItem.formatByteSize(stats.totalBytes)} (${FileItem.formatExactBytes(stats.totalBytes)})"
            stats != null -> "Calculating... (${FileItem.formatByteSize(stats.totalBytes)})"
            item.size > 0L -> "${FileItem.formatByteSize(item.size)} (${FileItem.formatExactBytes(item.size)})"
            else -> "Calculating..."
        }
    } else {
        "${FileItem.formatByteSize(item.size)} (${FileItem.formatExactBytes(item.size)})"
    }

    val containsText = if (item.isDirectory) {
        val stats = folderStats
        when {
            stats != null && stats.isComplete -> "${NumberFormat.getNumberInstance().format(stats.fileCount)} Files, ${NumberFormat.getNumberInstance().format(stats.folderCount)} Folders"
            stats != null -> "Scanning... (${stats.fileCount} files)"
            item.childCount > 0 -> "${item.childCount} items"
            else -> "Empty"
        }
    } else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${item.name} Properties",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                val typeValue = when {
                    item.isShortcut -> "${item.displayFileType} (Shortcut)"
                    item.isDirectory -> "File folder"
                    item.extension.isNotEmpty() -> "${item.displayFileType} (.${item.extension})"
                    else -> item.displayFileType
                }
                PropertyRow(label = "Type:", value = typeValue)
                PropertyRow(label = "Location:", value = item.path)
                PropertyRow(label = "Size:", value = displaySizeText)
                if (containsText != null) {
                    PropertyRow(label = "Contains:", value = containsText)
                }
                if (item.lastModified > 0) {
                    PropertyRow(label = "Modified:", value = dateFormat.format(Date(item.lastModified)))
                }
                val storageLabel = when {
                    item.isCloud -> "Cloud Storage (SAF)"
                    item.path.startsWith("/storage/") && !item.path.startsWith("/storage/emulated") -> "External Storage (D:)"
                    else -> "Internal Storage (C:)"
                }
                PropertyRow(label = "Storage:", value = storageLabel)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Win11Blue),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("OK")
            }
        },
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
fun FileTransferDialog(
    progress: TransferProgress,
    onCancel: () -> Unit
) {
    Dialog(
        onDismissRequest = { /* Modal: prevent dismissing outside during transfer */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .width(460.dp)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (progress.operation == "Moving") Icons.Default.ContentCut else Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = Win11Blue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${progress.operation} ${progress.totalItems} item${if (progress.totalItems > 1) "s" else ""}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Source to Destination
                if (progress.sourcePath.isNotEmpty() && progress.destPath.isNotEmpty()) {
                    val srcName = progress.sourcePath.trimEnd('/').substringAfterLast('/')
                    val destName = progress.destPath.trimEnd('/').substringAfterLast('/')
                    Text(
                        text = "From $srcName to $destName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Currently copying file name
                Text(
                    text = if (progress.currentFileName.isNotEmpty()) "Processing: ${progress.currentFileName}" else "Preparing...",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Progress Bar
                val fraction = if (progress.totalBytes > 0) {
                    (progress.bytesTransferred.toFloat() / progress.totalBytes.toFloat()).coerceIn(0f, 1f)
                } else 0f

                if (progress.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Win11Blue,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Win11Blue,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Progress Statistics: Transferred of Total (Percentage) and Speed
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val percent = (fraction * 100).toInt()
                    val bytesText = if (progress.totalBytes > 0) {
                        "${FileItem.formatByteSize(progress.bytesTransferred)} of ${FileItem.formatByteSize(progress.totalBytes)} ($percent%)"
                    } else {
                        FileItem.formatByteSize(progress.bytesTransferred)
                    }
                    Text(
                        text = bytesText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )

                    if (progress.speedBytesPerSec > 1024.0) {
                        Text(
                            text = "${FileItem.formatByteSize(progress.speedBytesPerSec.toLong())}/s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Time remaining & Item count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val timeStr = formatRemainingTime(progress.estimatedRemainingSeconds, progress.speedBytesPerSec)
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Item ${progress.currentItemIndex} of ${progress.totalItems}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Cancel Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

private fun formatRemainingTime(seconds: Long, speedBytesPerSec: Double): String {
    if (speedBytesPerSec <= 1024.0 || seconds <= 0) {
        return "Estimating time remaining..."
    }
    return when {
        seconds < 60 -> "About $seconds second${if (seconds != 1L) "s" else ""} remaining"
        seconds < 3600 -> {
            val mins = seconds / 60
            val secs = seconds % 60
            "About $mins min ${if (secs > 0) "$secs sec " else ""}remaining"
        }
        else -> {
            val hours = seconds / 3600
            val mins = (seconds % 3600) / 60
            "About $hours hr ${if (mins > 0) "$mins min " else ""}remaining"
        }
    }
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(90.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
