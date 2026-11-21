package br.com.educatransito

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import br.com.educatransito.data.*
import br.com.educatransito.media.AttachmentStore
import br.com.educatransito.reports.PdfReportWriter
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class OrientationViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val repository = OrientationRepository(AppDatabase.get(application))
    val media = AttachmentStore(application)
    var page by mutableStateOf(savedState.get<String>("page") ?: "home")
        private set
    var selectedId by mutableStateOf(savedState.get<Long>("selectedId") ?: 0L)
        private set
    var selectedRecord by mutableStateOf<OrientationRecord?>(null)
        private set
    var draft by mutableStateOf(readDraft(savedState.get<String>("draft")))
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var loaded by mutableStateOf(false)
        private set
    var reportToCreate by mutableStateOf<String?>(null)
        private set
    var mediaToCreate by mutableStateOf<Pair<String, String>?>(null)
        private set
    var lastPdfUri by mutableStateOf<Uri?>(null)
        private set
    val records = repository.observeAll().onEach { loaded = true }.catch {
        loaded = true
        message = "Não foi possível abrir o histórico local. Feche e abra o aplicativo."
        emit(emptyList())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        if (selectedId > 0) viewModelScope.launch {
            try {
                selectedRecord = repository.getRecord(selectedId)
                if (selectedRecord == null && page == "detail") {
                    navigate("history")
                    message = "A orientação selecionada não está disponível."
                }
            }
            catch (error: Exception) { handleError(error, "Não foi possível carregar a orientação.") }
        }
    }

    fun navigate(destination: String) {
        if (busy != null) return
        page = destination
        savedState["page"] = destination
    }

    fun openRecord(record: OrientationRecord) {
        selectedRecord = record
        selectedId = record.orientation.id
        savedState["selectedId"] = selectedId
        navigate("detail")
    }

    fun updateDraft(transform: (OrientationDraft) -> OrientationDraft) {
        if (busy != null) return
        draft = transform(draft)
        savedState["draft"] = draftJson(draft).toString()
    }

    fun clearMessage() { message = null }
    fun showMessage(text: String) { message = text }

    fun removeDraftAttachment(id: String) {
        if (busy != null) return
        val attachment = draft.attachments.firstOrNull { it.id == id } ?: return
        updateDraft { it.copy(attachments = it.attachments.filterNot { entry -> entry.id == id }) }
        viewModelScope.launch { media.discard(attachment.localFileName) }
    }

    fun discardDraft() {
        if (busy != null) return
        val oldAttachments = draft.attachments
        updateDraft { OrientationDraft() }
        viewModelScope.launch { oldAttachments.forEach { media.discard(it.localFileName) } }
    }

    fun prepareMediaPicker(kind: String, orientationId: Long? = null): Boolean {
        if (busy != null) return false
        val count = if (orientationId == null) draft.attachments.size else
            records.value.firstOrNull { it.orientation.id == orientationId }?.attachments?.size
                ?: selectedRecord?.attachments?.size ?: 0
        if (count >= AttachmentPolicy.MAX_COUNT) {
            message = "Cada orientação pode ter até 10 anexos."
            return false
        }
        savedState["mediaKind"] = kind
        savedState["mediaTarget"] = orientationId ?: 0L
        return true
    }

    fun importSelectedMedia(uri: Uri?) {
        if (uri == null) return
        if (busy != null) return
        val kind = savedState.get<String>("mediaKind") ?: return
        val target = savedState.get<Long>("mediaTarget") ?: 0L
        busy = "Copiando anexo para uso offline…"
        viewModelScope.launch {
            var imported: PendingAttachment? = null
            try {
                imported = media.import(uri, kind)
                if (target > 0) {
                    selectedRecord = repository.addAttachments(target, listOf(imported))
                    message = "Anexo salvo no protocolo."
                } else {
                    require(draft.attachments.size < AttachmentPolicy.MAX_COUNT) { "Cada orientação pode ter até 10 anexos." }
                    draft = draft.copy(attachments = draft.attachments + imported)
                    savedState["draft"] = draftJson(draft).toString()
                    message = "Anexo adicionado. Toque em Salvar orientação para concluir."
                }
            } catch (error: Exception) {
                // Cancellation can arrive after a transaction commits. Never remove referenced media.
                imported?.let { attachment -> withContext(NonCancellable) {
                    val referenced = try {
                        if (target > 0) repository.getRecord(target)?.attachments?.any { it.id == attachment.id } == true
                        else draft.attachments.any { it.id == attachment.id }
                    } catch (_: Exception) { true }
                    if (!referenced) media.discard(attachment.localFileName)
                } }
                handleError(error, "Não foi possível anexar o arquivo. Verifique o espaço disponível e tente novamente.")
            } finally {
                busy = null
                savedState.remove<String>("mediaKind")
                savedState.remove<Long>("mediaTarget")
            }
        }
    }

    /** One transaction creates the orientation, protocol, audit and attachment references. */
    fun save(reportDetailed: Boolean? = null) {
        if (busy != null) return
        busy = "Salvando orientação…"
        val snapshot = draft
        viewModelScope.launch {
            try {
                snapshot.attachments.forEach {
                    require(media.file(it.localFileName).isFile) { "Um anexo não está disponível. Remova-o e anexe novamente." }
                }
                val record = repository.save(snapshot)
                draft = OrientationDraft()
                savedState.remove<String>("draft")
                selectedId = record.orientation.id
                selectedRecord = record
                savedState["selectedId"] = selectedId
                page = "detail"
                savedState["page"] = page
                message = "Orientação salva: " + record.orientation.protocol
                if (reportDetailed != null) requestReport(listOf(record), reportDetailed, "Orientação " + record.orientation.protocol, allowBusy = true)
            } catch (error: Exception) {
                handleError(error, "Não foi possível salvar. Seus campos e anexos foram mantidos para tentar novamente.")
            } finally { busy = null }
        }
    }

    fun requestReport(selected: List<OrientationRecord>, detailed: Boolean, label: String, allowBusy: Boolean = false) {
        if (busy != null && !allowBusy) return
        if (selected.isEmpty()) { message = "Nenhuma orientação salva no período selecionado."; return }
        val request = JSONObject()
            .put("ids", JSONArray(selected.map { it.orientation.id }))
            .put("detailed", detailed).put("label", label)
        savedState["pendingReport"] = request.toString()
        val suffix = if (detailed) "detalhado" else "simples"
        reportToCreate = if (selected.size == 1) selected.first().orientation.protocol + "-" + suffix + ".pdf"
            else "EDUCATRANSITO-relatorio-" + suffix + ".pdf"
    }

    fun reportPickerLaunched() { reportToCreate = null }
    fun mediaCopyPickerLaunched() { mediaToCreate = null }

    fun exportPdf(destination: Uri?) {
        val request = savedState.get<String>("pendingReport") ?: return
        savedState.remove<String>("pendingReport")
        if (destination == null) {
            message = "Exportação cancelada. A orientação continua salva no histórico."
            return
        }
        busy = "Gerando e salvando PDF…"
        viewModelScope.launch {
            var temporary: File? = null
            try {
                withContext(Dispatchers.IO) {
                    val json = JSONObject(request)
                    val ids = json.getJSONArray("ids")
                    val selected = (0 until ids.length()).map {
                        repository.getRecord(ids.getLong(it)) ?: throw IOException("Registro não disponível.")
                    }
                    val folder = File(getApplication<Application>().cacheDir, "reports").apply { mkdirs() }
                    temporary = File(folder, UUID.randomUUID().toString() + ".pdf")
                    temporary!!.outputStream().use {
                        PdfReportWriter(getApplication()).write(it, selected, json.getBoolean("detailed"), json.getString("label"))
                    }
                    getApplication<Application>().contentResolver.openOutputStream(destination, "w")?.use { output ->
                        temporary!!.inputStream().use { input -> input.copyTo(output) }
                    } ?: throw IOException("Destino indisponível.")
                }
                lastPdfUri = destination
                message = "Relatório PDF salvo no local escolhido."
            } catch (error: Exception) {
                handleError(error, "Não foi possível salvar o PDF. Verifique o espaço e escolha outro local.")
            } finally {
                withContext(Dispatchers.IO) { temporary?.delete() }
                busy = null
            }
        }
    }

    fun requestAttachmentCopy(attachment: AttachmentEntity) {
        if (busy != null) return
        savedState["pendingMediaFile"] = attachment.localFileName
        mediaToCreate = attachment.displayName to attachment.mimeType
    }

    fun exportAttachment(destination: Uri?) {
        val name = savedState.get<String>("pendingMediaFile") ?: return
        savedState.remove<String>("pendingMediaFile")
        if (destination == null) return
        busy = "Salvando cópia do anexo…"
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(destination, "w")?.use { output ->
                        media.file(name).inputStream().use { input -> input.copyTo(output) }
                    } ?: throw IOException("Destino indisponível.")
                }
                message = "Cópia do anexo salva no local escolhido."
            } catch (error: Exception) {
                handleError(error, "Não foi possível salvar a cópia. O anexo original continua no aplicativo.")
            } finally { busy = null }
        }
    }

    private fun handleError(error: Exception, fallback: String) {
        if (error is CancellationException) throw error
        message = if (error is IllegalArgumentException) error.message ?: fallback else fallback
    }
}

