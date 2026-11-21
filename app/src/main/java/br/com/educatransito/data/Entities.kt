package br.com.educatransito.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Ignore
import androidx.room.PrimaryKey
import androidx.room.Relation
import org.json.JSONArray

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
    val deletedAt: Long? = null,
    @ColumnInfo(defaultValue = "''") val vehicleMake: String = "",
    @ColumnInfo(defaultValue = "''") val vehicleModel: String = "",
    @ColumnInfo(defaultValue = "''") val vehicleColor: String = ""
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

/** Media bytes live in filesDir/attachments; only safe relative filenames enter the database. */
@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(
        entity = OrientationEntity::class,
        parentColumns = ["id"], childColumns = ["orientationId"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index("orientationId"), Index(value = ["localFileName"], unique = true)]
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val orientationId: Long,
    val localFileName: String,
    val mimeType: String,
    val displayName: String,
    val sizeBytes: Long,
    val addedAt: Long
)

data class OrientationRecord(
    @Embedded val orientation: OrientationEntity,
    @Relation(parentColumn = "citizenId", entityColumn = "id")
    val citizen: CitizenEntity?,
    @Relation(parentColumn = "vehiclePlate", entityColumn = "plate")
    val vehicle: VehicleEntity?,
    @Relation(parentColumn = "id", entityColumn = "orientationId")
    val attachments: List<AttachmentEntity>
) {
    @get:Ignore
    val additionalTopicLabels: List<String>
        get() {
            if (orientation.additionalTopics.isBlank()) return emptyList()
            return try {
                val values = JSONArray(orientation.additionalTopics)
                (0 until values.length()).map { values.getString(it) }
            } catch (_: Exception) {
                orientation.additionalTopics.lineSequence().filter(String::isNotBlank).toList()
            }
        }
}
