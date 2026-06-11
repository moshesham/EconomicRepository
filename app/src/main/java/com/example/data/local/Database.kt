package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "series")
data class SeriesEntity(
    @PrimaryKey val id: String, // BLS Series ID, e.g. "LNS14000000"
    val name: String,
    val category: String,
    val description: String,
    val significance: String,
    val unit: String
)

@Entity(
    tableName = "data_points",
    primaryKeys = ["seriesId", "year", "period", "isCustom"]
)
data class DataPointEntity(
    val seriesId: String,
    val year: String,
    val period: String, // e.g. "M01", "Q01"
    val periodName: String, // e.g. "January"
    val value: Double,
    val isCustom: Boolean = false, // True if added manually by user for forecasting
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class SettingsEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Dao
interface SeriesDao {
    @Query("SELECT * FROM series")
    fun getAllSeries(): Flow<List<SeriesEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeries(series: List<SeriesEntity>)

    @Query("SELECT * FROM series WHERE id = :seriesId LIMIT 1")
    suspend fun getSeriesById(seriesId: String): SeriesEntity?
}

@Dao
interface DataPointDao {
    @Query("SELECT * FROM data_points WHERE seriesId = :seriesId ORDER BY year ASC, period ASC")
    fun getDataPointsForSeries(seriesId: String): Flow<List<DataPointEntity>>

    @Query("SELECT * FROM data_points WHERE seriesId = :seriesId ORDER BY year ASC, period ASC")
    suspend fun getDataPointsForSeriesList(seriesId: String): List<DataPointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDataPoints(dataPoints: List<DataPointEntity>)

    @Query("DELETE FROM data_points WHERE seriesId = :seriesId AND isCustom = 1")
    suspend fun clearCustomData(seriesId: String)

    @Query("DELETE FROM data_points WHERE seriesId = :seriesId AND isCustom = 0")
    suspend fun clearFetchedData(seriesId: String)

    @Delete
    suspend fun deleteDataPoint(dataPoint: DataPointEntity)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): SettingsEntity?

    @Query("SELECT * FROM app_settings WHERE `key` = :key LIMIT 1")
    fun observeSetting(key: String): Flow<SettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSetting(setting: SettingsEntity)
}

@Database(
    entities = [SeriesEntity::class, DataPointEntity::class, SettingsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun seriesDao(): SeriesDao
    abstract fun dataPointDao(): DataPointDao
    abstract fun settingsDao(): SettingsDao
}
