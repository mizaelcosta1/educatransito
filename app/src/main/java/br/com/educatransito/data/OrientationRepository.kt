package br.com.educatransito.data

import androidx.room.withTransaction
import br.com.educatransito.domain.ProtocolGenerator
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

/** A media import is completed on disk before it is eligible for a database transaction. */
data class PendingAttachment(
    val id: String,
    val localFileName: String,
    val mimeType: String,
    val displayName: String,
    val sizeBytes: Long
)

object AttachmentPolicy {
    const val MAX_COUNT = 10
    const val MAX_IMAGE_BYTES = 20L * 1024 * 1024
    const val MAX_VIDEO_BYTES = 200L * 1024 * 1024

    fun maxBytes(mimeType: String): Long = when {
        mimeType.startsWith("image/") -> MAX_IMAGE_BYTES
        mimeType.startsWith("video/") -> MAX_VIDEO_BYTES
        else -> throw IllegalArgumentException("Selecione uma imagem ou um vídeo.")
    }

    fun validate(attachments: List<PendingAttachment>, existingCount: Int = 0) {
        require(existingCount + attachments.size <= MAX_COUNT) {
            "Cada orientação pode conter até $MAX_COUNT anexos."
        }
        require(attachments.map { it.id }.distinct().size == attachments.size) { "Há anexos repetidos." }
        require(attachments.map { it.localFileName }.distinct().size == attachments.size) { "Há anexos repetidos." }
        attachments.forEach { attachment ->
            require(attachment.id.isNotBlank() && attachment.id.length <= 100) { "Identificação do anexo inválida." }
            require(attachment.localFileName.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,199}"))) {
                "Nome interno do anexo inválido."
            }
            require(attachment.displayName.isNotBlank() && attachment.displayName.length <= 255) {
                "Nome do anexo inválido."
            }
            val maximum = maxBytes(attachment.mimeType)
            require(attachment.sizeBytes in 1..maximum) {
                "O anexo está vazio ou excede o limite de ${maximum / (1024 * 1024)} MB."
            }
        }
    }
}

data class OrientationDraft(
    val plate: String = "",
    val make: String = "",
    val model: String = "",
    val color: String = "",
    val citizenName: String = "",
    val location: String = "",
    val audience: String = "Motorista",
    val primaryTopic: String = "",
    val additionalTopics: List<String> = emptyList(),
    val description: String = "",
    val agentNotes: String = "",
    val attachments: List<PendingAttachment> = emptyList()
) {
    fun validate() {
        val normalizedPlate = ProtocolGenerator.normalizePlate(plate)
        require(normalizedPlate.isEmpty() || normalizedPlate.matches(Regex("[A-Z]{3}[0-9][A-Z0-9][0-9]{2}"))) {
            "Informe uma placa válida, como ABC1234 ou ABC1D23."
        }
        require(location.isNotBlank()) { "Informe o local da abordagem." }
        require(primaryTopic.isNotBlank()) { "Selecione a orientação principal." }
        require(description.isNotBlank()) { "Escreva o relato da orientação." }
        require(audience in listOf("Motorista", "Motociclista", "Pedestre", "Ciclista")) {
            "Selecione o público orientado."
        }
        require(citizenName.length <= 200) { "O nome deve ter até 200 caracteres." }
        require(location.length <= 300) { "O local deve ter até 300 caracteres." }
        require(make.length <= 100 && model.length <= 100 && color.length <= 100) { "Os dados do veículo devem ter até 100 caracteres por campo." }
        require(primaryTopic.length <= 300 && additionalTopics.size <= 30 && additionalTopics.all { it.length <= 300 }) {
            "Revise os tipos de orientação selecionados."
        }
        require(description.length <= 20_000) { "O relato deve ter até 20.000 caracteres." }
        require(agentNotes.length <= 10_000) { "As observações devem ter até 10.000 caracteres." }
        AttachmentPolicy.validate(attachments)
    }
}

