package br.com.educatransito.reports

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import br.com.educatransito.data.AttachmentEntity
import br.com.educatransito.data.OrientationRecord
import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min

/** Generates portable PDFs using Android only. Caller owns the stream and runs this off the UI thread. */
class PdfReportWriter(context: Context) {
    private val mediaDirectory = File(context.applicationContext.filesDir, "attachments")
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))
        .withZone(ZoneId.systemDefault())

    fun write(output: OutputStream, records: List<OrientationRecord>, detailed: Boolean, periodLabel: String) {
        val summary = ReportSummary.from(records)
        val document = PdfDocument()
        try {
            val layout = ReportPages(document, if (detailed) "RELATÓRIO DETALHADO" else "RELATÓRIO SIMPLES")
            try {
                layout.heading("Histórico de orientações educativas")
                layout.field("Período", periodLabel)
                layout.field("Emitido em", dateFormat.format(Instant.now()))
                layout.field("Total de orientações", summary.total.toString())
                layout.paragraph("Contagens calculadas exclusivamente a partir dos registros salvos. Os totais por público representam atendimentos; uma mesma pessoa pode ter mais de um registro.", small = true)

                layout.heading("Público orientado")
                listOf("Motorista", "Motociclista", "Pedestre", "Ciclista").forEach {
                    layout.field(it, (summary.audienceCounts[it] ?: 0).toString())
                }
                summary.audienceCounts.filterKeys { it !in listOf("Motorista", "Motociclista", "Pedestre", "Ciclista") }
                    .forEach { (audience, count) -> layout.field(audience, count.toString()) }

                layout.heading("Orientações por categoria")
                if (summary.topicCounts.isEmpty()) layout.paragraph("Nenhuma orientação no período selecionado.")
                summary.topicCounts.forEach { (topic, count) -> layout.field(topic, count.toString()) }
                if (summary.total > 0) layout.paragraph("Uma ação pode conter várias categorias. Cada categoria é contada uma única vez por registro.", small = true)

                layout.heading(if (detailed) "Registros detalhados" else "Histórico cronológico")
                if (summary.total == 0) layout.paragraph("Nenhum registro salvo foi encontrado para este período.")
                summary.records.forEachIndexed { index, record ->
                    if (index > 0) layout.rule()
                    layout.subheading(record.orientation.protocol)
                    layout.field("Data e horário", dateFormat.format(Instant.ofEpochMilli(record.orientation.occurredAt)))
                    layout.field("Público", record.orientation.audience)
                    layout.field("Placa", record.orientation.vehiclePlate.orEmpty().ifBlank { "Não informada" })
                    layout.field("Orientação principal", record.orientation.primaryTopic)
                    if (detailed) writeDetails(layout, record)
                }
                layout.rule()
                layout.paragraph("Finalidade exclusivamente educativa e preventiva. O histórico de uma placa não comprova que o condutor atual recebeu as orientações anteriores.", small = true)
                layout.finish()
                document.writeTo(output)
                output.flush()
            } finally {
                // A failed output/copy must not leave a started native PDF page open.
                layout.finish()
            }
        } finally {
            document.close()
        }
    }

    private fun writeDetails(layout: ReportPages, record: OrientationRecord) {
        val item = record.orientation
        layout.field("Cidadão", record.citizen?.fullName.orEmpty().ifBlank { "Não informado" })
        record.citizen?.let { layout.field("Identificação local do cadastro", it.id.toString()) }
        record.citizen?.phone?.takeIf(String::isNotBlank)?.let { layout.field("Telefone", it) }
        layout.field("Marca", item.vehicleMake.ifBlank { "Não informada" })
        layout.field("Modelo", item.vehicleModel.ifBlank { "Não informado" })
        layout.field("Cor", item.vehicleColor.ifBlank { "Não informada" })
        layout.field("Local da abordagem", item.location)
        val additional = record.additionalTopicLabels.filter { it != item.primaryTopic }.distinct()
        if (additional.isNotEmpty()) layout.field("Orientações complementares", additional.joinToString("; "))
        layout.subheading("Relato")
        layout.paragraph(item.description.ifBlank { "Não informado." })
        if (item.agentNotes.isNotBlank()) {
            layout.subheading("Observações do agente")
            layout.paragraph(item.agentNotes)
        }
        layout.field("Recebimento assinado registrado", if (item.signatureReceived) "Sim" else "Não")
        layout.subheading("Anexos (${record.attachments.size})")
        if (record.attachments.isEmpty()) layout.paragraph("Nenhum anexo neste registro.")
        record.attachments.sortedWith(compareBy({ it.addedAt }, { it.id })).forEachIndexed { index, attachment ->
            layout.ensureSpace(70f)
            val image = attachment.mimeType.startsWith("image/")
            layout.field(if (image) "Imagem ${index + 1}" else "Vídeo ${index + 1}", attachment.displayName)
            layout.field("Tipo e tamanho", "${attachment.mimeType} • ${formatSize(attachment.sizeBytes)}")
            if (image) {
                val bitmap = readImage(attachment)
                if (bitmap == null) {
                    layout.paragraph("Imagem indisponível ou formato não compatível com a visualização no PDF.", small = true)
                } else {
                    try { layout.image(bitmap) } finally { bitmap.recycle() }
                }
            } else {
                layout.paragraph("Vídeo vinculado ao protocolo e armazenado no aplicativo. Abra o registro no histórico para reproduzi-lo. O arquivo de vídeo não é incorporado a este PDF.", small = true)
                if (localFile(attachment)?.isFile != true) layout.paragraph("Arquivo de vídeo não encontrado no armazenamento local.", small = true)
            }
        }
    }

    private fun localFile(attachment: AttachmentEntity): File? = runCatching {
        val file = File(mediaDirectory, attachment.localFileName).canonicalFile
        file.takeIf { it.parentFile == mediaDirectory.canonicalFile && it.isFile }
    }.getOrNull()

    private fun readImage(attachment: AttachmentEntity): Bitmap? {
        val file = localFile(attachment) ?: return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
            val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }) ?: return null
            val orientation = runCatching {
                ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            }
            if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024L * 1024 -> String.format(Locale("pt", "BR"), "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale("pt", "BR"), "%.1f KB", bytes / 1024.0)
        else -> "$bytes bytes"
    }
}

