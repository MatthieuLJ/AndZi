package com.matthieu.zi_app

import android.app.Application
import com.matthieu.zi_app.data.AppContainer
import com.matthieu.zi_app.data.DefaultAppContainer
import com.matthieu.zi_app.data.local.AndZiDatabase
import com.matthieu.zi_app.work.ProgressSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AndZiApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val appContainer: AppContainer by lazy {
        DefaultAppContainer(this)
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize Pinyin subsystem
        Pinyin.prepare_pinyin_process()

        // Ensure CC-CEDICT dictionary database is extracted and ready in background
        applicationScope.launch {
            try {
                AndZiDatabase.ensureDictionaryExtracted(applicationContext)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Schedule periodic WorkManager progress sync
        ProgressSyncWorker.schedulePeriodicWork(this)
    }
}