/** The outer transaction owns both protocol allocation and every dependent record. */
class OrientationRepository(
    private val database: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val dao = database.orientationDao()

    fun observeAll(): Flow<List<OrientationRecord>> = dao.observeAllRecords()

    suspend fun getRecord(id: Long): OrientationRecord? = dao.getRecord(id)

    suspend fun getByProtocol(protocol: String): OrientationRecord? =
        dao.getRecordByProtocol(protocol.trim().uppercase(Locale.ROOT))

    suspend fun save(draft: OrientationDraft): OrientationRecord {
        draft.validate()
        val timestamp = now()
        val year = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).year
        val plate = ProtocolGenerator.normalizePlate(draft.plate).ifBlank { null }
        return database.withTransaction {
            val sequence = dao.nextProtocolSequence(year)
            val protocol = ProtocolGenerator.format(year, sequence)
            // A name alone never proves that two encounters involve the same person.
            val citizenId = draft.citizenName.trim().takeIf { it.isNotEmpty() }?.let { name ->
                dao.insertCitizen(CitizenEntity(fullName = name, normalizedName = normalizeName(name)))
            }
            if (plate != null) {
                dao.saveVehicle(VehicleEntity(plate, draft.make.trim(), draft.model.trim(), draft.color.trim()))
            }
            val orientation = OrientationEntity(
                protocol = protocol,
                protocolYear = year,
                protocolSequence = sequence,
                citizenId = citizenId,
                vehiclePlate = plate,
                occurredAt = timestamp,
                location = draft.location.trim(),
                audience = draft.audience,
                primaryTopic = draft.primaryTopic.trim(),
                additionalTopics = JSONArray(draft.additionalTopics.map(String::trim)
                    .filter { it.isNotEmpty() && it != draft.primaryTopic.trim() }.distinct()).toString(),
                description = draft.description.trim(),
                agentNotes = draft.agentNotes.trim(),
                vehicleMake = draft.make.trim(),
                vehicleModel = draft.model.trim(),
                vehicleColor = draft.color.trim()
            )
            val id = dao.insertOrientation(orientation)
            dao.insertAttachments(draft.attachments.map { it.forOrientation(id, timestamp) })
            val record = checkNotNull(dao.getRecord(id))
            dao.insertAudit(AuditLogEntity(
                orientationId = id,
                changedAt = timestamp,
                action = "CREATE",
                beforeJson = null,
                afterJson = record.auditJson(),
                reason = "Cadastro de orientação educativa"
            ))
            record
        }
    }

    /** Adding media later leaves the original protocol and report untouched and creates an audit entry. */
    suspend fun addAttachments(id: Long, attachments: List<PendingAttachment>): OrientationRecord {
        require(attachments.isNotEmpty()) { "Selecione pelo menos um anexo." }
        return database.withTransaction {
            val before = requireNotNull(dao.getRecord(id)) { "Orientação não encontrada." }
            AttachmentPolicy.validate(attachments, before.attachments.size)
            val timestamp = now()
            dao.insertAttachments(attachments.map { it.forOrientation(id, timestamp) })
            val after = checkNotNull(dao.getRecord(id))
            dao.insertAudit(AuditLogEntity(
                orientationId = id,
                changedAt = timestamp,
                action = "ADD_ATTACHMENTS",
                beforeJson = before.auditJson(),
                afterJson = after.auditJson(),
                reason = "Inclusão de ${attachments.size} anexo(s) após o relato"
            ))
            after
        }
    }

    companion object {
        fun normalizeName(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.ROOT)
    }
}

private fun PendingAttachment.forOrientation(orientationId: Long, timestamp: Long) = AttachmentEntity(
    id = id,
    orientationId = orientationId,
    localFileName = localFileName,
    mimeType = mimeType,
    displayName = displayName,
    sizeBytes = sizeBytes,
    addedAt = timestamp
)

private fun OrientationRecord.auditJson(): String = JSONObject().apply {
    put("id", orientation.id)
    put("protocol", orientation.protocol)
    put("occurredAt", orientation.occurredAt)
    put("citizenId", citizen?.id ?: JSONObject.NULL)
    put("citizenName", citizen?.fullName ?: JSONObject.NULL)
    put("vehiclePlate", orientation.vehiclePlate ?: JSONObject.NULL)
    put("vehicleMake", orientation.vehicleMake)
    put("vehicleModel", orientation.vehicleModel)
    put("vehicleColor", orientation.vehicleColor)
    put("location", orientation.location)
    put("audience", orientation.audience)
    put("primaryTopic", orientation.primaryTopic)
    put("additionalTopics", JSONArray(additionalTopicLabels))
    put("description", orientation.description)
    put("agentNotes", orientation.agentNotes)
    put("attachments", JSONArray(attachments.map { attachment ->
        JSONObject().apply {
            put("id", attachment.id)
            put("localFileName", attachment.localFileName)
            put("displayName", attachment.displayName)
            put("mimeType", attachment.mimeType)
            put("sizeBytes", attachment.sizeBytes)
            put("addedAt", attachment.addedAt)
        }
    }))
}.toString()
