package database

import platform.DatabaseDriverFactory
import platform.createDatabase

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/2/20 19:36
 * @Description : 描述
 * @Version     : 1.0
 */
internal class DataBase(databaseDriverFactory: DatabaseDriverFactory) {

    private val database = createDatabase(databaseDriverFactory.createDriver())

    private val dbQuery = database.configQueries

    internal fun getDarkMode(): Long {
        return dbQuery.getDarkMode().executeAsOne()
    }

    internal fun getAaptPath(): String {
        return dbQuery.getAaptPath().executeAsOne()
    }

    internal fun getFlagDelete(): Boolean {
        return dbQuery.getFlagDelete().executeAsOne()
    }

    internal fun getSignerSuffix(): String {
        return dbQuery.getSignerSuffix().executeAsOne()
    }

    internal fun updateAaptPath(aaptPath: String) {
        dbQuery.updateAaptPath(aaptPath)
    }

    internal fun updateFlagDelete(flagDelete: Boolean) {
        dbQuery.updateFlagDelete(flagDelete)
    }

    internal fun updateSignerSuffix(signerSuffix: String) {
        dbQuery.updateSignerSuffix(signerSuffix)
    }

    internal fun updateDarkMode(darkMode: Long) {
        dbQuery.updateDarkMode(darkMode)
    }
}