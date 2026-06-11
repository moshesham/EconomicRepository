package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

data class DataSourceCategory(
    val name: String,
    val sources: List<DataSourceDetail>
)

data class DataSourceDetail(
    val name: String,
    val type: String,
    val coverage: String,
    val frequency: String,
    val sla: String,
    val highlights: String
)

val DATA_SOURCES = listOf(
    DataSourceCategory(
        "Financial Markets & Equity",
        listOf(
            DataSourceDetail("Yahoo Finance (yfinance)", "REST API", "Global stock OHLCV", "Daily", "1 day", "Historical price, Dividends, Splits"),
            DataSourceDetail("CBOE VIX (Volatility Index)", "CSV Download", "VIX index, futures, curves", "Daily", "6 hours", "VIX, VIX9D, VIX3M, VIX1Y"),
            DataSourceDetail("SEC EDGAR", "REST API", "10-K, 10-Q, 8-K, 13F", "Real-time", "1 hour", "Financial statements, Insider trading")
        )
    ),
    DataSourceCategory(
        "Macroeconomic Indicators",
        listOf(
            DataSourceDetail("FRED", "REST API", "400,000+ US economic series", "Varies", "24 hours", "GDP, CPI, Unemployment, Rates"),
            DataSourceDetail("IMF SDMX", "REST API", "188 IMF countries", "Varies", "1 month", "Exchange Rates, WEO Projections"),
            DataSourceDetail("World Bank", "REST API", "1,400+ indicators (217 countries)", "Annual", "3 months", "GDP, Population, Trade"),
            DataSourceDetail("OECD", "REST API", "38 member countries", "Monthly", "1 month", "CLI, Labor, National Accounts"),
            DataSourceDetail("BLS", "REST API", "US labor market, CPI", "Monthly", "2 weeks", "Employment, Wages, CPI components"),
            DataSourceDetail("Census Bureau (EITS)", "REST API", "Real-time economic indicators", "Weekly/Monthly", "1 week", "Retail Sales, Construction, Trade"),
            DataSourceDetail("EIA", "REST API", "US energy markets", "Daily/Weekly", "1 day", "Crude Oil, Natural Gas, Electricity")
        )
    ),
    DataSourceCategory(
        "Cryptocurrency",
        listOf(
            DataSourceDetail("CoinGecko API", "REST API", "10,000+ cryptocurrencies", "Real-time", "5 mins", "OHLCV, Market cap, Volume")
        )
    ),
    DataSourceCategory(
        "Derivatives & Market Depth",
        listOf(
            DataSourceDetail("Yahoo Finance Options", "REST API", "Listed equity options", "Daily", "1 day", "Implied volatility, Greeks, Open interest"),
            DataSourceDetail("ICI ETF Flows", "CSV / Scrape", "US mutual fund & ETF flows", "Weekly", "1 week", "Inflows/outflows, Asset-class rotation")
        )
    ),
    DataSourceCategory(
        "Sentiment & News",
        listOf(
            DataSourceDetail("News Sentiment", "CSV Archive + DB", "Financial news sentiment", "Daily", "1 day", "Sentiment score, Daily median/mean"),
            DataSourceDetail("Google Trends", "Web Scrape", "Search volume trends", "Daily", "N/A", "Search interest normalization")
        )
    ),
    DataSourceCategory(
        "Derived Features (Models)",
        listOf(
            DataSourceDetail("Technical Indicators", "Derived", "MA, MACD, RSI, VWA", "On-demand", "N/A", "Built from yfinance OHLCV"),
            DataSourceDetail("Margin Call Risk", "Derived", "Leverage & Drawdown", "On-demand", "N/A", "Built from Stocks, VIX"),
            DataSourceDetail("Financial Health Scores", "Derived", "Leverage, Liquidity, Profitability", "On-demand", "N/A", "Built from SEC filings"),
            DataSourceDetail("Insider Trading", "Derived", "Buy/sell ratios", "On-demand", "N/A", "Built from SEC Form 4"),
            DataSourceDetail("Sector Rotation", "Derived", "Phases (Expansion, Contraction)", "On-demand", "N/A", "Built from ETF Flows"),
            DataSourceDetail("Leverage Metrics", "Derived", "Leveraged ETFs, Options", "On-demand", "N/A", "Built from Yahoo/Options/SEC")
        )
    )
)

@Composable
fun DataSourcesView(viewModel: com.example.ui.viewmodel.EconomicViewModel, modifier: Modifier = Modifier) {
    val logs by viewModel.recentLogs.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text(
                text = "Data Sources & Integrations",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "MacroPulse aggregates data from verified APIs, external downloads, and derived intelligence layers with scheduled background update intervals.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        item {
            OrchestratorLogsPanel(
                logs = logs,
                onClear = { viewModel.clearAllLogs() }
            )
        }

        items(DATA_SOURCES) { category ->
            DataSourceCategoryCard(category)
        }
        
        item {
            Spacer(modifier = Modifier.height(80.dp)) // padding for bottom nav
        }
    }
}

@Composable
fun DataSourceCategoryCard(category: DataSourceCategory) {
    Column {
        Text(
            text = category.name.uppercase(),
            fontSize = 12.sp,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            category.sources.forEach { source ->
                DataSourceItemCard(source)
            }
        }
    }
}

@Composable
fun DataSourceItemCard(source: DataSourceDetail) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = source.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = source.type,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                InfoColumn(label = "Coverage", value = source.coverage, modifier = Modifier.weight(1f))
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                InfoColumn(label = "Frequency", value = source.frequency, modifier = Modifier.weight(1f))
                InfoColumn(label = "SLA (Staleness)", value = source.sla, modifier = Modifier.weight(1f))
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Tracking: ${source.highlights}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            letterSpacing = 0.5.sp
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun OrchestratorLogsPanel(
    logs: List<com.example.data.local.SyncLogEntity>,
    onClear: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(
                                if (logs.any { it.status.startsWith("FAILURE") }) Color(0xFFEF4444) 
                                else if (logs.isEmpty()) Color(0xFF9CA3AF)
                                else Color(0xFF10B981)
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Orchestrator Sync Monitor",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (logs.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Text("Clear Monitor", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No sync activities logged yet. Data will refresh in background.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    logs.take(5).forEach { log ->
                        SyncLogItemRow(log)
                    }
                    if (logs.size > 5) {
                        Text(
                            text = "+ ${logs.size - 5} database logs hidden",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SyncLogItemRow(log: com.example.data.local.SyncLogEntity) {
    val formatter = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault())
    val formattedTime = formatter.format(java.util.Date(log.timestamp))

    val (badgeText, badgeBg, badgeTextCol) = when (log.status) {
        "SUCCESS" -> Triple("Success", Color(0xFFD1FAE5), Color(0xFF065F46))
        "FAILURE_RATE_LIMIT" -> Triple("Rate Limited (429)", Color(0xFFFEF3C7), Color(0xFF92400E))
        "FAILURE_KEYS" -> Triple("Auth / Key Error", Color(0xFFFFE4E6), Color(0xFF9F1239))
        "FAILURE_CONN" -> Triple("Network Timeout", Color(0xFFF3F4F6), Color(0xFF374151))
        else -> Triple("Error / Offline", Color(0xFFFEE2E2), Color(0xFF991B1B))
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = log.sourceName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formattedTime,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = log.message,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Surface(
                color = badgeBg,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = badgeText.uppercase(),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeTextCol,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}
