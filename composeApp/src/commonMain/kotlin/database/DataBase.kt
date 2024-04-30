package database

import platform.createDatabase
import platform.createDriver

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/2/20 19:36
 * @Description : 描述
 * @Version     : 1.0
 */
internal class DataBase {

    private val database = createDatabase(createDriver())

    private val dbQuery = database.configQueries

    internal fun initInternal(aapt: String) {
        dbQuery.initInternal(aapt)
    }

    internal fun getDarkMode(): Long {
        return dbQuery.getDarkMode().executeAsOneOrNull() ?: 0L
    }

    internal fun getAaptPath(): String {
        return dbQuery.getAaptPath().executeAsOneOrNull() ?: ""
    }

    internal fun getFlagDelete(): Boolean {
        return dbQuery.getFlagDelete().executeAsOneOrNull() ?: true
    }

    internal fun getSignerSuffix(): String {
        return dbQuery.getSignerSuffix().executeAsOneOrNull() ?: "_sign"
    }

    internal fun getOutputPath(): String {
        return dbQuery.getOutputPath().executeAsOneOrNull() ?: ""
    }

    internal fun getIsAlignFileSize(): Boolean {
        return dbQuery.getIsAlignFileSize().executeAsOneOrNull() ?: true
    }

    internal fun getKeytoolPath(): String {
        return dbQuery.getKeytoolPath().executeAsOneOrNull() ?: ""
    }

    internal fun getDestStoreType(): String {
        return dbQuery.getDestStoreType().executeAsOneOrNull() ?: "JKS"
    }

    internal fun getDestStoreSize(): Long {
        return dbQuery.getDestStoreSize().executeAsOneOrNull() ?: 1024
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

    internal fun updateOutputPath(outputPath: String) {
        dbQuery.updateOutputPath(outputPath)
    }

    internal fun updateIsAlignFileSize(isAlignFileSize: Boolean) {
        dbQuery.updateIsAlignFileSize(isAlignFileSize)
    }

    internal fun updateKeytoolPath(keytoolPath: String) {
        dbQuery.updateKeytoolPath(keytoolPath)
    }

    internal fun updateDestStoreType(destStoreType: String) {
        dbQuery.updateDestStoreType(destStoreType)
    }

    internal fun updateDestStoreSize(destStoreSize: Long) {
        dbQuery.updateDestStoreSize(destStoreSize)
    }
}