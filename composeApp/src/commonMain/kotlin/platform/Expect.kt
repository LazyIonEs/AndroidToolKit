package platform

import app.cash.sqldelight.db.SqlDriver
import kit.ToolsKitDatabase

expect fun createDriver(): SqlDriver

fun createDatabase(driver: SqlDriver): ToolsKitDatabase {
    return ToolsKitDatabase(driver)
}