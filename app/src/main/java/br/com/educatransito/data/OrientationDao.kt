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

    @Insert
    abstract suspend fun insertOrientation(value: OrientationEntity): Long

    @Insert
    abstract suspend fun insertCitizen(value: CitizenEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveVehicle(value: VehicleEntity)

    @Insert
    abstract suspend fun insertAudit(value: AuditLogEntity)

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
        val next = (counter(year)?.lastSequence ?: 0L) + 1L
        saveCounter(ProtocolCounterEntity(year, next))
        return next
    }
}
