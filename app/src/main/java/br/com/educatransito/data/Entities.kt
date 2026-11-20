package br.com.educatransito.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "citizens", indices = [Index("normalizedName")])
data class CitizenEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fullName: String,
    val normalizedName: String,
    val phone: String? = null
)

@Entity(tableName = "vehicles", indices = [Index(value = ["plate"], unique = true)])
data class VehicleEntity(
    @PrimaryKey val plate: String,
    val make: String = "",
    val model: String = "",
    val color: String = ""
)

@Entity(tableName = "orientations", indices = [Index(value = ["protocol"], unique = true), Index("vehiclePlate"), Index("citizenId"), Index("occurredAt")])
data class OrientationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val protocol: String,
    val protocolYear: Int,
    val protocolSequence: Long,
    val citizenId: Long?,
    val vehiclePlate: String?,
    val occurredAt: Long,
    val location: String,
    val audience: String,
    val primaryTopic: String,
    val additionalTopics: String,
    val description: String = "",
    val agentNotes: String = "",
    val signatureReceived: Boolean = false,
    val deletedAt: Long? = null
)

@Entity(tableName = "audit_log", indices = [Index("orientationId"), Index("changedAt")])
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orientationId: Long,
    val changedAt: Long,
    val action: String,
    val beforeJson: String?,
    val afterJson: String?,
    val reason: String
)

@Entity(tableName = "protocol_counters")
data class ProtocolCounterEntity(@PrimaryKey val year: Int, val lastSequence: Long)
