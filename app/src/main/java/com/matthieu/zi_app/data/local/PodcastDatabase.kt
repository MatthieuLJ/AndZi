package com.matthieu.zi_app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.matthieu.zi_app.data.local.dao.PodcastDao
import com.matthieu.zi_app.data.local.entity.PodcastChannelEntity
import com.matthieu.zi_app.data.local.entity.PodcastEpisodeEntity
import com.matthieu.zi_app.data.local.entity.PodcastSentenceEntity
import com.matthieu.zi_app.data.local.entity.PodcastVocabEntity

@Database(
    entities = [
        PodcastChannelEntity::class,
        PodcastEpisodeEntity::class,
        PodcastSentenceEntity::class,
        PodcastVocabEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class PodcastDatabase : RoomDatabase() {

    abstract fun podcastDao(): PodcastDao

    companion object {
        const val DATABASE_NAME = "podcast_db"
        const val DATABASE_VERSION = 4

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE podcast_episode ADD COLUMN target_hsk_level INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE podcast_channel ADD COLUMN supports_paced_highlighting INTEGER NOT NULL DEFAULT 1")
                db.execSQL("UPDATE podcast_channel SET supports_paced_highlighting = 0 WHERE channel_id IN (3, 4)")
            }
        }

        @Volatile
        private var instance: PodcastDatabase? = null

        fun getInstance(context: Context): PodcastDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        fun resetInstanceForTesting() {
            instance?.close()
            instance = null
        }

        private fun buildDatabase(context: Context): PodcastDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                PodcastDatabase::class.java,
                DATABASE_NAME
            )
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
            .addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    seedDefaultChannels(db)
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    seedDefaultChannels(db)
                }

                private fun seedDefaultChannels(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        INSERT OR IGNORE INTO podcast_channel (channel_id, title, chinese_title, provider_type, feed_url, intro_offset_ms, outro_offset_ms, is_enabled, supports_paced_highlighting)
                        VALUES (1, 'Slow Chinese', '慢速中文', 'RSS', 'https://kitchenknif.github.io/SlowChinese/podcast.rss', 3000, 4000, 1, 1)
                    """)
                    db.execSQL("""
                        INSERT OR IGNORE INTO podcast_channel (channel_id, title, chinese_title, provider_type, feed_url, intro_offset_ms, outro_offset_ms, is_enabled, supports_paced_highlighting)
                        VALUES (2, 'Reading China', '中文天天读', 'RSS', 'https://kitchenknif.github.io/SlowChinese/podcast.rss', 5000, 6000, 1, 1)
                    """)
                    db.execSQL("""
                        UPDATE podcast_channel 
                        SET intro_offset_ms = 5000, outro_offset_ms = 6000, supports_paced_highlighting = 1
                        WHERE channel_id = 2 AND (intro_offset_ms != 5000 OR outro_offset_ms != 6000 OR supports_paced_highlighting != 1)
                    """)
                    db.execSQL("""
                        INSERT OR IGNORE INTO podcast_channel (channel_id, title, chinese_title, provider_type, feed_url, intro_offset_ms, outro_offset_ms, is_enabled, supports_paced_highlighting)
                        VALUES (3, 'MaoMi Chinese', '猫咪中文', 'RSS', 'https://feeds.buzzsprout.com/1426696.rss', 12000, 16000, 1, 0)
                    """)
                    db.execSQL("""
                        UPDATE podcast_channel 
                        SET intro_offset_ms = 12000, outro_offset_ms = 16000, is_enabled = 1, supports_paced_highlighting = 0
                        WHERE channel_id = 3 AND (intro_offset_ms != 12000 OR outro_offset_ms != 16000 OR is_enabled != 1 OR supports_paced_highlighting != 0)
                    """)
                    db.execSQL("""
                        INSERT OR IGNORE INTO podcast_channel (channel_id, title, chinese_title, provider_type, feed_url, intro_offset_ms, outro_offset_ms, is_enabled, supports_paced_highlighting)
                        VALUES (4, 'TeaTime Chinese', '茶歇中文', 'RSS', 'https://teatimechinese.libsyn.com/rss', 8000, 10000, 0, 0)
                    """)
                    db.execSQL("""
                        UPDATE podcast_channel 
                        SET supports_paced_highlighting = 0
                        WHERE channel_id = 4 AND supports_paced_highlighting != 0
                    """)
                }
            })
            .fallbackToDestructiveMigration()
            .build()
        }
    }
}
