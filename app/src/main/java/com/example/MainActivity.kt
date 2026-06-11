package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.repository.EconomicRepository
import com.example.ui.screens.DashboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.EconomicViewModel
import com.example.data.worker.DataOrchestratorWorker

class MainActivity : ComponentActivity() {
    private lateinit var db: AppDatabase
    private lateinit var repository: EconomicRepository
    private lateinit var viewModel: EconomicViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize SQLite Room database
        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "economic_vault.db"
        ).build()

        repository = EconomicRepository(db)
        viewModel = EconomicViewModel(repository)

        // Schedule background data updates to iterate through data registry sources
        DataOrchestratorWorker.scheduleDailyUpdates(applicationContext)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DashboardScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
