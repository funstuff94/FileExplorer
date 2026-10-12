package com.droid.fileexplorer

import com.droid.fileexplorer.util.FileUriResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileUriResolverTest {

    @Test
    fun extractVolumeUuid_identifiesSecondaryExternalStorage() {
        assertEquals("1234-5678", FileUriResolver.extractVolumeUuid("/storage/1234-5678/Music/song.mp3"))
        assertEquals("ABCD-EF01", FileUriResolver.extractVolumeUuid("/storage/ABCD-EF01"))
        assertEquals("9876-5432", FileUriResolver.extractVolumeUuid("/mnt/media_rw/9876-5432/Audio/track.wav"))
        assertEquals("usb_drive_1", FileUriResolver.extractVolumeUuid("/storage/usb_drive_1/folder/file.txt"))
    }

    @Test
    fun extractVolumeUuid_ignoresInternalAndEmulatedStorage() {
        assertNull(FileUriResolver.extractVolumeUuid("/storage/emulated/0/Music/song.mp3"))
        assertNull(FileUriResolver.extractVolumeUuid("/storage/emulated/10/Apps/MusicPlayer"))
        assertNull(FileUriResolver.extractVolumeUuid("/storage/self"))
        assertNull(FileUriResolver.extractVolumeUuid("/storage"))
        assertNull(FileUriResolver.extractVolumeUuid("/mnt/shared/Music"))
    }

    @Test
    fun extractVolumeUuid_parsesContentTreeUris() {
        val treeUri = "content://com.android.externalstorage.documents/tree/1234-5678%3A"
        assertEquals("1234-5678", FileUriResolver.extractVolumeUuid(treeUri))

        val primaryUri = "content://com.android.externalstorage.documents/tree/primary%3A"
        assertNull(FileUriResolver.extractVolumeUuid(primaryUri))
    }

    @Test
    fun isExternalStoragePath_verifiesCorrectVolumes() {
        assertTrue(FileUriResolver.isExternalStoragePath("/storage/1234-5678/Music/song.mp3"))
        assertTrue(FileUriResolver.isExternalStoragePath("/mnt/media_rw/ABCD-1234/file.flac"))
        assertFalse(FileUriResolver.isExternalStoragePath("/storage/emulated/0/Download"))
        assertFalse(FileUriResolver.isExternalStoragePath("/storage/emulated/10/Apps"))
    }

    @Test
    fun buildRelativePath_and_buildDocumentId_formatCorrectSafIds() {
        val uuid = "1234-5678"
        val fullPath = "/storage/1234-5678/Music/Rock/song.mp3"

        assertEquals("Music/Rock/song.mp3", FileUriResolver.buildRelativePath(uuid, fullPath))
        assertEquals("1234-5678:Music/Rock/song.mp3", FileUriResolver.buildDocumentId(uuid, fullPath))

        val rootPath = "/storage/1234-5678"
        assertEquals("", FileUriResolver.buildRelativePath(uuid, rootPath))
        assertEquals("1234-5678:", FileUriResolver.buildDocumentId(uuid, rootPath))
    }
}
