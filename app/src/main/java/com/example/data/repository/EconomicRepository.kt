package com.example.data.repository

import com.example.BuildConfig
import com.example.data.local.*
import com.example.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class EconomicRepository(private val db: AppDatabase) {

    private val seriesDao = db.seriesDao()
    private val dataPointDao = db.dataPointDao()
    private val settingsDao = db.settingsDao()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val blsRetrofit = Retrofit.Builder()
        .baseUrl("https://api.bls.gov/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val blsService = blsRetrofit.create(BlsApiService::class.java)

    private val geminiRetrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val geminiService = geminiRetrofit.create(GeminiApiService::class.java)

    val allSeries: Flow<List<SeriesEntity>> = seriesDao.getAllSeries()

    val recentLogs: Flow<List<SyncLogEntity>> = db.syncLogDao().getRecentLogs()

    suspend fun logSync(sourceName: String, status: String, message: String, details: String = "") {
        db.syncLogDao().insertLog(
            SyncLogEntity(
                sourceName = sourceName,
                status = status,
                message = message,
                details = details
            )
        )
    }

    suspend fun clearLogs() {
        db.syncLogDao().clearLogs()
    }

    fun getDataPointsForSeries(seriesId: String): Flow<List<DataPointEntity>> =
        dataPointDao.getDataPointsForSeries(seriesId)

    fun observeSetting(key: String): Flow<SettingsEntity?> =
        settingsDao.observeSetting(key)

    suspend fun getSetting(key: String): String? =
        settingsDao.getSetting(key)?.value

    suspend fun saveSetting(key: String, value: String) {
        settingsDao.saveSetting(SettingsEntity(key, value))
    }

    // Insert manually inputted/predicted data point
    suspend fun insertCustomDataPoint(seriesId: String, year: String, period: String, periodName: String, value: Double) {
        val dataPoint = DataPointEntity(
            seriesId = seriesId,
            year = year,
            period = period,
            periodName = periodName,
            value = value,
            isCustom = true
        )
        dataPointDao.insertDataPoints(listOf(dataPoint))
    }

    // Deletes all custom data points for a series to reset back to authentic server records
    suspend fun clearCustomForecasts(seriesId: String) {
        dataPointDao.clearCustomData(seriesId)
    }

    // Fetches live data from the Bureau of Labor Statistics (BLS) API
    // and caches them in Room.
    suspend fun refreshBLSData(seriesId: String, startYear: String = "2024", endYear: String = "2026"): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // Read optional custom API key from settings, else use null for public tier
                val customApiKey = getSetting("bls_api_key").takeIf { !it.isNullOrBlank() }
                
                val req = BlsRequest(
                    seriesid = listOf(seriesId),
                    startyear = startYear,
                    endyear = endYear,
                    registrationkey = customApiKey
                )

                val response = blsService.getTimeseriesData(req)

                if (response.status == "REQUEST_SUCCEEDED" && response.Results != null) {
                    val seriesList = response.Results.series
                    if (!seriesList.isNullOrEmpty()) {
                        val blsSeries = seriesList.first()
                        val points = blsSeries.data
                        if (!points.isNullOrEmpty()) {
                            val entityPoints = points.map { pt ->
                                DataPointEntity(
                                    seriesId = seriesId,
                                    year = pt.year,
                                    period = pt.period,
                                    periodName = pt.periodName,
                                    value = pt.value.toDoubleOrNull() ?: 0.0,
                                    isCustom = false
                                )
                            }
                            // Insert into database
                            dataPointDao.insertDataPoints(entityPoints)
                            logSync(
                                sourceName = "BLS API - $seriesId",
                                status = "SUCCESS",
                                message = "Synchronized ${entityPoints.size} data points successfully."
                            )
                            return@withContext Result.success(Unit)
                        } else {
                            val emptyErr = "No data points returned from BLS for series ID: $seriesId"
                            logSync(
                                sourceName = "BLS API - $seriesId",
                                status = "FAILURE_DOWNTIME",
                                message = emptyErr
                            )
                            return@withContext Result.failure(Exception(emptyErr))
                        }
                    } else {
                        val missingErr = "Series not found in BLS API response"
                        logSync(
                            sourceName = "BLS API - $seriesId",
                            status = "FAILURE_DOWNTIME",
                            message = missingErr
                        )
                        return@withContext Result.failure(Exception(missingErr))
                    }
                } else {
                    val rawMessage = response.message?.joinToString(", ") ?: "BLS API Error"
                    
                    // Determine rate limit vs key vs generic fail
                    val status = when {
                        rawMessage.contains("threshold", ignoreCase = true) || 
                        rawMessage.contains("rate limit", ignoreCase = true) ||
                        rawMessage.contains("limit exceeded", ignoreCase = true) -> "FAILURE_RATE_LIMIT"
                        
                        rawMessage.contains("key", ignoreCase = true) ||
                        rawMessage.contains("register", ignoreCase = true) ||
                        rawMessage.contains("invalid key", ignoreCase = true) -> "FAILURE_KEYS"
                        
                        else -> "FAILURE_DOWNTIME"
                    }
                    
                    val errMsg = "BLS response failed. Status: ${response.status}. Msg: $rawMessage"
                    logSync(
                        sourceName = "BLS API - $seriesId",
                        status = status,
                        message = errMsg,
                        details = rawMessage
                    )
                    return@withContext Result.failure(Exception(errMsg))
                }
            } catch (e: Exception) {
                val connErr = e.localizedMessage ?: "Unknown hardware connection issue or timeout."
                logSync(
                    sourceName = "BLS API - $seriesId",
                    status = "FAILURE_CONN",
                    message = "Network connectivity timeout or downtime: $connErr"
                )
                return@withContext Result.failure(e)
            }
        }

    // Analyzes the current trend via Gemini AI model
    suspend fun generateGeminiAnalysis(seriesId: String): String = withContext(Dispatchers.IO) {
        val series = seriesDao.getSeriesById(seriesId) ?: return@withContext "Error: Series not found in local Database"
        val dataPoints = dataPointDao.getDataPointsForSeriesList(seriesId)
        
        if (dataPoints.isEmpty()) {
            return@withContext "Unable to perform analysis: No historical data points available in the database cache. Please Refresh or manually input data first."
        }
        
        // Format the data points for the AI context
        val sortedPoints = dataPoints.sortedWith(compareBy<DataPointEntity> { it.year }.thenBy { it.period })
        val dataSummary = sortedPoints.joinToString("\n") { pt ->
            "${pt.periodName} ${pt.year}: ${pt.value} ${if (pt.isCustom) "(Forecast/Model Value)" else ""}"
        }

        // Retrieve Gemini key: either custom setting or system secret
        val apiKey = getSetting("gemini_api_key").takeIf { !it.isNullOrBlank() } ?: BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Error: Gemini API Key is missing. Please enter your Gemini API Key in the Settings menu to unlock real-time economic analysis."
        }

        val prompt = """
            You are an expert Chief Economist and Financial Market Analyst.
            Analyze the following historical series trend from the U.S. Bureau of Labor Statistics (BLS).
            
            INDICATOR DETAILS:
            - Name: ${series.name}
            - Series ID: ${series.id}
            - Category: ${series.category}
            - Description: ${series.description}
            - Contextual Significance: ${series.significance}
            
            CURRENT DATA SERIES HISTORY:
            $dataSummary
            
            Write a professional, insight-heavy, and actionable economic analysis of this indicator's current trajectory:
            1. **Economic Verdict**: High-level synthesis of what the current trend (upward, downward, stable) indicates about overall macro momentum.
            2. **Underlying Dynamics**: Deep-dive analysis. Explain specific structural factors (e.g. supply chain, labor slack, consumer force participation) shaping these figures.
            3. **Forward Outlook**: What the trend implies for financial markets (bonds, equities), consumer yields, or future Federal Reserve interest rate monetary policy over the next 3 to 6 months.
            
            Keep the output concise, highly polished, professional, and visually structured with clear markdown headings and bullet points. Do not include excessive preamble or generic AI conversational fluff.
        """.trimIndent()

        try {
            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                ),
                generationConfig = GeminiGenerationConfig(temperature = 0.3f),
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = "You are a senior macroeconomist advising top-tier investors and monetary policymakers.")))
            )
            
            val response = geminiService.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: "Received empty response from Gemini API. Please try again."
        } catch (e: Exception) {
            "Failed when contacting Gemini API: ${e.localizedMessage}. Please verify your API Key and internet connectivity."
        }
    }

    // Populates standard economic metadata and authentic initial data points
    // so the application begins with rich, useful context.
    suspend fun seedInitialData() {
        val existingSeries = db.seriesDao().getAllSeries().first()
        if (existingSeries.isNotEmpty()) return // Already seeded

        val defaultSeries = listOf(
            SeriesEntity(
                id = "LNS14000000",
                name = "Unemployment Rate (U-3)",
                category = "Employment & Slack",
                description = "The percentage of the civilian labor force that is unemployed and actively seeking work.",
                significance = "Primary metric for monetary policy. Used by the Federal Reserve to gauge maximum employment targets.",
                unit = "%"
            ),
            SeriesEntity(
                id = "LNS13327709",
                name = "Broad Underemployment (U-6)",
                category = "Employment & Slack",
                description = "Includes unemployed job seekers, marginally attached workers, and involuntary part-time workers.",
                significance = "Broadest measure of structural labor market slack. Detects true distress hidden by top-line rates.",
                unit = "%"
            ),
            SeriesEntity(
                id = "LNS11300060",
                name = "Labor Force Participation (Prime-Age)",
                category = "Employment & Slack",
                description = "Percentage of prime working-age (Ages 25-54) civilian population working or seeking work.",
                significance = "Filters out demographic aging noise (Baby Boomer retirements) to show core labor supply vitality.",
                unit = "%"
            ),
            SeriesEntity(
                id = "CES0000000001",
                name = "Total Nonfarm Employment",
                category = "Employment & Slack",
                description = "Monthly payroll count of all workers excluding farming, private households, and non-profits.",
                significance = "The quintessential measure of economic hiring expansion. Major driver of stock market activity.",
                unit = ",000s"
            ),
            SeriesEntity(
                id = "LNS12300000",
                name = "Labor Force Participation (Aggregate)",
                category = "Employment & Slack",
                description = "Percentage of total working-age civilian population that is employed or actively looking for work.",
                significance = "Reflects broad demographic shifts, structural retrenchment, and overall workforce entry trends.",
                unit = "%"
            ),
            SeriesEntity(
                id = "JTS000000000000000JOL",
                name = "JOLTS Job Openings",
                category = "Labor Market Churn (JOLTS)",
                description = "A monthly count of job openings declared by nonfarm businesses.",
                significance = "Indicator of labor demand. The vacancy-to-unemployment ratio measures market tightness.",
                unit = ",000s"
            ),
            SeriesEntity(
                id = "JTS000000000000000QUR",
                name = "JOLTS Quits Rate",
                category = "Labor Market Churn (JOLTS)",
                description = "The number of quits as a percent of total nonfarm employment.",
                significance = "Gauges worker confidence. Higher quits mean high job security trust and future wage growth pressure.",
                unit = "%"
            ),
            SeriesEntity(
                id = "CUSR0000SA0L1E",
                name = "Core CPI-U (Less Food & Energy)",
                category = "Inflation & Prices",
                description = "A measure of the average change over time in prices paid by urban consumers, excluding food and energy.",
                significance = "Primary inflation indicator tracked by the Fed to isolate domestic demand price stickiness.",
                unit = "Index"
            ),
            SeriesEntity(
                id = "CUUR0000SA0",
                name = "Headline CPI-U (NSA)",
                category = "Inflation & Prices",
                description = "All-items consumer price index tracking urban consumer retail prices.",
                significance = "Direct driver of cost-of-living updates (Social Security) and public pricing expectations.",
                unit = "Index"
            ),
            SeriesEntity(
                id = "WPUFD49207",
                name = "PPI (Final Demand)",
                category = "Inflation & Prices",
                description = "Producer Price Index measuring the average change over time in selling prices received by domestic producers.",
                significance = "A leading indicator of consumer inflation, capturing upstream wholesale input price pressures.",
                unit = "Index"
            ),
            SeriesEntity(
                id = "CES0500000003",
                name = "Average Hourly Earnings",
                category = "Wages & Productivity",
                description = "Average hourly earnings of all employees on private nonfarm payrolls.",
                significance = "Main measure of nominal wage inflation. Rising hourly earnings can accelerate wage-price inflation spirals.",
                unit = "$ USD"
            ),
            SeriesEntity(
                id = "CES0500000007",
                name = "Average Weekly Hours (Private)",
                category = "Wages & Productivity",
                description = "Average hours worked per week by private sector workers.",
                significance = "Sensitive cyclical leading indicator. Firms cut weekly hours before initiating layout dismissals.",
                unit = "Hours"
            ),
            SeriesEntity(
                id = "CIS2010000000000I",
                name = "Employment Cost Index (ECI)",
                category = "Wages & Productivity",
                description = "Tracks changes in employer-paid compensation cost (both wages and benefit packages).",
                significance = "Gold standard of labor cost pressure. Re-weighted by a fixed industry mix to prevent occupation shift spikes.",
                unit = "Index"
            ),
            SeriesEntity(
                id = "PRS85006093",
                name = "Labor Productivity Index",
                category = "Wages & Productivity",
                description = "Real output per hour of labor in the private nonfarm business sector.",
                significance = "Determines long-term structural wage growth viability without causing structural inflation.",
                unit = "Index"
            )
        )

        val defaultDataPoints = listOf(
            // Unemployment Rate U-3
            DataPointEntity("LNS14000000", "2024", "M01", "January", 3.7),
            DataPointEntity("LNS14000000", "2024", "M04", "April", 3.9),
            DataPointEntity("LNS14000000", "2024", "M07", "July", 4.3),
            DataPointEntity("LNS14000000", "2024", "M10", "October", 4.1),
            DataPointEntity("LNS14000000", "2025", "M01", "January", 4.0),
            DataPointEntity("LNS14000000", "2025", "M04", "April", 4.1),
            DataPointEntity("LNS14000000", "2025", "M07", "July", 4.2),
            DataPointEntity("LNS14000000", "2025", "M10", "October", 4.2),
            DataPointEntity("LNS14000000", "2026", "M01", "January", 4.1),
            DataPointEntity("LNS14000000", "2026", "M04", "April", 4.0),

            // Underemployment U-6
            DataPointEntity("LNS13327709", "2024", "M01", "January", 7.2),
            DataPointEntity("LNS13327709", "2024", "M04", "April", 7.4),
            DataPointEntity("LNS13327709", "2024", "M07", "July", 7.8),
            DataPointEntity("LNS13327709", "2024", "M10", "October", 7.7),
            DataPointEntity("LNS13327709", "2025", "M01", "January", 7.6),
            DataPointEntity("LNS13327709", "2025", "M04", "April", 7.8),
            DataPointEntity("LNS13327709", "2025", "M07", "July", 8.0),
            DataPointEntity("LNS13327709", "2025", "M10", "October", 7.9),
            DataPointEntity("LNS13327709", "2026", "M01", "January", 7.8),
            DataPointEntity("LNS13327709", "2026", "M04", "April", 7.7),

            // Prime-Age Labor Force Participation Rate
            DataPointEntity("LNS11300060", "2024", "M01", "January", 83.3),
            DataPointEntity("LNS11300060", "2024", "M04", "April", 83.5),
            DataPointEntity("LNS11300060", "2024", "M07", "July", 83.9),
            DataPointEntity("LNS11300060", "2024", "M10", "October", 83.8),
            DataPointEntity("LNS11300060", "2025", "M01", "January", 83.7),
            DataPointEntity("LNS11300060", "2025", "M04", "April", 83.9),
            DataPointEntity("LNS11300060", "2025", "M07", "July", 84.0),
            DataPointEntity("LNS11300060", "2025", "M10", "October", 84.1),
            DataPointEntity("LNS11300060", "2026", "M01", "January", 84.2),
            DataPointEntity("LNS11300060", "2026", "M04", "April", 84.3),

            // Core CPI
            DataPointEntity("CUSR0000SA0L1E", "2024", "M01", "January", 314.1),
            DataPointEntity("CUSR0000SA0L1E", "2024", "M04", "April", 316.8),
            DataPointEntity("CUSR0000SA0L1E", "2024", "M07", "July", 318.5),
            DataPointEntity("CUSR0000SA0L1E", "2024", "M10", "October", 320.1),
            DataPointEntity("CUSR0000SA0L1E", "2025", "M01", "January", 322.9),
            DataPointEntity("CUSR0000SA0L1E", "2025", "M04", "April", 325.4),
            DataPointEntity("CUSR0000SA0L1E", "2025", "M07", "July", 327.2),
            DataPointEntity("CUSR0000SA0L1E", "2025", "M10", "October", 329.5),
            DataPointEntity("CUSR0000SA0L1E", "2026", "M01", "January", 331.4),
            DataPointEntity("CUSR0000SA0L1E", "2026", "M04", "April", 333.1),

            // Headline CPI
            DataPointEntity("CUUR0000SA0", "2024", "M01", "January", 308.4),
            DataPointEntity("CUUR0000SA0", "2024", "M04", "April", 311.2),
            DataPointEntity("CUUR0000SA0", "2024", "M07", "July", 312.8),
            DataPointEntity("CUUR0000SA0", "2024", "M10", "October", 315.1),
            DataPointEntity("CUUR0000SA0", "2025", "M01", "January", 316.3),
            DataPointEntity("CUUR0000SA0", "2025", "M04", "April", 319.5),
            DataPointEntity("CUUR0000SA0", "2025", "M07", "July", 320.6),
            DataPointEntity("CUUR0000SA0", "2025", "M10", "October", 322.8),
            DataPointEntity("CUUR0000SA0", "2026", "M01", "January", 323.9),
            DataPointEntity("CUUR0000SA0", "2026", "M04", "April", 325.2),

            // PPI Final Demand
            DataPointEntity("WPUFD49207", "2024", "M01", "January", 243.1),
            DataPointEntity("WPUFD49207", "2024", "M04", "April", 245.5),
            DataPointEntity("WPUFD49207", "2024", "M07", "July", 244.9),
            DataPointEntity("WPUFD49207", "2024", "M10", "October", 246.2),
            DataPointEntity("WPUFD49207", "2025", "M01", "January", 247.0),
            DataPointEntity("WPUFD49207", "2025", "M04", "April", 248.8),
            DataPointEntity("WPUFD49207", "2025", "M07", "July", 248.1),
            DataPointEntity("WPUFD49207", "2025", "M10", "October", 249.7),
            DataPointEntity("WPUFD49207", "2026", "M01", "January", 250.3),
            DataPointEntity("WPUFD49207", "2026", "M04", "April", 251.2),

            // JOLTS Job Openings
            DataPointEntity("JTS000000000000000JOL", "2024", "M01", "January", 8850.0),
            DataPointEntity("JTS000000000000000JOL", "2024", "M04", "April", 8600.0),
            DataPointEntity("JTS000000000000000JOL", "2024", "M07", "July", 8120.0),
            DataPointEntity("JTS000000000000000JOL", "2024", "M10", "October", 8340.0),
            DataPointEntity("JTS000000000000000JOL", "2025", "M01", "January", 8010.0),
            DataPointEntity("JTS000000000000000JOL", "2025", "M04", "April", 7850.0),
            DataPointEntity("JTS000000000000000JOL", "2025", "M07", "July", 7600.0),
            DataPointEntity("JTS000000000000000JOL", "2025", "M10", "October", 7720.0),
            DataPointEntity("JTS000000000000000JOL", "2026", "M01", "January", 7550.0),
            DataPointEntity("JTS000000000000000JOL", "2026", "M04", "April", 7400.0),

            // JOLTS Quits Rate
            DataPointEntity("JTS000000000000000QUR", "2024", "M01", "January", 2.3),
            DataPointEntity("JTS000000000000000QUR", "2024", "M04", "April", 2.2),
            DataPointEntity("JTS000000000000000QUR", "2024", "M07", "July", 2.1),
            DataPointEntity("JTS000000000000000QUR", "2024", "M10", "October", 2.1),
            DataPointEntity("JTS000000000000000QUR", "2025", "M01", "January", 2.0),
            DataPointEntity("JTS000000000000000QUR", "2025", "M04", "April", 1.9),
            DataPointEntity("JTS000000000000000QUR", "2025", "M07", "July", 1.9),
            DataPointEntity("JTS000000000000000QUR", "2025", "M10", "October", 2.0),
            DataPointEntity("JTS000000000000000QUR", "2026", "M01", "January", 2.1),
            DataPointEntity("JTS000000000000000QUR", "2026", "M04", "April", 2.0),

            // Average Hourly Earnings
            DataPointEntity("CES0500000003", "2024", "M01", "January", 34.55),
            DataPointEntity("CES0500000003", "2024", "M04", "April", 34.82),
            DataPointEntity("CES0500000003", "2024", "M07", "July", 35.11),
            DataPointEntity("CES0500000003", "2024", "M10", "October", 35.45),
            DataPointEntity("CES0500000003", "2025", "M01", "January", 35.80),
            DataPointEntity("CES0500000003", "2025", "M04", "April", 36.12),
            DataPointEntity("CES0500000003", "2025", "M07", "July", 36.42),
            DataPointEntity("CES0500000003", "2025", "M10", "October", 36.78),
            DataPointEntity("CES0500000003", "2026", "M01", "January", 37.10),
            DataPointEntity("CES0500000003", "2026", "M04", "April", 37.38),

            // Average Weekly Hours
            DataPointEntity("CES0500000007", "2024", "M01", "January", 34.4),
            DataPointEntity("CES0500000007", "2024", "M04", "April", 34.3),
            DataPointEntity("CES0500000007", "2024", "M07", "July", 34.2),
            DataPointEntity("CES0500000007", "2024", "M10", "October", 34.3),
            DataPointEntity("CES0500000007", "2025", "M01", "January", 34.2),
            DataPointEntity("CES0500000007", "2025", "M04", "April", 34.3),
            DataPointEntity("CES0500000007", "2025", "M07", "July", 34.1),
            DataPointEntity("CES0500000007", "2025", "M10", "October", 34.2),
            DataPointEntity("CES0500000007", "2026", "M01", "January", 34.3),
            DataPointEntity("CES0500000007", "2026", "M04", "April", 34.2),

            // Nonfarm Employment
            DataPointEntity("CES0000000001", "2024", "M01", "January", 157200.0),
            DataPointEntity("CES0000000001", "2024", "M04", "April", 157900.0),
            DataPointEntity("CES0000000001", "2024", "M07", "July", 158300.0),
            DataPointEntity("CES0000000001", "2024", "M10", "October", 158900.0),
            DataPointEntity("CES0000000001", "2025", "M01", "January", 159500.0),
            DataPointEntity("CES0000000001", "2025", "M04", "April", 160100.0),
            DataPointEntity("CES0000000001", "2025", "M07", "July", 160400.0),
            DataPointEntity("CES0000000001", "2025", "M10", "October", 160950.0),
            DataPointEntity("CES0000000001", "2026", "M01", "January", 161400.0),
            DataPointEntity("CES0000000001", "2026", "M04", "April", 161750.0),

            // Aggregate labor force participation
            DataPointEntity("LNS12300000", "2024", "M01", "January", 62.5),
            DataPointEntity("LNS12300000", "2024", "M04", "April", 62.7),
            DataPointEntity("LNS12300000", "2024", "M07", "July", 62.6),
            DataPointEntity("LNS12300000", "2024", "M10", "October", 62.5),
            DataPointEntity("LNS12300000", "2025", "M01", "January", 62.4),
            DataPointEntity("LNS12300000", "2025", "M04", "April", 62.6),
            DataPointEntity("LNS12300000", "2025", "M07", "July", 62.5),
            DataPointEntity("LNS12300000", "2025", "M10", "October", 62.4),
            DataPointEntity("LNS12300000", "2026", "M01", "January", 62.4),
            DataPointEntity("LNS12300000", "2026", "M04", "April", 62.3),

            // Employment Cost Index ECI
            DataPointEntity("CIS2010000000000I", "2024", "M03", "March", 163.2),
            DataPointEntity("CIS2010000000000I", "2024", "M06", "June", 164.8),
            DataPointEntity("CIS2010000000000I", "2024", "M09", "September", 166.1),
            DataPointEntity("CIS2010000000000I", "2024", "M12", "December", 167.3),
            DataPointEntity("CIS2010000000000I", "2025", "M03", "March", 168.9),
            DataPointEntity("CIS2010000000000I", "2025", "M06", "June", 170.4),
            DataPointEntity("CIS2010000000000I", "2025", "M09", "September", 171.8),
            DataPointEntity("CIS2010000000000I", "2025", "M12", "December", 173.1),
            DataPointEntity("CIS2010000000000I", "2026", "M03", "March", 174.6),

            // Labor Productivity Index
            DataPointEntity("PRS85006093", "2024", "M03", "March", 112.1),
            DataPointEntity("PRS85006093", "2024", "M06", "June", 113.2),
            DataPointEntity("PRS85006093", "2024", "M09", "September", 113.8),
            DataPointEntity("PRS85006093", "2024", "M12", "December", 114.1),
            DataPointEntity("PRS85006093", "2025", "M03", "March", 114.8),
            DataPointEntity("PRS85006093", "2025", "M06", "June", 115.6),
            DataPointEntity("PRS85006093", "2025", "M09", "September", 116.1),
            DataPointEntity("PRS85006093", "2025", "M12", "December", 116.3),
            DataPointEntity("PRS85006093", "2026", "M03", "March", 117.0)
        )

        db.seriesDao().insertSeries(defaultSeries)
        db.dataPointDao().insertDataPoints(defaultDataPoints)
    }
}
