package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.DataPointEntity
import com.example.data.local.SeriesEntity
import com.example.data.repository.EconomicRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface AnalysisUiState {
    object Idle : AnalysisUiState
    object Loading : AnalysisUiState
    data class Success(val markdownText: String) : AnalysisUiState
    data class Error(val message: String) : AnalysisUiState
}

sealed interface ClimateSummaryUiState {
    object Idle : ClimateSummaryUiState
    object Loading : ClimateSummaryUiState
    data class Success(val markdownText: String) : ClimateSummaryUiState
    data class Error(val message: String) : ClimateSummaryUiState
}

sealed interface RefreshUiState {
    object Idle : RefreshUiState
    object Loading : RefreshUiState
    object Success : RefreshUiState
    data class Error(val message: String) : RefreshUiState
}

class EconomicViewModel(private val repository: EconomicRepository) : ViewModel() {

    // Selected series filter category
    private val _selectedCategory = MutableStateFlow<String>("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    // Currently selected Economic Indicator Series ID
    private val _selectedSeriesId = MutableStateFlow<String>("LNS14000000") // Default to U-3 Unemployment rate
    val selectedSeriesId: StateFlow<String> = _selectedSeriesId.asStateFlow()

    // Live Flow of all series definitions
    val allSeries: StateFlow<List<SeriesEntity>> = repository.allSeries
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Filtered list of economic series based on selected Category
    val filteredSeries: StateFlow<List<SeriesEntity>> = combine(allSeries, _selectedCategory) { seriesList, category ->
        if (category == "All") seriesList else seriesList.filter { it.category == category }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Details of the currently selected Series
    val selectedSeriesDetails: StateFlow<SeriesEntity?> = combine(allSeries, _selectedSeriesId) { list, id ->
        list.find { it.id == id }
    }.stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = null)

    // Date Filters
    private val _startYear = MutableStateFlow(2024)
    val startYear: StateFlow<Int> = _startYear.asStateFlow()

    private val _startMonth = MutableStateFlow(1)
    val startMonth: StateFlow<Int> = _startMonth.asStateFlow()

    private val _endYear = MutableStateFlow(2026)
    val endYear: StateFlow<Int> = _endYear.asStateFlow()

    private val _endMonth = MutableStateFlow(12)
    val endMonth: StateFlow<Int> = _endMonth.asStateFlow()

    // Dynamic Flow of observations for the selected Series, filtered dynamically by date range
    @OptIn(ExperimentalCoroutinesApi::class)
    private val rawSeriesDataPoints: Flow<List<DataPointEntity>> = _selectedSeriesId
        .flatMapLatest { id -> repository.getDataPointsForSeries(id) }

    val selectedSeriesDataPoints: StateFlow<List<DataPointEntity>> = combine(
        rawSeriesDataPoints,
        _startYear,
        _startMonth,
        _endYear,
        _endMonth
    ) { points, sY, sM, eY, eM ->
        points.filter { pt ->
            val monthNum = when {
                pt.period.startsWith("M") -> pt.period.substring(1).toIntOrNull() ?: 1
                pt.period.startsWith("Q") -> (pt.period.substring(1).toIntOrNull() ?: 1) * 3
                else -> 1
            }
            val pointValue = pt.year.toIntOrNull()?.let { y -> y * 12 + monthNum } ?: 0
            val startValue = sY * 12 + sM
            val endValue = eY * 12 + eM
            pointValue in startValue..endValue
        }.sortedWith(compareBy<DataPointEntity> { it.year }.thenBy { it.period })
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Economic Climate Summary State
    private val _climateSummaryState = MutableStateFlow<ClimateSummaryUiState>(ClimateSummaryUiState.Idle)
    val climateSummaryState: StateFlow<ClimateSummaryUiState> = _climateSummaryState.asStateFlow()

    // AI Analysis State
    private val _analysisState = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Idle)
    val analysisState: StateFlow<AnalysisUiState> = _analysisState.asStateFlow()

    // Live Refresh / Sync State
    private val _refreshState = MutableStateFlow<RefreshUiState>(RefreshUiState.Idle)
    val refreshState: StateFlow<RefreshUiState> = _refreshState.asStateFlow()

    // Live Sync Logs Flow for background transparency
    val recentLogs: StateFlow<List<com.example.data.local.SyncLogEntity>> = repository.recentLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val systemStatus: StateFlow<String> = kotlinx.coroutines.flow.combine(
        recentLogs,
        refreshState
    ) { logs, refState ->
        when {
            refState is RefreshUiState.Loading -> "syncing"
            logs.any { it.status.startsWith("FAILURE") } -> "experiencing issues"
            else -> "up-to-date"
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "up-to-date"
    )

    fun queryBackendStatus() {
        viewModelScope.launch {
            _refreshState.value = RefreshUiState.Loading
            repository.testPostgresConnection()
            _refreshState.value = RefreshUiState.Idle
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    // Live API Keys (from Settings)
    val blsApiKey: StateFlow<String> = repository.observeSetting("bls_api_key")
        .map { it?.value ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val geminiApiKey: StateFlow<String> = repository.observeSetting("gemini_api_key")
        .map { it?.value ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // Postgres Server Settings State
    val pgHost: StateFlow<String> = repository.observeSetting("pg_host")
        .map { it?.value ?: "127.0.0.1" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "127.0.0.1")

    val pgPort: StateFlow<String> = repository.observeSetting("pg_port")
        .map { it?.value ?: "5432" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "5432")

    val pgDatabase: StateFlow<String> = repository.observeSetting("pg_database")
        .map { it?.value ?: "macro_pulse_db" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "macro_pulse_db")

    val pgUsername: StateFlow<String> = repository.observeSetting("pg_username")
        .map { it?.value ?: "postgres" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "postgres")

    val pgGatewayUrl: StateFlow<String> = repository.observeSetting("pg_gateway_url")
        .map { it?.value ?: "http://10.0.2.2:8000/api/" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "http://10.0.2.2:8000/api/")

    // PostgreSQL Status & Testing
    private val _pgTestState = MutableStateFlow<String>("IDLE") // IDLE, TESTING, SUCCESS: total_records, ERROR
    val pgTestState: StateFlow<String> = _pgTestState.asStateFlow()

    fun testPostgresConnection() {
        viewModelScope.launch {
            _pgTestState.value = "TESTING"
            val res = repository.testPostgresConnection()
            res.fold(
                onSuccess = { info ->
                    _pgTestState.value = "SUCCESS: Host=${info.host}, Port=${info.port}, Records=${info.totalRecords}"
                },
                onFailure = { err ->
                    _pgTestState.value = "ERROR: ${err.localizedMessage ?: "Failed connection"}"
                }
            )
        }
    }

    fun clearPgTestState() {
        _pgTestState.value = "IDLE"
    }

    // Direct synchronization from Postgres Database Server
    fun syncSelectedSeriesFromPostgres() {
        val seriesId = _selectedSeriesId.value
        viewModelScope.launch {
            _refreshState.value = RefreshUiState.Loading
            val result = repository.syncAllFromPostgres(seriesId)
            result.fold(
                onSuccess = { count ->
                    _refreshState.value = RefreshUiState.Success
                },
                onFailure = { err ->
                    _refreshState.value = RefreshUiState.Error(err.localizedMessage ?: "Postgres query failed.")
                }
            )
        }
    }

    init {
        // Seed database immediately on first run
        viewModelScope.launch {
            repository.seedInitialData()
        }
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun selectSeries(seriesId: String) {
        _selectedSeriesId.value = seriesId
        _analysisState.value = AnalysisUiState.Idle // Reset commentary on shift
    }

    fun getDataPointsForSeries(seriesId: String): Flow<List<DataPointEntity>> {
        return repository.getDataPointsForSeries(seriesId)
    }

    // Force pull updated figures from live BLS Servers
    fun refreshSelectedSeries() {
        val seriesId = _selectedSeriesId.value
        viewModelScope.launch {
            _refreshState.value = RefreshUiState.Loading
            val result = repository.refreshBLSData(seriesId)
            result.fold(
                onSuccess = {
                    _refreshState.value = RefreshUiState.Success
                },
                onFailure = { err ->
                    _refreshState.value = RefreshUiState.Error(err.localizedMessage ?: "Network request failed.")
                }
            )
        }
    }

    fun clearRefreshState() {
        _refreshState.value = RefreshUiState.Idle
    }

    // Requests Chief Economist analysis from Gemini on the current series trend
    fun analyzeCurrentSeries() {
        val seriesId = _selectedSeriesId.value
        viewModelScope.launch {
            _analysisState.value = AnalysisUiState.Loading
            try {
                val analysis = repository.generateGeminiAnalysis(seriesId)
                if (analysis.startsWith("Error:") || analysis.startsWith("Failed")) {
                    _analysisState.value = AnalysisUiState.Error(analysis)
                } else {
                    _analysisState.value = AnalysisUiState.Success(analysis)
                }
            } catch (e: Exception) {
                _analysisState.value = AnalysisUiState.Error("Unexpected failure: ${e.localizedMessage}")
            }
        }
    }

    // Add forecast point manually
    fun addForecastDataPoint(year: String, monthNum: String, valueStr: String) {
        val valDouble = valueStr.toDoubleOrNull() ?: return
        val yearStr = year.trim()
        val num = monthNum.toIntOrNull() ?: 1
        val padNum = if (num < 10) "0$num" else "$num"
        val period = "M$padNum"
        val periodName = when(num) {
            1 -> "January"
            2 -> "February"
            3 -> "March"
            4 -> "April"
            5 -> "May"
            6 -> "June"
            7 -> "July"
            8 -> "August"
            9 -> "September"
            10 -> "October"
            11 -> "November"
            12 -> "December"
            else -> "Month"
        }
        
        viewModelScope.launch {
            repository.insertCustomDataPoint(
                seriesId = _selectedSeriesId.value,
                year = yearStr,
                period = period,
                periodName = periodName,
                value = valDouble
            )
        }
    }

    // Wipe added predicted/modelling points for the selected series
    fun clearCustomDataPoints() {
        val seriesId = _selectedSeriesId.value
        viewModelScope.launch {
            repository.clearCustomForecasts(seriesId)
        }
    }

    // Save customized keys in settings
    fun saveApiKeys(blsKey: String, geminiKey: String) {
        viewModelScope.launch {
            repository.saveSetting("bls_api_key", blsKey.trim())
            repository.saveSetting("gemini_api_key", geminiKey.trim())
        }
    }

    // Save PostgreSQL Database server connection configurations
    fun savePostgresConfig(host: String, port: String, dbName: String, dbUser: String, gatewayUrl: String) {
        viewModelScope.launch {
            repository.saveSetting("pg_host", host.trim())
            repository.saveSetting("pg_port", port.trim())
            repository.saveSetting("pg_database", dbName.trim())
            repository.saveSetting("pg_username", dbUser.trim())
            repository.saveSetting("pg_gateway_url", gatewayUrl.trim())
        }
    }

    fun setDateRange(sYear: Int, sMonth: Int, eYear: Int, eMonth: Int) {
        _startYear.value = sYear
        _startMonth.value = sMonth
        _endYear.value = eYear
        _endMonth.value = eMonth
    }

    fun generateEconomicClimateSummary() {
        viewModelScope.launch {
            _climateSummaryState.value = ClimateSummaryUiState.Loading
            try {
                val summary = repository.generateEconomicClimateSummary()
                if (summary.startsWith("Error:") || summary.startsWith("Failed")) {
                    _climateSummaryState.value = ClimateSummaryUiState.Error(summary)
                } else {
                    _climateSummaryState.value = ClimateSummaryUiState.Success(summary)
                }
            } catch (e: Exception) {
                _climateSummaryState.value = ClimateSummaryUiState.Error("Unexpected failure: ${e.localizedMessage}")
            }
        }
    }

    fun clearClimateSummary() {
        _climateSummaryState.value = ClimateSummaryUiState.Idle
    }

    // Factory to easily construct constructor dependencies
    class Factory(private val repository: EconomicRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(EconomicViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return EconomicViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
