package com.droid.fileexplorer.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.droid.fileexplorer.MainActivity
import com.droid.fileexplorer.R
import com.droid.fileexplorer.model.FileItem

object ShortcutHelper {

    fun createDesktopShortcut(context: Context, item: FileItem) {
        try {
            if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra(MainActivity.EXTRA_INITIAL_PATH, item.path)
                    putExtra(MainActivity.EXTRA_INITIAL_TREE_URI, item.treeUri)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }

                val iconRes = if (item.isDirectory) R.mipmap.ic_launcher else R.drawable.ic_launcher_foreground
                val icon = IconCompat.createWithResource(context, iconRes)

                val shortcut = ShortcutInfoCompat.Builder(context, "shortcut_${item.path.hashCode()}")
                    .setShortLabel(item.name.take(20))
                    .setLongLabel(item.name)
                    .setIcon(icon)
                    .setIntent(launchIntent)
                    .build()

                ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
                Toast.makeText(context, "Shortcut for '${item.name}' created on Desktop/Launcher", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Pinning shortcuts not supported by current launcher", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Could not create shortcut: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
