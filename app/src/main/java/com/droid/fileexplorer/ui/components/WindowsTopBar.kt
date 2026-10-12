package com.droid.fileexplorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import com.droid.fileexplorer.model.Breadcrumb
import com.droid.fileexplorer.model.ExplorerTab
import com.droid.fileexplorer.theme.Win11Blue
import com.droid.fileexplorer.theme.Win11FolderYellow

@Composable
fun WindowsTopBar(
    tabs: List<ExplorerTab>,
    activeTabId: String,
    currentFolderName: String,
    currentPath: String,
    breadcrumbs: List<Breadcrumb>,
    searchQuery: String,
    canGoBack: Boolean = true,
    canGoForward: Boolean = true,
    isCurrentFolderPinned: Boolean = false,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onNewTab: () -> Unit,
    onOpenNewWindow: () -> Unit = {},
    onTogglePinCurrent: () -> Unit = {},
    onBack: () -> Unit,
    onForward: () -> Unit,
    onUp: () -> Unit,
    onRefresh: () -> Unit,
    onBreadcrumbClick: (Breadcrumb) -> Unit,
    onSearchChange: (String) -> Unit,
    isEditingAddress: Boolean = false,
    onEditingAddressChange: (Boolean) -> Unit = {},
    onAddressSubmit: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val addressScrollState = rememberScrollState()
    val tabScrollState = rememberScrollState()
    var addressInputValue by remember { mutableStateOf(TextFieldValue(currentPath)) }
    val addressFocusRequester = remember { FocusRequester() }

    LaunchedEffect(currentPath) {
        addressInputValue = TextFieldValue(currentPath)
        if (isEditingAddress) {
            onEditingAddressChange(false)
        }
    }

    LaunchedEffect(isEditingAddress) {
        if (isEditingAddress) {
            addressInputValue = TextFieldValue(
                text = currentPath,
                selection = TextRange(0, currentPath.length)
            )
            try {
                addressFocusRequester.requestFocus()
            } catch (_: Exception) {}
        } else {
            addressInputValue = TextFieldValue(currentPath)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        // Windows 11 Multiple Tabs Strip
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(tabScrollState)
                .padding(bottom = 6.dp)
        ) {
            tabs.forEach { tab ->
                val isActive = tab.id == activeTabId
                Surface(
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                    color = if (isActive) MaterialTheme.colorScheme.surface else Color.Transparent,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            1.dp,
                            if (isActive) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            if (isEditingAddress) onEditingAddressChange(false)
                            onTabClick(tab.id)
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (tab.treeUri != null) Icons.Default.Cloud else if (tab.path.isEmpty() || tab.path == "This PC") Icons.Default.Computer else Icons.Default.Folder,
                            contentDescription = null,
                            tint = Win11FolderYellow,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width((120.dp).coerceAtMost(160.dp))
                        )

                        if (tabs.size > 1) {
                            IconButton(
                                onClick = {
                                    if (isEditingAddress) onEditingAddressChange(false)
                                    onTabClose(tab.id)
                                },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close tab",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // New Tab "+" Button
            IconButton(
                onClick = {
                    if (isEditingAddress) onEditingAddressChange(false)
                    onNewTab()
                },
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Tab (Ctrl+T)",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // New Window Button
            IconButton(
                onClick = onOpenNewWindow,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInBrowser,
                    contentDescription = "New Window (Ctrl+N)",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Pin active folder toggle
            IconButton(
                onClick = onTogglePinCurrent,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (isCurrentFolderPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (isCurrentFolderPinned) "Unpin from Quick access" else "Pin to Quick access",
                    tint = if (isCurrentFolderPinned) Win11Blue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Navigation Row: Back / Forward / Up / Refresh + Breadcrumb Address Bar + Search Box
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Navigation Buttons
            IconButton(
                onClick = {
                    if (isEditingAddress) onEditingAddressChange(false)
                    onBack()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = {
                    if (isEditingAddress) onEditingAddressChange(false)
                    onForward()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Forward",
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = {
                    if (isEditingAddress) onEditingAddressChange(false)
                    onUp()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = "Up",
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = {
                    if (isEditingAddress) onEditingAddressChange(false)
                    onRefresh()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Windows 11 Breadcrumb / Editable Address Bar
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .border(
                        1.dp,
                        if (isEditingAddress) Win11Blue else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        RoundedCornerShape(6.dp)
                    )
            ) {
                if (isEditingAddress) {
                    // --- EDITABLE TEXT FIELD MODE ---
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (breadcrumbs.any { it.treeUri != null }) Icons.Default.Cloud else Icons.Default.Folder,
                            contentDescription = null,
                            tint = Win11FolderYellow,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))

                        BasicTextField(
                            value = addressInputValue,
                            onValueChange = { addressInputValue = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(Win11Blue),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    if (addressInputValue.text.isNotBlank()) {
                                        onAddressSubmit(addressInputValue.text)
                                        onEditingAddressChange(false)
                                    }
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(addressFocusRequester)
                                .onKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown) {
                                        if (event.key == Key.Enter) {
                                            if (addressInputValue.text.isNotBlank()) {
                                                onAddressSubmit(addressInputValue.text)
                                                onEditingAddressChange(false)
                                            }
                                            true
                                        } else if (event.key == Key.Escape) {
                                            addressInputValue = TextFieldValue(currentPath)
                                            onEditingAddressChange(false)
                                            true
                                        } else false
                                    } else false
                                }
                        )

                        // Paste from Clipboard Button
                        IconButton(
                            onClick = {
                                val clipText = clipboardManager.getText()?.text
                                if (!clipText.isNullOrEmpty()) {
                                    addressInputValue = TextFieldValue(
                                        text = clipText.trim(),
                                        selection = TextRange(clipText.trim().length)
                                    )
                                }
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Paste from clipboard",
                                tint = Win11Blue,
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        // Clear Button
                        if (addressInputValue.text.isNotEmpty()) {
                            IconButton(
                                onClick = { addressInputValue = TextFieldValue("") },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        // Go / Navigate Arrow Button
                        IconButton(
                            onClick = {
                                if (addressInputValue.text.isNotBlank()) {
                                    onAddressSubmit(addressInputValue.text)
                                    onEditingAddressChange(false)
                                }
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Go to path",
                                tint = Win11Blue,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Cancel / Exit Edit Mode
                        IconButton(
                            onClick = {
                                addressInputValue = TextFieldValue(currentPath)
                                onEditingAddressChange(false)
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                } else {
                    // --- BREADCRUMBS MODE ---
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditingAddressChange(true) }
                            .padding(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (breadcrumbs.any { it.treeUri != null }) Icons.Default.Cloud else Icons.Default.Folder,
                            contentDescription = null,
                            tint = Win11FolderYellow,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(addressScrollState)
                        ) {
                            breadcrumbs.forEachIndexed { index, crumb ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            if (isEditingAddress) onEditingAddressChange(false)
                                            onBreadcrumbClick(crumb)
                                        }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = crumb.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (index == breadcrumbs.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (index == breadcrumbs.lastIndex) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                                if (index < breadcrumbs.lastIndex) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            // Clickable blank area to edit path
                            Spacer(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .width(60.dp)
                                    .height(28.dp)
                                    .clickable { onEditingAddressChange(true) }
                            )
                        }

                        // Edit / Type Button
                        IconButton(
                            onClick = { onEditingAddressChange(true) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Type address (Ctrl+L)",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Copy Path Button
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(currentPath))
                                Toast.makeText(context, "Path copied: $currentPath", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Path",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Quick Paste & Go Button
                        IconButton(
                            onClick = {
                                val clipText = clipboardManager.getText()?.text
                                if (!clipText.isNullOrEmpty()) {
                                    onAddressSubmit(clipText.trim())
                                } else {
                                    onEditingAddressChange(true)
                                }
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Paste and go",
                                tint = Win11Blue,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Windows 11 Search Box
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .width(220.dp)
                    .height(36.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search $currentFolderName",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(Win11Blue),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchChange("") },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
