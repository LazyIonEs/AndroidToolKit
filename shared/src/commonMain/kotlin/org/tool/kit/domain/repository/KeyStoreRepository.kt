package org.tool.kit.domain.repository

interface KeyStoreRepository {
    /** 生成密钥库和证书，返回输出路径或失败结果。 */
    suspend fun generate(request: org.tool.kit.domain.keystore.GenerateKeyStoreRequest): org.tool.kit.domain.keystore.GenerateKeyStoreOutcome
    /** Null on load failure; an empty list is a successfully loaded empty store. */
    suspend fun loadAliases(path: String, password: String): List<String>?
    /** 验证指定别名的私钥是否能被给定密码读取，无法读取时返回 false。 */
    suspend fun validateAliasPassword(path: String, storePassword: String, alias: String?, password: String): Boolean
}
