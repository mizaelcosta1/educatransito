package br.com.educatransito.reports

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import br.com.educatransito.data.AttachmentEntity
import br.com.educatransito.data.CitizenEntity
import br.com.educatransito.data.OrientationEntity
import br.com.educatransito.data.OrientationRecord
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfReportWriterTest {
    @Test fun createsA4SimpleAndPaginatedDetailedReportsWithLocalImageAndVideoMetadata() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
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
        val directory = File(requireNotNull(context.getExternalFilesDir(null)), "qa").apply { mkdirs() }
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
                for (index in 0 until renderer.pageCount) {
                    val page = renderer.openPage(index)
                    try {
                        assertEquals(595, page.width)
                        assertEquals(842, page.height)
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val image = if (index == 0) preview else File(preview.parentFile, "${preview.nameWithoutExtension}-${index + 1}.png")
                            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        } finally {
                            bitmap.recycle()
                        }
                    } finally {
                        page.close()
                    }
                }
            } finally {
                renderer.close()
            }
        }
    }
}


private fun reportRecord(): OrientationRecord = OrientationRecord(
    orientation = OrientationEntity(
        id = 1,
        protocol = "EDU-2026-000001",
        protocolYear = 2026,
        protocolSequence = 1,
        citizenId = 1,
        vehiclePlate = "ABC1D23",
        occurredAt = 1795186800000L,
        location = "Praça de teste",
        audience = "Motorista",
        primaryTopic = "Cinto",
        additionalTopics = "[]",
        description = "RELATO DE TESTE: orientação educativa realizada.",
        agentNotes = "Observação exclusiva do relatório detalhado."
    ),
    citizen = CitizenEntity(id = 1, fullName = "Cidadão fictício para teste", normalizedName = "cidadao ficticio para teste"),
    vehicle = null,
    attachments = emptyList()
)
