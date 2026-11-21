package br.com.educatransito.media

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import br.com.educatransito.data.AttachmentPolicy
import java.io.File
import java.io.RandomAccessFile
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AttachmentStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var provider: FixtureProvider
    private lateinit var store: AttachmentStore
    private val uri = Uri.parse("content://educatransito.test.documents/selected")

    @Before fun prepareProvider() {
        val info = ProviderInfo().apply {
            authority = "educatransito.test.documents"
            exported = true
        }
        provider = Robolectric.buildContentProvider(FixtureProvider::class.java).create(info).get()
        provider.file = temporary.newFile("source.png")
        store = AttachmentStore(RuntimeEnvironment.getApplication())
        attachmentDirectory().deleteRecursively()
    }

    @Test fun importedImageRemainsAfterSourceDeletionAndStoreRecreation() = runBlocking {
        // A genuine 1 x 1 PNG; the import validates Android's decoder bounds.
        val png = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC"
        )
        provider.file.writeBytes(png)
        provider.name = "../imagem\n.png"
        val attachment = store.import(uri, "image")

        assertEquals("image/png", attachment.mimeType)
        assertEquals(png.size.toLong(), attachment.sizeBytes)
        assertNotEquals(provider.name, attachment.localFileName)
        assertFalse(attachment.displayName.contains('/'))
        assertFalse(attachment.displayName.contains('\n'))
        assertTrue(provider.file.delete())

        val reopened = AttachmentStore(RuntimeEnvironment.getApplication())
        assertArrayEquals(png, reopened.file(attachment.localFileName).readBytes())
        assertEquals(attachmentDirectory().canonicalPath, reopened.file(attachment.localFileName).parentFile!!.canonicalPath)
    }

    @Test fun wrongMediaTypeIsRejectedBeforeCopying() = runBlocking {
        provider.mime = "video/mp4"
        assertRejected { store.import(uri, "image") }
        assertEquals(0, provider.openCount)
        assertNoImportedFiles()
    }

    @Test fun advertisedOversizeIsRejectedBeforeOpeningTheSource() = runBlocking {
        provider.reportedSize = AttachmentPolicy.MAX_IMAGE_BYTES + 1
        assertRejected { store.import(uri, "image") }
        assertEquals(0, provider.openCount)
        assertNoImportedFiles()
    }

    @Test fun unknownSizeStillHasStreamingLimitAndCleansPartialFile() = runBlocking {
        RandomAccessFile(provider.file, "rw").use { it.setLength(AttachmentPolicy.MAX_IMAGE_BYTES + 1) }
        provider.reportedSize = null
        assertRejected { store.import(uri, "image") }
        assertTrue(provider.openCount > 0)
        assertNoImportedFiles()
    }

    @Test fun emptySourceDoesNotLeaveAnAttachment() = runBlocking {
        assertRejected { store.import(uri, "image") }
        assertNoImportedFiles()
    }

    @Test fun invalidImageDoesNotLeaveAnAttachment() = runBlocking {
        provider.file.writeText("This is not an image")
        assertRejected { store.import(uri, "image") }
        assertNoImportedFiles()
    }

    private suspend fun assertRejected(block: suspend () -> Unit) {
        try {
            block()
            fail("Invalid media must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected validation failure.
        }
    }

    private fun attachmentDirectory() = File(RuntimeEnvironment.getApplication().filesDir, "attachments")
    private fun assertNoImportedFiles() = assertTrue(attachmentDirectory().listFiles().orEmpty().isEmpty())

    class FixtureProvider : ContentProvider() {
        lateinit var file: File
        var mime = "image/png"
        var name = "imagem.png"
        var reportedSize: Long? = null
        var openCount = 0

        override fun onCreate() = true
        override fun getType(uri: Uri) = mime
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
            MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
                addRow(arrayOf(name, reportedSize))
            }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            openCount++
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
    }
}
