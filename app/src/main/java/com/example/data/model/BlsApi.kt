package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.POST

@JsonClass(generateAdapter = true)
data class BlsRequest(
    @Json(name = "seriesid") val seriesid: List<String>,
    @Json(name = "startyear") val startyear: String,
    @Json(name = "endyear") val endyear: String,
    @Json(name = "registrationkey") val registrationkey: String? = null
)

@JsonClass(generateAdapter = true)
data class BlsResponse(
    @Json(name = "status") val status: String,
    @Json(name = "responseTime") val responseTime: Int? = null,
    @Json(name = "message") val message: List<String>? = null,
    @Json(name = "Results") val Results: BlsResults? = null
)

@JsonClass(generateAdapter = true)
data class BlsResults(
    @Json(name = "series") val series: List<BlsSeries>? = null
)

@JsonClass(generateAdapter = true)
data class BlsSeries(
    @Json(name = "seriesID") val seriesID: String,
    @Json(name = "data") val data: List<BlsDataPoint>? = null
)

@JsonClass(generateAdapter = true)
data class BlsDataPoint(
    @Json(name = "year") val year: String,
    @Json(name = "period") val period: String,
    @Json(name = "periodName") val periodName: String,
    @Json(name = "value") val value: String,
    @Json(name = "latest") val latest: String? = null
)

interface BlsApiService {
    @POST("publicAPI/v2/timeseries/data/")
    suspend fun getTimeseriesData(
        @Body request: BlsRequest
    ): BlsResponse
}
