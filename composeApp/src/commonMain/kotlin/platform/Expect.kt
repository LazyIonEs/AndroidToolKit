package platform

import app.cash.sqldelight.db.SqlDriver
import kit.ToolKitDatabase

expect fun createDriver(): SqlDriver

fun createDatabase(driver: SqlDriver): ToolKitDatabase {
    return ToolKitDatabase(driver)
}