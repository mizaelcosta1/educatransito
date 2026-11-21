package br.com.educatransito.media

import android.graphics.Bitmap
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.educatransito.data.AppDatabase
import br.com.educatransito.data.OrientationDraft
import br.com.educatransito.data.OrientationRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses real Android codecs and ContentResolver, including a tiny generated H.264 video. */
@RunWith(AndroidJUnit4::class)
class MediaAttachmentInstrumentedTest {
    @Test fun importsImageAndVideoAndKeepsCopiesAfterOriginRemoved() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sources = File(context.cacheDir, "reports").apply { mkdirs() }
        val image = File(sources, "fixture-image.png")
        Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLUE)
            image.outputStream().use { check(compress(Bitmap.CompressFormat.PNG, 100, it)) }
            recycle()
        }
        val video = File(sources, "fixture-video.mp4").apply { writeBytes(Base64.decode(VIDEO, Base64.DEFAULT)) }
        val expectedImage = image.readBytes()
        val expectedVideo = video.readBytes()
        val store = AttachmentStore(context)
        val imageAttachment = store.import(FileProvider.getUriForFile(context, context.packageName + ".files", image), "image")
        val videoAttachment = store.import(FileProvider.getUriForFile(context, context.packageName + ".files", video), "video")
        assertEquals("image/png", imageAttachment.mimeType)
        assertEquals("video/mp4", videoAttachment.mimeType)
        assertTrue(image.delete())
        assertTrue(video.delete())
        assertArrayEquals(expectedImage, store.file(imageAttachment.localFileName).readBytes())
        assertArrayEquals(expectedVideo, store.file(videoAttachment.localFileName).readBytes())
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repository = OrientationRepository(database)
            val saved = repository.save(OrientationDraft(
                location = "Local fictício de teste",
                primaryTopic = "Respeito à faixa de pedestres",
                description = "Relato fictício com imagem e vídeo locais.",
                attachments = listOf(imageAttachment, videoAttachment)
            ))
            val reloaded = repository.getRecord(saved.orientation.id)!!
            assertEquals(2, reloaded.attachments.size)
            assertEquals(saved.orientation.protocol, reloaded.orientation.protocol)
            assertEquals(setOf("image/png", "video/mp4"), reloaded.attachments.map { it.mimeType }.toSet())
        } finally {
            database.close()
            store.discard(imageAttachment.localFileName)
            store.discard(videoAttachment.localFileName)
        }
    }

    companion object {
        // Generated color-only, one-second H.264 baseline fixture; contains no personal data.
        private const val VIDEO = "AAAAIGZ0eXBpc29tAAACAGlzb21pc28yYXZjMW1wNDEAAAMObW9vdgAAAGxtdmhkAAAAAAAAAAAAAAAAAAAD6AAAA+gAAQAAAQAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgAAAjl0cmFrAAAAXHRraGQAAAADAAAAAAAAAAAAAAABAAAAAAAAA+gAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAABAAAAAACAAAAAgAAAAAAAkZWR0cwAAABxlbHN0AAAAAAAAAAEAAAPoAAAAAAABAAAAAAGxbWRpYQAAACBtZGhkAAAAAAAAAAAAAAAAAABAAAAAQABVxAAAAAAALWhkbHIAAAAAAAAAAHZpZGUAAAAAAAAAAAAAAABWaWRlb0hhbmRsZXIAAAABXG1pbmYAAAAUdm1oZAAAAAEAAAAAAAAAAAAAACRkaW5mAAAAHGRyZWYAAAAAAAAAAQAAAAx1cmwgAAAAAQAAARxzdGJsAAAAuHN0c2QAAAAAAAAAAQAAAKhhdmMxAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAAAACAAIABIAAAASAAAAAAAAAABFUxhdmM2MS4xOS4xMDEgbGlieDI2NAAAAAAAAAAAAAAAGP//AAAALmF2Y0MBQsAK/+EAFmdCwArZCWwEQAAAAwBAAAADAIPEiZIBAAVoy4PLIAAAABBwYXNwAAAAAQAAAAEAAAAUYnRydAAAAAAAABRwAAAAAAAAABhzdHRzAAAAAAAAAAEAAAABAABAAAAAABxzdHNjAAAAAAAAAAEAAAABAAAAAQAAAAEAAAAUc3RzegAAAAAAAAKOAAAAAQAAABRzdGNvAAAAAAAAAAEAAAM+AAAAYXVkdGEAAABZbWV0YQAAAAAAAAAhaGRscgAAAAAAAAAAbWRpcmFwcGwAAAAAAAAAAAAAAAAsaWxzdAAAACSpdG9vAAAAHGRhdGEAAAABAAAAAExhdmY2MS43LjEwMwAAAAhmcmVlAAAClm1kYXQAAAJwBgX//2zcRem95tlIt5Ys2CDZI+7veDI2NCAtIGNvcmUgMTY0IHIzMTA4IDMxZTE5ZjkgLSBILjI2NC9NUEVHLTQgQVZDIGNvZGVjIC0gQ29weWxlZnQgMjAwMy0yMDIzIC0gaHR0cDovL3d3dy52aWRlb2xhbi5vcmcveDI2NC5odG1sIC0gb3B0aW9uczogY2FiYWM9MCByZWY9MyBkZWJsb2NrPTE6MDowIGFuYWx5c2U9MHgxOjB4MTExIG1lPWhleCBzdWJtZT03IHBzeT0xIHBzeV9yZD0xLjAwOjAuMDAgbWl4ZWRfcmVmPTEgbWVfcmFuZ2U9MTYgY2hyb21hX21lPTEgdHJlbGxpcz0xIDh4OGRjdD0wIGNxbT0wIGRlYWR6b25lPTIxLDExIGZhc3RfcHNraXA9MSBjaHJvbWFfcXBfb2Zmc2V0PS0yIHRocmVhZHM9MSBsb29rYWhlYWRfdGhyZWFkcz0xIHNsaWNlZF90aHJlYWRzPTAgbnI9MCBkZWNpbWF0ZT0xIGludGVybGFjZWQ9MCBibHVyYXlfY29tcGF0PTAgY29uc3RyYWluZWRfaW50cmE9MCBiZnJhbWVzPTAgd2VpZ2h0cD0wIGtleWludD0yNTAga2V5aW50X21pbj0xIHNjZW5lY3V0PTQwIGludHJhX3JlZnJlc2g9MCByY19sb29rYWhlYWQ9NDAgcmM9Y3JmIG1idHJlZT0xIGNyZj0yMy4wIHFjb21wPTAuNjAgcXBtaW49MCBxcG1heD02OSBxcHN0ZXA9NCBpcF9yYXRpbz0xLjQwIGFxPTE6MS4wMACAAAAAFmWIhAV8RigAD4zHAAFWyOAAIXMnXXg="
    }
}
