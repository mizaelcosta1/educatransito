package br.com.educatransito.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CitizenEntity::class, VehicleEntity::class, OrientationEntity::class, AuditLogEntity::class, ProtocolCounterEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun orientationDao(): OrientationDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "educatransito.db")
                .build().also { INSTANCE = it }
        }
    }
}
