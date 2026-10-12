package com.droid.fileexplorer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
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
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)
        requestStorageAccessIfNeeded()

        setContent {
            FileExplorerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FileExplorerApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
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
