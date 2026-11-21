package br.com.educatransito.media

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import br.com.educatransito.data.AttachmentPolicy
import br.com.educatransito.data.PendingAttachment
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Copies user-selected documents into private storage, so the record remains available offline. */
class AttachmentStore(private val context: Context) {
    private val directory = File(context.filesDir, "attachments")

    suspend fun import(uri: Uri, kind: String): PendingAttachment = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "Escolha um arquivo pelo seletor do Android." }
        require(kind == "image" || kind == "video")
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)?.lowercase(Locale.ROOT)
            ?: throw IllegalArgumentException("Não foi possível identificar o tipo do arquivo.")
        require(mime.startsWith("$kind/")) { "Selecione ${if (kind == "image") "uma imagem" else "um vídeo"}." }
        val maxBytes = AttachmentPolicy.maxBytes(mime)
        var displayName = if (kind == "image") "Imagem" else "Vídeo"
        var reportedSize = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameColumn >= 0 && !cursor.isNull(nameColumn)) displayName = cursor.getString(nameColumn)
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) reportedSize = cursor.getLong(sizeColumn)
            }
        }
        require(reportedSize <= maxBytes) { sizeError(kind) }
        displayName = displayName.filter { !it.isISOControl() && it != '/' && it != '\\' }.take(160).ifBlank { "Anexo" }
        check(directory.isDirectory || directory.mkdirs()) { "Não foi possível preparar o armazenamento dos anexos." }
        val id = UUID.randomUUID().toString()
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }
            ?: if (kind == "image") "img" else "video"
        val destination = File(directory, "$id.$extension")
        var total = 0L
        try {
            resolver.openInputStream(uri)?.use { input ->
                destination.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= maxBytes) { sizeError(kind) }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: throw IOException("Não foi possível ler o arquivo selecionado.")
            require(total > 0) { "O arquivo selecionado está vazio." }
            if (kind == "image") {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(destination.path, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Imagem inválida ou em formato não suportado pelo aparelho." }
            } else {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(destination.path)
                    require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") {
                        "Vídeo inválido ou em formato não suportado pelo aparelho. Tente um arquivo MP4."
                    }
                } finally {
                    retriever.release()
                }
            }
            PendingAttachment(id, destination.name, mime, displayName, total)
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }

    fun file(localFileName: String): File {
        require(localFileName.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,199}"))) { "Anexo inválido." }
        return File(directory, localFileName).also {
            require(it.canonicalFile.parentFile == directory.canonicalFile) { "Anexo inválido." }
        }
    }

    suspend fun discard(localFileName: String) = withContext(Dispatchers.IO) { file(localFileName).delete(); Unit }

    private fun sizeError(kind: String) = if (kind == "image")
        "A imagem deve ter até 20 MB." else "O vídeo deve ter até 200 MB."
}
