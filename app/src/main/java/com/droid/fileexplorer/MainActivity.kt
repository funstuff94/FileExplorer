package com.droid.fileexplorer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.droid.fileexplorer.data.DefaultFileRepository
import com.droid.fileexplorer.theme.FileExplorerTheme
import com.droid.fileexplorer.ui.FileExplorerApp
import com.droid.fileexplorer.viewmodel.FileExplorerViewModel

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_INITIAL_PATH = "EXTRA_INITIAL_PATH"
        const val EXTRA_INITIAL_TREE_URI = "EXTRA_INITIAL_TREE_URI"
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.refreshDrives()
        }
    }

    private val mediaReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.refreshDrives()
        }
    }

    private val viewModel: FileExplorerViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repository = DefaultFileRepository(applicationContext)
                return FileExplorerViewModel(repository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setBackgroundDrawableResource(android.R.color.black)
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)
        requestStorageAccessIfNeeded()

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val systemInDarkTheme = isSystemInDarkTheme()
            val isDark = uiState.isDarkMode ?: systemInDarkTheme

            FileExplorerTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FileExplorerApp(
                        viewModel = viewModel,
                        isDarkTheme = isDark,
                        onToggleTheme = { viewModel.toggleDarkMode(systemInDarkTheme) }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val usbFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        val mediaFilterWithScheme = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        val mediaFilterNoScheme = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(usbReceiver, usbFilter, Context.RECEIVER_EXPORTED)
                registerReceiver(mediaReceiver, mediaFilterWithScheme, Context.RECEIVER_EXPORTED)
                registerReceiver(mediaReceiver, mediaFilterNoScheme, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(usbReceiver, usbFilter)
                registerReceiver(mediaReceiver, mediaFilterWithScheme)
                registerReceiver(mediaReceiver, mediaFilterNoScheme)
            }
        } catch (_: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(usbReceiver, usbFilter, Context.RECEIVER_NOT_EXPORTED)
                    registerReceiver(mediaReceiver, mediaFilterWithScheme, Context.RECEIVER_NOT_EXPORTED)
                    registerReceiver(mediaReceiver, mediaFilterNoScheme, Context.RECEIVER_NOT_EXPORTED)
                }
            } catch (_: Exception) {}
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(usbReceiver)
        } catch (_: Exception) {}
        try {
            unregisterReceiver(mediaReceiver)
        } catch (_: Exception) {}
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
        viewModel.refreshDrives()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val path = intent.getStringExtra(EXTRA_INITIAL_PATH)
        val treeUri = intent.getStringExtra(EXTRA_INITIAL_TREE_URI)
        viewModel.handleIntentPath(path, treeUri)
    }

    override fun onResume() {
        super.onResume()
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
        viewModel.onPermissionResult(hasPermission)
        viewModel.refreshDrives()
        viewModel.startDeviceMonitoring()
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopDeviceMonitoring()
    }

    private fun requestStorageAccessIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        }
    }
}
