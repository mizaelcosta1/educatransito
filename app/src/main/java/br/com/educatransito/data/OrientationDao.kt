package br.com.educatransito.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class OrientationDao {
    @Query("SELECT * FROM protocol_counters WHERE year = :year")
    protected abstract suspend fun counter(year: Int): ProtocolCounterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun saveCounter(value: ProtocolCounterEntity)

    @Query("SELECT COALESCE(MAX(protocolSequence), 0) FROM orientations WHERE protocolYear = :year")
    protected abstract suspend fun greatestSequence(year: Int): Long

    @Insert
    abstract suspend fun insertOrientation(value: OrientationEntity): Long

    @Insert
    abstract suspend fun insertCitizen(value: CitizenEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveVehicle(value: VehicleEntity)

    @Insert
    abstract suspend fun insertAudit(value: AuditLogEntity)

    @Insert
    abstract suspend fun insertAttachments(values: List<AttachmentEntity>)

    @Transaction
    @Query("SELECT * FROM orientations WHERE deletedAt IS NULL ORDER BY occurredAt DESC, id DESC")
    abstract fun observeAllRecords(): Flow<List<OrientationRecord>>

    @Transaction
    @Query("SELECT * FROM orientations WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    abstract suspend fun getRecord(id: Long): OrientationRecord?

    @Transaction
    @Query("SELECT * FROM orientations WHERE protocol = :protocol AND deletedAt IS NULL LIMIT 1")
    abstract suspend fun getRecordByProtocol(protocol: String): OrientationRecord?

    @Query("SELECT * FROM audit_log WHERE orientationId = :orientationId ORDER BY changedAt, id")
    abstract suspend fun auditFor(orientationId: Long): List<AuditLogEntity>

    @Query("SELECT * FROM orientations WHERE deletedAt IS NULL AND vehiclePlate = :plate ORDER BY occurredAt DESC")
    abstract fun historyByPlate(plate: String): Flow<List<OrientationEntity>>

    @Query("SELECT * FROM orientations WHERE protocol = :protocol AND deletedAt IS NULL LIMIT 1")
    abstract suspend fun byProtocol(protocol: String): OrientationEntity?

    @Query("SELECT * FROM orientations WHERE deletedAt IS NULL AND citizenId = :citizenId ORDER BY occurredAt DESC")
    abstract fun historyByCitizen(citizenId: Long): Flow<List<OrientationEntity>>

    @Query("SELECT * FROM orientations WHERE deletedAt IS NULL AND occurredAt BETWEEN :start AND :end ORDER BY occurredAt")
    abstract fun reportPeriod(start: Long, end: Long): Flow<List<OrientationEntity>>

    @Transaction
    open suspend fun nextProtocolSequence(year: Int): Long {
        val last = maxOf(counter(year)?.lastSequence ?: 0L, greatestSequence(year))
        val next = Math.addExact(last, 1L)
        saveCounter(ProtocolCounterEntity(year, next))
        return next
    }
}
