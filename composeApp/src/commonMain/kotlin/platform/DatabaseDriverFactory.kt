package platform

import app.cash.sqldelight.db.SqlDriver
import kit.ToolsKitDatabase

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver

}

fun createDatabase(driver: SqlDriver): ToolsKitDatabase {
    return ToolsKitDatabase(driver)
}