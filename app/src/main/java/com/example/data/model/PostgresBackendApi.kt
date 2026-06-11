package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class PostgresDbInfo(
    @Json(name = "host") val host: String,
    @Json(name = "port") val port: Int,
    @Json(name = "database") val database: String,
    @Json(name = "status") val status: String, // "CONNECTED", "AUTHENTICATED", "OFFLINE"
    @Json(name = "total_series_tables") val totalSeriesTables: Int,
    @Json(name = "total_records") val totalRecords: Long
)

@JsonClass(generateAdapter = true)
data class PostgresSyncRequest(
    @Json(name = "series_ids") val seriesIds: List<String>,
    @Json(name = "fetch_historical") val fetchHistorical: Boolean = true
)

@JsonClass(generateAdapter = true)
data class PostgresSeriesDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "category") val category: String,
    @Json(name = "description") val description: String,
    @Json(name = "significance") val significance: String,
    @Json(name = "unit") val unit: String
)

@JsonClass(generateAdapter = true)
data class PostgresDataPointDto(
    @Json(name = "series_id") val seriesId: String,
    @Json(name = "year") val year: String,
    @Json(name = "period") val period: String,
    @Json(name = "period_name") val periodName: String,
    @Json(name = "value") val value: Double
)

@JsonClass(generateAdapter = true)
data class PostgresSyncResponse(
    @Json(name = "status") val status: String,
    @Json(name = "rows_affected") val rowsAffected: Int,
    @Json(name = "message") val message: String,
    @Json(name = "series") val series: List<PostgresSeriesDto>,
    @Json(name = "data_points") val dataPoints: List<PostgresDataPointDto>
)

interface PostgresBackendApiService {
    @GET("postgres/status")
    suspend fun getPostgresStatus(): PostgresDbInfo

    @POST("postgres/sync")
    suspend fun triggerPostgresSync(
        @Body request: PostgresSyncRequest
    ): PostgresSyncResponse
}
