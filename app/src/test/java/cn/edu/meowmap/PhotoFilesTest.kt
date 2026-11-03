package cn.edu.meowmap

import cn.edu.meowmap.data.PhotoFiles
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PhotoFilesTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun photo(root: File, name: String) = File(root, name).apply { writeBytes(byteArrayOf(1, 2, 3)) }

    @Test fun removesOnlyUnusedPrivatePhotos() {
        val root = temporary.newFolder("photos")
        val orphan = photo(root, "unused.jpg")
        val saved = photo(root, "record.jpg")
        val draft = photo(root, "draft.jpg")
        val files = PhotoFiles(root)
        assertEquals(1, files.deleteUnreferenced(listOf(orphan.path, saved.path, draft.path), listOf(saved.path, draft.path)))
        assertFalse(orphan.exists()); assertTrue(saved.exists()); assertTrue(draft.exists())
    }
    @Test fun neverDeletesExternalFilesOrParentTraversalTargets() {
        val root = temporary.newFolder("photos")
        val outside = photo(temporary.root, "outside.jpg")
        assertEquals(0, PhotoFiles(root).deleteUnreferenced(listOf(outside.path, File(root, "../outside.jpg").path), emptyList()))
        assertTrue(outside.exists())
    }
    @Test fun sharedPhotoIsRetainedUntilFinalReferenceIsRemoved() {
        val root = temporary.newFolder("photos")
        val shared = photo(root, "shared.jpg")
        val files = PhotoFiles(root)
        assertEquals(0, files.deleteUnreferenced(listOf(shared.path), listOf(shared.path)))
        assertEquals(1, files.deleteUnreferenced(listOf(shared.path), emptyList()))
    }
    @Test fun ignoresDirectoriesNonPhotosMissingAndDuplicateCandidates() {
        val root = temporary.newFolder("photos")
        val nonPhoto = photo(root, "notes.txt")
        val directory = File(root, "nested.jpg").apply { mkdirs() }
        val orphan = photo(root, "unused.jpg")
        assertEquals(1, PhotoFiles(root).deleteUnreferenced(listOf(nonPhoto.path, directory.path, File(root, "missing.jpg").path, orphan.path, orphan.path), emptyList()))
        assertTrue(nonPhoto.exists()); assertTrue(directory.exists())
    }
    @Test fun canonicalReferencesProtectFilesFromAlternatePathSpellings() {
        val root = temporary.newFolder("photos")
        val saved = photo(root, "saved.jpg")
        assertEquals(0, PhotoFiles(root).deleteUnreferenced(listOf(saved.path), listOf(File(root, "./saved.jpg").path)))
        assertTrue(saved.exists())
    }
}
