package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DataPointEntity
import com.example.data.local.SeriesEntity
import com.example.ui.viewmodel.AnalysisUiState
import com.example.ui.viewmodel.EconomicViewModel
import com.example.ui.viewmodel.RefreshUiState
import kotlin.math.roundToInt

enum class DashboardTab { Home, Sources, Settings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: EconomicViewModel,
    modifier: Modifier = Modifier
) {
    var currentTab by remember { mutableStateOf(DashboardTab.Home) }
    var detailActiveInMobile by remember { mutableStateOf(false) }

    val filteredList by viewModel.filteredSeries.collectAsStateWithLifecycle()
    val allSeriesList by viewModel.allSeries.collectAsStateWithLifecycle()
    val selectedSeriesDetails by viewModel.selectedSeriesDetails.collectAsStateWithLifecycle()
    val selectedSeriesData by viewModel.selectedSeriesDataPoints.collectAsStateWithLifecycle()
    val currentCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val currentSeriesId by viewModel.selectedSeriesId.collectAsStateWithLifecycle()

    val refreshState by viewModel.refreshState.collectAsStateWithLifecycle()
    val analysisState by viewModel.analysisState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(refreshState) {
        if (refreshState is RefreshUiState.Success) {
            snackbarHostState.showSnackbar("Live figures successfully synced from BLS!")
            viewModel.clearRefreshState()
        } else if (refreshState is RefreshUiState.Error) {
            snackbarHostState.showSnackbar("Sync failed: ${(refreshState as RefreshUiState.Error).message}")
            viewModel.clearRefreshState()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                "MacroPulse",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                "Economic Research & Forecasting",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { currentTab = if (currentTab == DashboardTab.Settings) DashboardTab.Home else DashboardTab.Settings },
                        modifier = Modifier.testTag("settings_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (currentTab == DashboardTab.Settings) Icons.Default.Close else Icons.Default.Settings,
                            contentDescription = "API Keys Control panel"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == DashboardTab.Home,
                    onClick = { currentTab = DashboardTab.Home },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = currentTab == DashboardTab.Sources,
                    onClick = { currentTab = DashboardTab.Sources },
                    icon = { Icon(Icons.Default.List, contentDescription = "Sources") },
                    label = { Text("Sources", fontSize = 11.sp) }
                )
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            val isWideScreen = maxWidth > 720.dp

            if (currentTab == DashboardTab.Settings) {
                // Settings Overlay Drawer
                SettingsPanelContent(
                    viewModel = viewModel,
                    onDismiss = { currentTab = DashboardTab.Home }
                )
            } else if (currentTab == DashboardTab.Sources) {
                DataSourcesView(viewModel = viewModel)
            } else {
                if (isWideScreen) {
                    // Canonical List-Detail Split Grid Layout for Tablets
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Left List side (weighted)
                        Column(
                            modifier = Modifier
                                .weight(4f)
                                .fillMaxHeight()
                                .padding(start = 12.dp, top = 8.dp, bottom = 12.dp)
                        ) {
                            GeometricSummaryHero()
                            Spacer(modifier = Modifier.height(12.dp))
                            CategoryTabsRow(
                                activeCategory = currentCategory,
                                selectAction = { viewModel.selectCategory(it) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            IndicatorsList(
                                seriesList = filteredList,
                                selectedId = currentSeriesId,
                                onItemClick = { id ->
                                    viewModel.selectSeries(id)
                                },
                                viewModel = viewModel
                            )
                        }

                        // Right Detail side (weighted)
                        Box(
                            modifier = Modifier
                                .weight(6f)
                                .fillMaxHeight()
                                .padding(end = 12.dp, top = 8.dp, bottom = 12.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(28.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(28.dp)
                                )
                        ) {
                            selectedSeriesDetails?.let { details ->
                                DetailPanel(
                                    details = details,
                                    dataPoints = selectedSeriesData,
                                    analysisState = analysisState,
                                    refreshState = refreshState,
                                    onRefresh = { viewModel.refreshSelectedSeries() },
                                    onAnalyze = { viewModel.analyzeCurrentSeries() },
                                    onAddForecast = { yr, mo, valStr -> viewModel.addForecastDataPoint(yr, mo, valStr) },
                                    onClearForecasts = { viewModel.clearCustomDataPoints() }
                                )
                            } ?: EmptyDetailPlaceholder()
                        }
                    }
                } else {
                    // Mobile Adaptive Screen Flow (Stack Switcher)
                    AnimatedContent(
                        targetState = detailActiveInMobile,
                        label = "Mobile screen change"
                    ) { isDetailExpanded ->
                        if (isDetailExpanded) {
                            // Focus on indicator details view
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                val details = selectedSeriesDetails
                                if (details != null) {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        // Mobile Back Navigation
                                        TextButton(
                                            onClick = { detailActiveInMobile = false },
                                            modifier = Modifier
                                                .padding(bottom = 6.dp)
                                                .testTag("mobile_back_button"),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowBack,
                                                contentDescription = "Back to dashboard index",
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Back to Index", fontWeight = FontWeight.SemiBold)
                                        }

                                        DetailPanel(
                                            details = details,
                                            dataPoints = selectedSeriesData,
                                            analysisState = analysisState,
                                            refreshState = refreshState,
                                            onRefresh = { viewModel.refreshSelectedSeries() },
                                            onAnalyze = { viewModel.analyzeCurrentSeries() },
                                            onAddForecast = { yr, mo, valStr -> viewModel.addForecastDataPoint(yr, mo, valStr) },
                                            onClearForecasts = { viewModel.clearCustomDataPoints() }
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("No series selected")
                                            Button(onClick = { detailActiveInMobile = false }) {
                                                Text("Go back")
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Focus on indices list screen
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                GeometricSummaryHero()
                                Spacer(modifier = Modifier.height(12.dp))
                                CategoryTabsRow(
                                    activeCategory = currentCategory,
                                    selectAction = { viewModel.selectCategory(it) }
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                IndicatorsList(
                                    seriesList = filteredList,
                                    selectedId = currentSeriesId,
                                    onItemClick = { id ->
                                        viewModel.selectSeries(id)
                                        detailActiveInMobile = true
                                    },
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun textPlaceholder(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No series selected")
            Button(onClick = onBack) { Text("Go back") }
        }
    }
}

// Sparkline Canvas representation to go on each Indicator item Card
@Composable
fun MicroSparkline(
    dataPoints: List<DataPointEntity>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(modifier = modifier) {
        if (dataPoints.size < 2) return@Canvas

        val values = dataPoints.map { it.value }
        val min = values.minOrNull() ?: 0.0
        val max = values.maxOrNull() ?: 1.0
        val range = (max - min).takeIf { it > 0.0 } ?: 1.0

        val width = size.width
        val height = size.height

        val path = Path()
        val stepX = width / (dataPoints.size - 1)

        dataPoints.forEachIndexed { idx, pt ->
            val cx = idx * stepX
            val py = height - (((pt.value - min) / range).toFloat() * (height - 4f) + 2f)
            if (idx == 0) {
                path.moveTo(cx, py)
            } else {
                path.lineTo(cx, py)
            }
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

// Large dynamic line graph with custom crosshair grid interaction and forecast separation
@Composable
fun MacroInteractiveChart(
    dataPoints: List<DataPointEntity>,
    unitLabel: String,
    modifier: Modifier = Modifier
) {
    if (dataPoints.isEmpty()) {
        Box(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text("No baseline trends logged for this indicator.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val values = dataPoints.map { it.value }
    val minVal = values.minOrNull() ?: 0.0
    val maxVal = values.maxOrNull() ?: 1.0
    val valRange = (maxVal - minVal).takeIf { it > 0 } ?: 1.0

    // Add 10% vertical buffer
    val displayMin = (minVal - (valRange * 0.1)).coerceAtLeast(0.0)
    val displayMax = maxVal + (valRange * 0.1)
    val displayRange = displayMax - displayMin

    var hoverIndex by remember(dataPoints) { mutableStateOf<Int?>(null) }
    var rawTouchX by remember { mutableStateOf(-1f) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val forecastColor = Color(0xFFF59E0B) // Amber Gold
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = Modifier.fillMaxWidth()) {
        // Tracker HUD overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .height(44.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (hoverIndex != null && hoverIndex!! in dataPoints.indices) {
                val pt = dataPoints[hoverIndex!!]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (pt.isCustom) forecastColor else primaryColor,
                                    shape = RoundedCornerShape(4.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "${pt.periodName} ${pt.year}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (pt.isCustom) {
                            Badge(
                                modifier = Modifier.padding(start = 6.dp),
                                containerColor = forecastColor.copy(alpha = 0.2f),
                                contentColor = forecastColor
                            ) {
                                Text("SIMULATED", fontSize = 9.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    Text(
                        "${pt.value} $unitLabel",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Text(
                    "💡 Drag along the chart to scan historical and simulated data.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // The Canvas curve
        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                    shape = RoundedCornerShape(24.dp)
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .pointerInput(dataPoints) {
                    detectTapGestures(
                        onTap = { offset ->
                            val ptCount = dataPoints.size
                            if (ptCount > 1) {
                                val chartWidth = size.width
                                val stepX = chartWidth / (ptCount - 1)
                                val idx = (offset.x / stepX).roundToInt().coerceIn(0, ptCount - 1)
                                hoverIndex = idx
                                rawTouchX = offset.x
                            }
                        }
                    )
                }
                .pointerInput(dataPoints) {
                    detectDragGestures(
                        onDragEnd = { hoverIndex = null },
                        onDragCancel = { hoverIndex = null },
                        onDrag = { change, dragAmount ->
                            val ptCount = dataPoints.size
                            if (ptCount > 1) {
                                val chartWidth = size.width
                                val stepX = chartWidth / (ptCount - 1)
                                val cx = change.position.x
                                val idx = (cx / stepX).roundToInt().coerceIn(0, ptCount - 1)
                                hoverIndex = idx
                                rawTouchX = cx
                            }
                        }
                    )
                }
        ) {
            val chartW = size.width
            val chartH = size.height

            if (dataPoints.size < 2) return@Canvas

            // 1. Draw grid backdrop lines
            val numGridlines = 4
            for (i in 0..numGridlines) {
                val gy = (chartH / numGridlines) * i
                drawLine(
                    color = gridColor,
                    start = Offset(0f, gy),
                    end = Offset(chartW, gy),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // 2. Build the primary paths
            val stepX = chartW / (dataPoints.size - 1)
            val path = Path()
            val fillPath = Path()

            // Optional Moving Average for multi-line context
            val maPath = Path()
            val maPeriod = 3
            val useMaPath = dataPoints.size >= maPeriod

            dataPoints.forEachIndexed { idx, pt ->
                val cx = idx * stepX
                val cy = chartH - (((pt.value - displayMin) / displayRange).toFloat() * chartH)
                
                if (idx == 0) {
                    path.moveTo(cx, cy)
                    fillPath.moveTo(cx, chartH)
                    fillPath.lineTo(cx, cy)
                } else {
                    val prevX = (idx - 1) * stepX
                    val prevCy = chartH - (((dataPoints[idx - 1].value - displayMin) / displayRange).toFloat() * chartH)
                    val controlX = (prevX + cx) / 2
                    path.cubicTo(controlX, prevCy, controlX, cy, cx, cy)
                    fillPath.cubicTo(controlX, prevCy, controlX, cy, cx, cy)
                }
                
                if (idx == dataPoints.lastIndex) {
                    fillPath.lineTo(cx, chartH)
                    fillPath.close()
                }

                // Compute Moving Average points
                if (useMaPath && idx >= maPeriod - 1) {
                    val sum = (0 until maPeriod).sumOf { dataPoints[idx - it].value }
                    val maVal = sum / maPeriod
                    val maY = chartH - (((maVal - displayMin) / displayRange).toFloat() * chartH)
                    
                    if (idx == maPeriod - 1) {
                        maPath.moveTo(cx, maY)
                    } else {
                        val prevMaX = (idx - 1) * stepX
                        val prevSum = (0 until maPeriod).sumOf { dataPoints[idx - 1 - it].value }
                        val prevMaVal = prevSum / maPeriod
                        val prevMaY = chartH - (((prevMaVal - displayMin) / displayRange).toFloat() * chartH)
                        val mControlX = (prevMaX + cx) / 2
                        maPath.cubicTo(mControlX, prevMaY, mControlX, maY, cx, maY)
                    }
                }
            }

            // 3. Draw gradient bounds area fill
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.25f),
                        primaryColor.copy(alpha = 0.0f)
                    )
                )
            )

            // 4. Draw main connector trendline
            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
            
            // 4b. Draw Multi-line moving average
            if (useMaPath) {
                drawPath(
                    path = maPath,
                    color = primaryColor.copy(alpha = 0.5f),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                    )
                )
            }

            // 5. Draw interactive node values
            dataPoints.forEachIndexed { idx, pt ->
                val cx = idx * stepX
                val cy = chartH - (((pt.value - displayMin) / displayRange).toFloat() * chartH)

                // Highlight simulated forecasted points specifically
                val pointColor = if (pt.isCustom) forecastColor else primaryColor
                val pointRad = if (idx == hoverIndex) 6.dp.toPx() else 4.dp.toPx()

                drawCircle(
                    color = pointColor,
                    radius = pointRad,
                    center = Offset(cx, cy)
                )
                if (idx == hoverIndex) {
                    drawCircle(
                        color = Color.White,
                        radius = 2.dp.toPx(),
                        center = Offset(cx, cy)
                    )
                }
            }

            // 6. Draw tracking vertical focal line
            hoverIndex?.let { hIdx ->
                if (hIdx in dataPoints.indices) {
                    val trackerX = hIdx * stepX
                    drawLine(
                        color = onSurfaceVariantColor.copy(alpha = 0.5f),
                        start = Offset(trackerX, 0f),
                        end = Offset(trackerX, chartH),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
            }
        }

        // X Axis labels (First and Last years)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Est. ${dataPoints.firstOrNull()?.periodName ?: "N/A"} ${dataPoints.firstOrNull()?.year ?: ""}",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Est. ${dataPoints.lastOrNull()?.periodName ?: "N/A"} ${dataPoints.lastOrNull()?.year ?: ""}",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun GeometricSummaryHero() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Market Sentiment",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Text(
                    text = "Updated 12m ago",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Stable",
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "UNDERLYING",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("↑", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("1.2%", color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Sidebar categories row
@Composable
fun CategoryTabsRow(
    activeCategory: String,
    selectAction: (String) -> Unit
) {
    val categories = listOf("All", "Employment & Slack", "Labor Market Churn (JOLTS)", "Inflation & Prices", "Wages & Productivity")
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { cat ->
            val isActive = activeCategory == cat
            FilterChip(
                selected = isActive,
                onClick = { selectAction(cat) },
                label = { Text(cat, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.testTag("category_tab_${cat.lowercase().replace(" & ", "_").replace(" ", "_")}")
            )
        }
    }
}

// Left index listing of the metrics
@Composable
fun IndicatorsList(
    seriesList: List<SeriesEntity>,
    selectedId: String,
    onItemClick: (String) -> Unit,
    viewModel: EconomicViewModel
) {
    if (seriesList.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No series mapped to this category.", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(seriesList, key = { it.id }) { series ->
            val isSelected = series.id == selectedId
            val ptsFlow = remember(series.id) { viewModel.getDataPointsForSeries(series.id) }
            val pts by ptsFlow.collectAsStateWithLifecycle(initialValue = emptyList<DataPointEntity>())

            val latestPt = pts.sortedWith(compareBy<DataPointEntity> { it.year }.thenBy { it.period }).lastOrNull()
            val priorPt = pts.sortedWith(compareBy<DataPointEntity> { it.year }.thenBy { it.period }).getOrNull(pts.size - 2)

            val displayValue = latestPt?.value?.toString() ?: "N/A"
            
            // Calculate direction and momentum for visual colors
            val pctDiff = if (latestPt != null && priorPt != null && priorPt.value > 0.0) {
                ((latestPt.value - priorPt.value) / priorPt.value) * 100
            } else 0.0

            val isIncreasing = pctDiff > 0.0
            
            // Custom economic positive/negative color determination based on inflation & employment models
            val isFavorable = remember(series.id, isIncreasing) {
                when {
                    series.id in listOf("LNS14000000", "LNS13327709") -> !isIncreasing // Unemployment going down is positive
                    series.id in listOf("CUSR0000SA0L1E", "CUUR0000SA0", "WPUFD49207") -> !isIncreasing // Inflation slowing down is positive
                    else -> isIncreasing // Wages, weekly hours, productivity, openings up is positive
                }
            }

            val badgeColor = if (latestPt == null || priorPt == null) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            } else if (isFavorable) {
                Color(0xFF10B981) // Emerald
            } else {
                Color(0xFFEF4444) // Scarlet
            }

            Surface(
                onClick = { onItemClick(series.id) },
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(
                    width = 1.2.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                tonalElevation = if (isSelected) 4.dp else 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .testTag("series_item_${series.id}")
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = series.id.take(1),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = series.id,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = series.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 2,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column {
                        // Micro preview Sparkline trace
                        if (pts.isNotEmpty()) {
                            MicroSparkline(
                                dataPoints = pts,
                                lineColor = badgeColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .padding(bottom = 6.dp)
                            )
                        }
                        
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "$displayValue${series.unit}",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (pctDiff != 0.0) {
                                Text(
                                    text = "${if (pctDiff > 0) "+" else ""}${String.format("%.2f", pctDiff)}%",
                                    fontSize = 10.sp,
                                    color = badgeColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Right panel complete dashboard details of the selected economic series
@Composable
fun DetailPanel(
    details: SeriesEntity,
    dataPoints: List<DataPointEntity>,
    analysisState: AnalysisUiState,
    refreshState: RefreshUiState,
    onRefresh: () -> Unit,
    onAnalyze: () -> Unit,
    onAddForecast: (year: String, month: String, valStr: String) -> Unit,
    onClearForecasts: () -> Unit
) {
    var customYear by remember { mutableStateOf("2026") }
    var customMonth by remember { mutableStateOf("5") }
    var customValue by remember { mutableStateOf("") }

    val hasForecasts = remember(dataPoints) { dataPoints.any { it.isCustom } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Details ID and title block
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = details.id,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                // Refresh live data tracker button
                FilledTonalButton(
                    onClick = onRefresh,
                    enabled = refreshState !is RefreshUiState.Loading,
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("refresh_button"),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    if (refreshState is RefreshUiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Refresh Live Data", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(
                text = details.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = details.description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Large high-precision chart
        Text(
            "Historical and Simulated Path",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        MacroInteractiveChart(
            dataPoints = dataPoints,
            unitLabel = details.unit,
            modifier = Modifier.height(180.dp)
        )

        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // 1. Economic model interactive simulator forecasting
        Surface(
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Scenario Predictor & Modeler",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (hasForecasts) {
                        TextButton(
                            onClick = onClearForecasts,
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text("Clear Models", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    "Simulate how the trend curve shifts by injecting custom future observations.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = customYear,
                        onValueChange = { customYear = it },
                        label = { Text("Year") },
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("forecast_year_input"),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                    TextField(
                        value = customMonth,
                        onValueChange = { customMonth = it },
                        label = { Text("Month (1-12)") },
                        modifier = Modifier
                            .weight(1.8f)
                            .testTag("forecast_month_input"),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                    TextField(
                        value = customValue,
                        onValueChange = { customValue = it },
                        label = { Text("Value (${details.unit})") },
                        placeholder = { Text("e.g. 4.2") },
                        modifier = Modifier
                            .weight(2.2f)
                            .testTag("forecast_value_input"),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                    Button(
                        onClick = {
                            if (customYear.isNotBlank() && customMonth.isNotBlank() && customValue.isNotBlank()) {
                                onAddForecast(customYear, customMonth, customValue)
                                customValue = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("submit_forecast_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Observation")
                    }
                }
            }
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // 2. AI Intelligence Commentary Analyser
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Gemini Economic Commentary",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    if (analysisState is AnalysisUiState.Success) {
                        IconButton(onClick = onAnalyze) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Regenerate analysis", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                AnimatedVisibility(visible = true) {
                    when (analysisState) {
                        is AnalysisUiState.Idle -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                Text(
                                    "Request a professional macroeconomic verdict on the current series slope direct from Gemini.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                Button(
                                    onClick = onAnalyze,
                                    modifier = Modifier.testTag("ask_gemini_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Analyze with Gemini")
                                }
                            }
                        }
                        is AnalysisUiState.Loading -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Consulting chief economic model...",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        is AnalysisUiState.Success -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Renders markdown analysis beautifully 
                                Text(
                                    text = analysisState.markdownText,
                                    fontSize = 12.5.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        is AnalysisUiState.Error -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = analysisState.message,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    color = Color(0xFFEF4444),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = onAnalyze) {
                                    Text("Retry Analysis")
                                }
                            }
                        }
                    }
                }
            }
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Fed Target Signaling Guidance
        Column {
            Text(
                "Contextual Federal Reserve Target Signaling",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = details.significance,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyDetailPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Choose an Economic Indicator",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Select any indicator from the index left to reveal charts, model simulated values, and real-time Gemini macro-analysis.",
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Settings Overlay Cabinet for API Credentials inputting
@Composable
fun SettingsPanelContent(
    viewModel: EconomicViewModel,
    onDismiss: () -> Unit
) {
    val blsKeyInDb by viewModel.blsApiKey.collectAsStateWithLifecycle()
    val geminiKeyInDb by viewModel.geminiApiKey.collectAsStateWithLifecycle()

    var tempBlsKey by remember { mutableStateOf("") }
    var tempGeminiKey by remember { mutableStateOf("") }

    // Preload keys when state is set
    LaunchedEffect(blsKeyInDb, geminiKeyInDb) {
        tempBlsKey = blsKeyInDb
        tempGeminiKey = geminiKeyInDb
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "API Configuration Control",
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Configure credential keys to customize the dashboard triggers. By default, standard public API limits and pre-loaded models are applied.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // BLS API Key block
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Bureau of Labor Statistics API Key", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(
                    "Entering your official register key lifts limits from 25 queries/day to 500 queries/day and covers longer time spans.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextField(
                    value = tempBlsKey,
                    onValueChange = { tempBlsKey = it },
                    placeholder = { Text("Enter your BLS Registration Key") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bls_api_key_input"),
                    singleLine = true,
                    colors = TextFieldDefaults.colors()
                )
            }
        }

        // Gemini API Key block
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gemini AI API Key Override", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(
                    "Optional key override. If empty, the system automatically defaults to using the credentials secured via the development workspace secret console.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextField(
                    value = tempGeminiKey,
                    onValueChange = { tempGeminiKey = it },
                    placeholder = { Text("Enter custom Gemini API Key") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gemini_api_key_input"),
                    singleLine = true,
                    colors = TextFieldDefaults.colors()
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    viewModel.saveApiKeys(tempBlsKey, tempGeminiKey)
                    onDismiss()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("save_settings_button")
            ) {
                Text("Save Credentials")
            }
        }
    }
}
