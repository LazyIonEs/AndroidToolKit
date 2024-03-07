package platform

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kit.ToolsKitDatabase
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
    return JdbcSqliteDriver(
        url = "jdbc:sqlite:${dbFile.absolutePath}",
        properties = Properties(),
        schema = ToolsKitDatabase.Schema,
        migrateEmptySchema = dbFile.exists(),
    ).also {
        ToolsKitDatabase.Schema.create(it)
    }
}

private fun getDatabaseFile(): File {
    return File(
        File(System.getProperty("user.home"), ".android_tools_kit").also { if (!it.exists()) it.mkdirs() },
        "config.db"
    )
}