/** Text is wrapped before drawing; every line checks its page boundary, including long words. */
private class ReportPages(private val document: PdfDocument, private val reportTitle: String) {
    private var page: PdfDocument.Page? = null
    private var pageNumber = 0
    private var y = 0f
    private val left = 36f
    private val width = 523f
    private val bottom = 783f
    private val normal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(30, 41, 54)
        textSize = 10.5f
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val bold = Paint(normal).apply { typeface = Typeface.create("sans-serif", Typeface.BOLD) }
    private val small = Paint(normal).apply { textSize = 8.5f; color = Color.DKGRAY }
    private val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.6f }
    private val canvas: Canvas get() = requireNotNull(page).canvas

    init { newPage() }

    private fun newPage() {
        finish()
        pageNumber++
        page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
        canvas.drawText("EDUCATRÂNSITO", left, 39f, Paint(bold).apply { textSize = 16f })
        canvas.drawText(reportTitle, left, 57f, small)
        canvas.drawLine(left, 69f, left + width, 69f, linePaint)
        y = 83f
    }

    fun ensureSpace(height: Float) { if (y + height > bottom) newPage() }

    fun heading(text: String) {
        ensureSpace(55f)
        y += 9f
        drawTextBlock(text, Paint(bold).apply { textSize = 12.5f })
        y += 5f
    }

    fun subheading(text: String) {
        ensureSpace(44f)
        y += 6f
        drawTextBlock(text, bold)
        y += 3f
    }

    fun field(label: String, value: String) { paragraph("$label: $value") }

    fun paragraph(value: String, small: Boolean = false) {
        drawTextBlock(value, if (small) this.small else normal)
        y += if (small) 5f else 4f
    }

    private fun drawTextBlock(value: String, paint: Paint) {
        val metrics = paint.fontMetrics
        val lineHeight = ceil(metrics.descent - metrics.ascent + 3).toFloat()
        value.replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ').split('\n').forEach { paragraph ->
            if (paragraph.isEmpty()) {
                ensureSpace(lineHeight)
                y += lineHeight
            } else {
                var remaining = paragraph
                while (remaining.isNotEmpty()) {
                    var count = paint.breakText(remaining, true, width, null).coerceAtLeast(1)
                    if (count < remaining.length) {
                        if (Character.isHighSurrogate(remaining[count - 1])) count--
                        val breakAt = remaining.lastIndexOf(' ', count - 1)
                        if (breakAt > 0) count = breakAt
                    }
                    if (count <= 0) count = Character.charCount(remaining.codePointAt(0))
                    val line = remaining.substring(0, count).trimEnd()
                    ensureSpace(lineHeight)
                    canvas.drawText(line, left, y - metrics.ascent, paint)
                    y += lineHeight
                    remaining = remaining.substring(count).trimStart()
                }
            }
        }
    }

    fun rule() {
        ensureSpace(30f)
        y += 8f
        canvas.drawLine(left, y, left + width, y, linePaint)
        y += 8f
    }

    fun image(bitmap: Bitmap) {
        val scale = min(width / bitmap.width, 250f / bitmap.height)
        val imageWidth = bitmap.width * scale
        val imageHeight = bitmap.height * scale
        ensureSpace(imageHeight + 12f)
        canvas.drawBitmap(bitmap, null, RectF(left, y, left + imageWidth, y + imageHeight), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        y += imageHeight + 12f
    }

    fun finish() {
        val active = page ?: return
        active.canvas.drawLine(left, 800f, left + width, 800f, linePaint)
        active.canvas.drawText("Registro de ação educativa • não substitui autuação", left, 815f, small)
        val number = "Página $pageNumber"
        active.canvas.drawText(number, left + width - small.measureText(number), 815f, small)
        document.finishPage(active)
        page = null
    }
}
