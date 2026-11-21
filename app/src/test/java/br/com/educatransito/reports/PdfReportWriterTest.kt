package br.com.educatransito.reports

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import br.com.educatransito.data.AttachmentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfReportWriterTest {
    @Test fun createsA4SimpleAndPaginatedDetailedReportsWithLocalImageAndVideoMetadata() {
        val context: Context = RuntimeEnvironment.getApplication()
        val attachments = File(context.filesDir, "attachments").apply { mkdirs() }
        val imageFile = File(attachments, "qa-image.png")
        Bitmap.createBitmap(900, 600, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            canvas.drawColor(Color.rgb(232, 241, 247))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(16, 42, 67) }
            canvas.drawRect(70f, 200f, 830f, 340f, paint)
            paint.color = Color.rgb(230, 168, 23)
            canvas.drawRect(300f, 220f, 600f, 320f, paint)
            paint.color = Color.BLACK
            paint.textSize = 36f
            canvas.drawText("IMAGEM FICTÍCIA DE TESTE", 175f, 130f, paint)
            imageFile.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
            recycle()
        }
        val videoFile = File(attachments, "qa-video.mp4").apply { writeText("Fixture metadata only; not a real video") }
        val description = buildString {
            repeat(90) { append("Linha ${it + 1}: orientação sobre segurança no trânsito, respeito aos pedestres e uso correto do cinto.\n") }
            append("PALAVRASEMESPACOS".repeat(20))
            append("\nFIM DO RELATO DE TESTE.")
        }
        val record = reportRecord().let {
            it.copy(
                orientation = it.orientation.copy(description = description),
                attachments = listOf(
                    AttachmentEntity(id = "qa-image", orientationId = 1, localFileName = imageFile.name, mimeType = "image/png", displayName = "Imagem fictícia.png", sizeBytes = imageFile.length(), addedAt = 1_000),
                    AttachmentEntity(id = "qa-video", orientationId = 1, localFileName = videoFile.name, mimeType = "video/mp4", displayName = "Vídeo fictício.mp4", sizeBytes = videoFile.length(), addedAt = 2_000)
                )
            )
        }
        val directory = File("build/qa").apply { mkdirs() }
        val simple = File(directory, "relatorio-simples-teste.pdf")
        val detailed = File(directory, "relatorio-detalhado-teste.pdf")
        val writer = PdfReportWriter(context)
        simple.outputStream().use { writer.write(it, listOf(record), false, "Teste de um registro") }
        detailed.outputStream().use { writer.write(it, listOf(record), true, "Teste de um registro") }
        assertTrue(simple.readBytes().take(5).toByteArray().decodeToString() == "%PDF-")
        assertTrue(detailed.length() > simple.length())
        renderAndCheck(simple, File(directory, "relatorio-simples-primeira-pagina.png"), expectMultiplePages = false)
        renderAndCheck(detailed, File(directory, "relatorio-detalhado-primeira-pagina.png"), expectMultiplePages = true)
    }

    private fun renderAndCheck(file: File, preview: File, expectMultiplePages: Boolean) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            val renderer = PdfRenderer(descriptor)
            try {
                if (expectMultiplePages) assertTrue(renderer.pageCount >= 3) else assertEquals(1, renderer.pageCount)
                val page = renderer.openPage(0)
                try {
                    assertEquals(595, page.width)
                    assertEquals(842, page.height)
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    preview.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                } finally {
                    page.close()
                }
            } finally {
                renderer.close()
            }
        }
    }
}
