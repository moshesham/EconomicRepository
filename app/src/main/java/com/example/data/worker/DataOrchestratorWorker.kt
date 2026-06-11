package com.example.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.room.Room
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import com.example.data.repository.EconomicRepository
import com.example.data.local.AppDatabase

class DataOrchestratorWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("DataOrchestrator", "Running background update for economic data registry...")
        
        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "economic_vault.db"
        ).fallbackToDestructiveMigration()
         .build()

        val repository = EconomicRepository(db)

        try {
            val seriesList = db.seriesDao().getAllSeries().first()
            if (seriesList.isEmpty()) {
                Log.d("DataOrchestrator", "No series registered yet. Seeding initial data...")
                repository.seedInitialData()
            }
            
            val freshSeriesList = db.seriesDao().getAllSeries().first()
            var successCount = 0
            var failCount = 0

            freshSeriesList.forEach { series ->
                // Refresh data from BLS API
                val res = repository.refreshBLSData(series.id)
                if (res.isSuccess) {
                    successCount++
                } else {
                    failCount++
                }
            }

            repository.logSync(
                sourceName = "Orchestrator",
                status = if (failCount == 0) "SUCCESS" else "FAILURE_DOWNTIME",
                message = "Automated background sync completed. $successCount indicator(s) updated successfully. $failCount failure(s)."
            )

            Log.d("DataOrchestrator", "Update successful. Status recorded in database SyncLog table.")
            return Result.success()
        } catch (e: Exception) {
            Log.e("DataOrchestrator", "Error in DataOrchestrator job execution", e)
            return Result.retry()
        } finally {
            db.close()
        }
    }

    companion object {
        private const val WORK_NAME = "DataOrchestratorBackgroundSync"

        fun scheduleDailyUpdates(context: Context) {
            // Schedule background updates based on the documented frequencies
            // Taking Daily as a baseline for the general orchestrator layer
            val syncRequest = PeriodicWorkRequestBuilder<DataOrchestratorWorker>(24, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        }
    }
}
