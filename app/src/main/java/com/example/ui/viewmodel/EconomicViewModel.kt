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

    // Dynamic Flow of observations for the selected Series
    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedSeriesDataPoints: StateFlow<List<DataPointEntity>> = _selectedSeriesId
        .flatMapLatest { id -> repository.getDataPointsForSeries(id) }
        .map { points ->
            // Sort chronologically
            points.sortedWith(compareBy<DataPointEntity> { it.year }.thenBy { it.period })
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

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
