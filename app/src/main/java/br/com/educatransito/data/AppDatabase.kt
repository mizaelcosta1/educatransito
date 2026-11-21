package br.com.educatransito.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CitizenEntity::class, VehicleEntity::class, OrientationEntity::class, AuditLogEntity::class, ProtocolCounterEntity::class, AttachmentEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun orientationDao(): OrientationDao
    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orientations ADD COLUMN vehicleMake TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE orientations ADD COLUMN vehicleModel TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE orientations ADD COLUMN vehicleColor TEXT NOT NULL DEFAULT ''")
                db.execSQL("""
                    UPDATE orientations SET
                        vehicleMake = COALESCE((SELECT make FROM vehicles WHERE plate = orientations.vehiclePlate), ''),
                        vehicleModel = COALESCE((SELECT model FROM vehicles WHERE plate = orientations.vehiclePlate), ''),
                        vehicleColor = COALESCE((SELECT color FROM vehicles WHERE plate = orientations.vehiclePlate), '')
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS attachments (
                        id TEXT NOT NULL PRIMARY KEY,
                        orientationId INTEGER NOT NULL,
                        localFileName TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        sizeBytes INTEGER NOT NULL,
                        addedAt INTEGER NOT NULL,
                        FOREIGN KEY(orientationId) REFERENCES orientations(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_attachments_orientationId ON attachments (orientationId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_attachments_localFileName ON attachments (localFileName)")
            }
        }

        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "educatransito.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { INSTANCE = it }
        }
    }
}
