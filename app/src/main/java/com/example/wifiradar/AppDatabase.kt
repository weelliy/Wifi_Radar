package com.example.wifiradar

import androidx.room.*

@Entity(tableName = "wifi_points")
data class WifiPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequency: Int,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val verticalAccuracy: Float,
    val estimatedDistance: Double,
    val floor: Int = 0, // <-- НОВОЕ ПОЛЕ: этаж
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface WifiPointDao {
    @Insert
    suspend fun insert(point: WifiPoint)

    @Query("SELECT * FROM wifi_points ORDER BY timestamp DESC")
    suspend fun getAll(): List<WifiPoint>

    @Query("DELETE FROM wifi_points")
    suspend fun clearAll()
}

@Database(entities = [WifiPoint::class], version = 2, exportSchema = false) // <-- Версия увеличена до 2
abstract class AppDatabase : RoomDatabase() {
    abstract fun wifiPointDao(): WifiPointDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: android.content.Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wifi_radar_db"
                )
                .fallbackToDestructiveMigration() // <-- Сносит старую базу и создает новую при смене версии
                .build().also { INSTANCE = it }
            }
    }
}
