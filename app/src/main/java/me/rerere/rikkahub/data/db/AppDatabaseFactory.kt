package me.rerere.rikkahub.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.fts.createMessageFtsTables
import me.rerere.rikkahub.data.db.migrations.Migration_11_12
import me.rerere.rikkahub.data.db.migrations.Migration_13_14
import me.rerere.rikkahub.data.db.migrations.Migration_14_15
import me.rerere.rikkahub.data.db.migrations.Migration_15_16
import me.rerere.rikkahub.data.db.migrations.Migration_27_28
import me.rerere.rikkahub.data.db.migrations.Migration_30_31
import me.rerere.rikkahub.data.db.migrations.Migration_32_33
import me.rerere.rikkahub.data.db.migrations.Migration_33_34
import me.rerere.rikkahub.data.db.migrations.Migration_6_7
import me.rerere.rikkahub.data.model.MemoryFile
import java.io.File

/** Shared schema, migrations and extensions for the app and staged backup validation. */
internal object AppDatabaseFactory {
    fun create(context: Context, name: String = SQLiteConfiguration.DATABASE_NAME): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(
                Migration_6_7,
                Migration_11_12,
                Migration_13_14,
                Migration_14_15,
                Migration_15_16,
                Migration_27_28,
                Migration_30_31,
                Migration_32_33,
                Migration_33_34,
            )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    // 旧版本搜索用 jieba 分词，会把词典解压到这里（约 14MB），现在不再需要
                    File(context.filesDir, "simple_dict").takeIf { it.exists() }?.deleteRecursively()
                    createMessageFtsTables(db)
                    migrateLegacyPinnedMemoryPaths(db)
                }
            })
            .openHelperFactory(SQLiteConfiguration.openHelperFactory(context))
            .build()
}

/**
 * 幂等迁移旧版置顶记忆文件名（/profile.md -> /core.md，/preferences.md -> /state.md），
 * 兼容从 v1.4.1（已是 Room v34）原地升级与导入原版 RikkaHub 备份的数据库。
 */
private fun migrateLegacyPinnedMemoryPaths(db: SupportSQLiteDatabase) {
    runCatching {
        val hasTable = db.query(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'memory_file'"
        ).use { it.moveToFirst() }
        if (!hasTable) return

        db.beginTransaction()
        try {
            listOf(
                Triple(MemoryFile.LEGACY_PROFILE_PATH, MemoryFile.CORE_PATH, "profile" to "core"),
                Triple(MemoryFile.LEGACY_PREFERENCES_PATH, MemoryFile.STATE_PATH, "preferences" to "state"),
            ).forEach { (legacyPath, targetPath, names) ->
                val (oldName, newName) = names
                val oldLf = "---\nname: $oldName\n"
                val newLf = "---\nname: $newName\n"
                val oldCrlf = "---\r\nname: $oldName\r\n"
                val newCrlf = "---\r\nname: $newName\r\n"
                db.execSQL(
                    """
                    UPDATE memory_file
                    SET path = ?,
                        content = CASE
                            WHEN instr(content, ?) = 1 THEN ? || substr(content, ?)
                            WHEN instr(content, ?) = 1 THEN ? || substr(content, ?)
                            ELSE content
                        END
                    WHERE path = ?
                      AND NOT EXISTS (
                          SELECT 1 FROM memory_file AS existing
                          WHERE existing.assistant_id = memory_file.assistant_id
                            AND existing.path = ?
                      )
                    """.trimIndent(),
                    arrayOf<Any?>(
                        targetPath,
                        oldLf, newLf, oldLf.length + 1,
                        oldCrlf, newCrlf, oldCrlf.length + 1,
                        legacyPath,
                        targetPath,
                    ),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
