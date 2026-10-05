package com.matthieu.zi_app.work

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.local.AndZiDatabase
import com.matthieu.zi_app.data.local.ProgressDatabase
import com.matthieu.zi_app.data.preferences.UserPreferences
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.data.repository.VocabularyRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNotificationManager

@RunWith(RobolectricTestRunner::class)
class ProgressSyncWorkerTest {

    private lateinit var context: Context
    private lateinit var andZiDb: AndZiDatabase
    private lateinit var progressDb: ProgressDatabase
    private lateinit var vocRepo: VocabularyRepository
    private lateinit var studyRepo: StudyRepository
    private lateinit var userPrefs: UserPreferences
    private lateinit var notificationManager: NotificationManager
    private lateinit var shadowNotificationManager: ShadowNotificationManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        andZiDb = AndZiDatabase.createInMemory(context)
        progressDb = ProgressDatabase.createInMemory(context)

        // Inject in-memory DB instances
        AndZiDatabase.setTestInstance(andZiDb)
        ProgressDatabase.setTestInstance(progressDb)

        vocRepo = VocabularyRepository(andZiDb.vocabularyDao())
        studyRepo = StudyRepository(andZiDb.leitnerDao())
        userPrefs = UserPreferences(context)

        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowNotificationManager = shadowOf(notificationManager)
    }

    @After
    fun teardown() {
        AndZiDatabase.setTestInstance(null)
        ProgressDatabase.setTestInstance(null)
        andZiDb.close()
        progressDb.close()
    }

    @Test
    fun testNotificationPostedWhenCardsDueAndNotificationsEnabled() = runBlocking {
        userPrefs.setNotificationEnabled(true)

        // Add 1 card due for study
        vocRepo.addVocabulary(Vocabulary("书", "shu1", arrayListOf("book")))

        val worker = TestListenableWorkerBuilder<ProgressSyncWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        val notification = shadowNotificationManager.getNotification(ProgressSyncWorker.NOTIFICATION_ID)
        assertNotNull(notification)

        val shadowNotification = shadowOf(notification)
        val contentText = shadowNotification.contentText.toString()
        assertTrue(contentText.contains("1"))
    }

    @Test
    fun testNoNotificationWhenNoCardsDue() = runBlocking {
        userPrefs.setNotificationEnabled(true)

        val worker = TestListenableWorkerBuilder<ProgressSyncWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        val notification = shadowNotificationManager.getNotification(ProgressSyncWorker.NOTIFICATION_ID)
        assertNull(notification)
    }

    @Test
    fun testNoNotificationWhenNotificationsDisabled() = runBlocking {
        userPrefs.setNotificationEnabled(false)

        // Add 5 cards
        for (i in 1..5) {
            vocRepo.addVocabulary(Vocabulary("字$i", "zi$i", arrayListOf("word $i")))
        }

        val worker = TestListenableWorkerBuilder<ProgressSyncWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        val notification = shadowNotificationManager.getNotification(ProgressSyncWorker.NOTIFICATION_ID)
        assertNull(notification)
    }

    @Test
    fun testCancelNotificationClearsActiveNotification() = runBlocking {
        userPrefs.setNotificationEnabled(true)
        vocRepo.addVocabulary(Vocabulary("书", "shu1", arrayListOf("book")))

        val worker = TestListenableWorkerBuilder<ProgressSyncWorker>(context).build()
        worker.doWork()

        assertNotNull(shadowNotificationManager.getNotification(ProgressSyncWorker.NOTIFICATION_ID))

        ProgressSyncWorker.cancelNotification(context)
        assertNull(shadowNotificationManager.getNotification(ProgressSyncWorker.NOTIFICATION_ID))
    }
}
