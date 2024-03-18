package platform

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kit.ToolKitDatabase
import java.io.File
import java.util.Properties

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/2/20 17:52
 * @Description : 数据库驱动工厂
 * @Version     : 1.0
 */
actual fun createDriver(): SqlDriver {
    val dbFile = getDatabaseFile()
    // 后续删除
    if (!dbFile.exists()) {
        val oldDbDir = getOldDatabaseDir()
        if (oldDbDir.exists()) {
            oldDbDir.renameTo(dbFile.parentFile)
        }
    }
    // 移动至getDatabaseFile()
    dbFile.parentFile.also {  if (!it.exists()) it.mkdirs() }
    return JdbcSqliteDriver(
        url = "jdbc:sqlite:${dbFile.absolutePath}",
        properties = Properties(),
        schema = ToolKitDatabase.Schema,
        migrateEmptySchema = dbFile.exists(),
    ).also {
        ToolKitDatabase.Schema.create(it)
    }
}

private fun getDatabaseFile() = File(File(System.getProperty("user.home"), ".android_tool_kit"), "config.db")

private fun getOldDatabaseDir() = File(System.getProperty("user.home"), ".android_tools_kit")
