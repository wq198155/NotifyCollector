package com.example.notifycollector.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [GroupEntity::class, NotificationEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        /**
         * v1 -> v2：分组新增"有效期（分钟）"字段。
         * 注意：Room 2.6 会校验默认值，SQL 的 DEFAULT 必须与实体 @ColumnInfo(defaultValue) 一致，
         * 否则启动时会抛 IllegalStateException（schema 校验失败）。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE groups ADD COLUMN expireMinutes INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * v2 -> v3：通知表新增"已读"字段，用于左滑"已读"后变灰并沉底。
         * Boolean 在 SQLite 中以 INTEGER 存储，0=false / 1=true。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE notifications ADD COLUMN read INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * v3 -> v4：分组新增"排序权重"字段，用于首页手动拖拽排序后持久化。
         * SQL 的 DEFAULT 0 必须与实体 @ColumnInfo(defaultValue="0") 一致，否则 Room 校验失败。
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE groups ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * v4 -> v5：通知新增"回收站标记"字段。可为空（默认 null = 正常在列），
         * 超过 14 天的通知会被自动把 recycledAt 置为当时时间戳搬进回收站。
         * 可空列不需要默认值，SQL 不写 NOT NULL、已有行自动为 NULL，与实体 nullable 定义一致。
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE notifications ADD COLUMN recycledAt INTEGER"
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val inst = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "notify_collector_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = inst
                inst
            }
        }
    }
}
