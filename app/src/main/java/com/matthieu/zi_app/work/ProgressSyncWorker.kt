package com.matthieu.zi_app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.matthieu.zi_app.MainActivity
import com.matthieu.zi_app.R
import com.matthieu.zi_app.data.local.AndZiDatabase
import com.matthieu.zi_app.data.local.ProgressDatabase
import com.matthieu.zi_app.data.preferences.UserPreferences
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
                    .build()

                val workRequest = PeriodicWorkRequestBuilder<ProgressSyncWorker>(
                    repeatInterval = 2,
                    repeatIntervalTimeUnit = TimeUnit.HOURS
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
                )
            } catch (_: Exception) {
                // Ignore in unit test environments where WorkManager is not initialized
            }
        }

        fun triggerImmediateCheck(context: Context) {
            try {
                val workRequest = OneTimeWorkRequestBuilder<ProgressSyncWorker>().build()
                WorkManager.getInstance(context).enqueue(workRequest)
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

        fun cancelNotification(context: Context) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(NOTIFICATION_ID)
            } catch (_: Exception) {
                // Ignore in environments where notification manager is unavailable
            }
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val andZiDb = AndZiDatabase.getInstance(context)
            val progressDb = ProgressDatabase.getInstance(context)

            val studyRepo = StudyRepository(andZiDb.leitnerDao())
            val progressRepo = ProgressRepository(progressDb.progressDao(), andZiDb.leitnerDao())

            val now = System.currentTimeMillis()
            val expiredCount = studyRepo.getNumExpired(now)
            val boxCounts = studyRepo.getBoxCounts().associate { it.box to it.count }

            val totalBox1 = boxCounts[0] ?: 0
            val totalBox2 = boxCounts[1] ?: 0
            val totalBox3 = boxCounts[2] ?: 0
            val totalBox4 = boxCounts[3] ?: 0
            val totalBox5 = boxCounts[4] ?: 0
            val totalBox6 = boxCounts[5] ?: 0
            val totalBox7 = boxCounts[6] ?: 0
            val totalBox8 = boxCounts[7] ?: 0
            val totalBox9 = boxCounts[8] ?: 0
            val totalBox10 = boxCounts[9] ?: 0

            val allRecords = andZiDb.leitnerDao().getAllStudyRecords()
            val exp2 = allRecords.count { it.box == 1 && it.expTime < now }
            val exp3 = allRecords.count { it.box == 2 && it.expTime < now }
            val exp4 = allRecords.count { it.box == 3 && it.expTime < now }
            val exp5 = allRecords.count { it.box == 4 && it.expTime < now }
            val exp6 = allRecords.count { it.box == 5 && it.expTime < now }
            val exp7 = allRecords.count { it.box == 6 && it.expTime < now }
            val exp8 = allRecords.count { it.box == 7 && it.expTime < now }
            val exp9 = allRecords.count { it.box == 8 && it.expTime < now }
            val exp10 = allRecords.count { it.box >= 9 && it.expTime < now }

            val totalSum = totalBox1 + totalBox2 + totalBox3 + totalBox4 + totalBox5 +
                totalBox6 + totalBox7 + totalBox8 + totalBox9 + totalBox10

            if (totalSum > 0) {
                progressRepo.recordProgressSnapshot(
                    totalBox1 = totalBox1,
                    totalBox2 = totalBox2,
                    totalBox3 = totalBox3,
                    totalBox4 = totalBox4,
                    totalBox5 = totalBox5,
                    totalBox6 = totalBox6,
                    totalBox7 = totalBox7,
                    totalBox8 = totalBox8,
                    totalBox9 = totalBox9,
                    totalBox10 = totalBox10,
                    expiredBox2 = exp2,
                    expiredBox3 = exp3,
                    expiredBox4 = exp4,
                    expiredBox5 = exp5,
                    expiredBox6 = exp6,
                    expiredBox7 = exp7,
                    expiredBox8 = exp8,
                    expiredBox9 = exp9,
                    expiredBox10 = exp10,
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
        val userPreferences = UserPreferences(context)
        val notificationsEnabled = userPreferences.notificationEnabled.value
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        createNotificationChannel(nm)

        val systemNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()

        if (notificationsEnabled && systemNotificationsEnabled && expiredCount > 0) {
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

            val contentText = if (expiredCount == 1) {
                "You have 1 card due for review"
            } else {
                context.getString(R.string.notification_content, expiredCount)
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.zi_icon_notification)
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(contentText)
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
