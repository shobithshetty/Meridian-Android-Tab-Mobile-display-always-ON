package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FriendClock::class, ClockSettings::class, AlarmItem::class],
    version = 19,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clockDao(): ClockDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE clock_settings ADD COLUMN circadianShiftEnabled INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE clock_settings ADD COLUMN eyeBreakEnabled INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE clock_settings ADD COLUMN eyeBreakIntervalMinutes INTEGER NOT NULL DEFAULT 20"
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE clock_settings ADD COLUMN layoutStyle TEXT NOT NULL DEFAULT 'BOLD'"
                )
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE clock_settings ADD COLUMN nightDimMode TEXT NOT NULL DEFAULT 'AUTO'"
                )
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE clock_settings ADD COLUMN backdropPattern TEXT NOT NULL DEFAULT 'NONE'"
                )
            }
        }

        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clock_settings ADD COLUMN weatherEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE clock_settings ADD COLUMN weatherUnit TEXT NOT NULL DEFAULT 'C'")
                db.execSQL("ALTER TABLE clock_settings ADD COLUMN weatherAlertsEnabled INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clock_settings ADD COLUMN alarmStyle TEXT NOT NULL DEFAULT 'GENTLE'")
                db.execSQL("ALTER TABLE clock_settings ADD COLUMN autoBackupUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE clock_settings ADD COLUMN lastAutoBackupEpochDay INTEGER NOT NULL DEFAULT -1")
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clock_settings ADD COLUMN countdownsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE clock_settings ADD COLUMN routinesJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE clock_settings ADD COLUMN holidayRegion TEXT NOT NULL DEFAULT 'AUTO'")
                db.execSQL("ALTER TABLE clock_settings ADD COLUMN showDayInfo INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clock_settings ADD COLUMN transitionWarningsEnabled INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clock_settings ADD COLUMN keepAwakeMode TEXT NOT NULL DEFAULT 'ALWAYS'")
                db.execSQL("ALTER TABLE clock_settings ADD COLUMN autoStartOnBoot INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE clock_settings ADD COLUMN hasHomeLocation INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE clock_settings ADD COLUMN homeLatitude REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE clock_settings ADD COLUMN homeLongitude REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE clock_settings ADD COLUMN rhythmStripEnabled INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wallpaper_clock_db"
                )
                    .addMigrations(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19)
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