private fun draftJson(value: OrientationDraft): JSONObject = JSONObject().apply {
    put("plate", value.plate); put("make", value.make); put("model", value.model); put("color", value.color)
    put("citizenName", value.citizenName); put("location", value.location); put("audience", value.audience)
    put("primaryTopic", value.primaryTopic); put("additionalTopics", JSONArray(value.additionalTopics))
    put("description", value.description); put("agentNotes", value.agentNotes)
    put("attachments", JSONArray().apply { value.attachments.forEach { attachment ->
        put(JSONObject().put("id", attachment.id).put("localFileName", attachment.localFileName)
            .put("mimeType", attachment.mimeType).put("displayName", attachment.displayName).put("sizeBytes", attachment.sizeBytes))
    } })
}

private fun readDraft(raw: String?): OrientationDraft = try {
    if (raw == null) OrientationDraft() else {
        val value = JSONObject(raw)
        val topics = value.optJSONArray("additionalTopics") ?: JSONArray()
        val attachments = value.optJSONArray("attachments") ?: JSONArray()
        OrientationDraft(
            plate = value.optString("plate"), make = value.optString("make"), model = value.optString("model"),
            color = value.optString("color"), citizenName = value.optString("citizenName"),
            location = value.optString("location"), audience = value.optString("audience", "Motorista"),
            primaryTopic = value.optString("primaryTopic"), description = value.optString("description"),
            agentNotes = value.optString("agentNotes"),
            additionalTopics = (0 until topics.length()).map { topics.getString(it) },
            attachments = (0 until attachments.length()).map {
                val attachment = attachments.getJSONObject(it)
                PendingAttachment(attachment.getString("id"), attachment.getString("localFileName"),
                    attachment.getString("mimeType"), attachment.getString("displayName"), attachment.getLong("sizeBytes"))
            }
        )
    }
} catch (_: Exception) { OrientationDraft() }
