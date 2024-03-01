package platform

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kit.ToolsKitDatabase
import java.io.File

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/2/20 17:52
 * @Description : 数据库驱动工厂
 * @Version     : 1.0
 */
actual fun createDriver(): SqlDriver {
    val databaseDirFile = File(System.getProperty("user.home"), ".android_tools_kit")
    if (!databaseDirFile.exists()) {
        databaseDirFile.mkdirs()
    }
    val databaseFile = File(databaseDirFile, "config.db")
    return JdbcSqliteDriver(url = "jdbc:sqlite:" + databaseFile.absolutePath).also {
            if (!databaseFile.exists()) {
                ToolsKitDatabase.Schema.create(it)
            }
        }
}
