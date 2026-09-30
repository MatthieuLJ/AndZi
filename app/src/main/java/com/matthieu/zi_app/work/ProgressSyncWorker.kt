package com.matthieu.zi_app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.matthieu.zi_app.MainActivity
import com.matthieu.zi_app.R
import com.matthieu.zi_app.data.local.AndZiDatabase
import com.matthieu.zi_app.data.local.ProgressDatabase
import com.matthieu.zi_app.data.repository.ProgressRepository
import com.matthieu.zi_app.data.repository.StudyRepository
import java.util.concurrent.TimeUnit

/**
 * Modern WorkManager background worker for logging study statistics and sending study reminder notifications.
 */
class ProgressSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "andzi_progress_sync_work"
        const val CHANNEL_ID = "andzi_reminders"
        const val NOTIFICATION_ID = 1001

        fun schedulePeriodicWork(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .setRequiresBatteryNotLow(true)
                    .build()

                val workRequest = PeriodicWorkRequestBuilder<ProgressSyncWorker>(
                    repeatInterval = 2,
                    repeatIntervalTimeUnit = TimeUnit.HOURS
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
            } catch (_: Exception) {
                // Ignore in unit test environments where WorkManager is not initialized
            }
        }

        fun cancelPeriodicWork(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            } catch (_: Exception) {
                // Ignore in unit test environments where WorkManager is not initialized
            }
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val andZiDb = AndZiDatabase.getInstance(context)
            val progressDb = ProgressDatabase.getInstance(context)

            val studyRepo = StudyRepository(andZiDb.leitnerDao())
            val progressRepo = ProgressRepository(progressDb.progressDao())

            val now = System.currentTimeMillis()
            val expiredCount = studyRepo.getNumExpired(now)
            val boxCounts = studyRepo.getBoxCounts().associate { it.box to it.count }

            val totalBox1 = boxCounts[1] ?: 0
            val totalBox2 = boxCounts[2] ?: 0
            val totalBox3 = boxCounts[3] ?: 0
            val totalBox4 = boxCounts[4] ?: 0
            val totalBox5 = boxCounts[5] ?: 0

            if (totalBox1 + totalBox2 + totalBox3 + totalBox4 + totalBox5 > 0) {
                progressRepo.recordProgressSnapshot(
                    totalBox1 = totalBox1,
                    totalBox2 = totalBox2,
                    totalBox3 = totalBox3,
                    totalBox4 = totalBox4,
                    totalBox5 = totalBox5,
                    expiredBox2 = 0,
                    expiredBox3 = 0,
                    expiredBox4 = 0,
                    expiredBox5 = 0,
                    date = now
                )
            }

            // Reminders
            checkAndPostNotification(expiredCount)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun checkAndPostNotification(expiredCount: Int) {
        val prefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean("notification_enabled", true)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        createNotificationChannel(nm)

        if (notificationsEnabled && expiredCount > 25) {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("start_test", true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.zi_icon_notification)
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(context.getString(R.string.notification_content, expiredCount))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            nm.notify(NOTIFICATION_ID, notification)
        } else {
            nm.cancel(NOTIFICATION_ID)
        }
    }

    private fun createNotificationChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Study Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Periodic reminders when Chinese flashcards are due for review"
            }
            nm.createNotificationChannel(channel)
        }
    }
}
