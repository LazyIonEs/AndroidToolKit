package org.tool.kit.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.tool.kit.BuildConfig
import org.tool.kit.domain.repository.SavedSigningProfile
import org.tool.kit.domain.repository.SigningProfileRepository
import org.tool.kit.domain.signing.SigningCredentials
import org.tool.kit.utils.isMac
import org.tool.kit.utils.isWindows
import uniffi.toolkit.signingProfilesKey
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** 密码用系统凭据库持有的密钥加密，展开列表时只读取路径和别名。 */
internal class JvmSigningProfileRepository(
    private val io: CoroutineDispatcher,
    private val file: Path = signingProfilesFile(),
    private val keyProvider: (ByteArray?) -> ByteArray = ::signingProfilesKey,
) : SigningProfileRepository {
    @Serializable
    private data class Record(val path: String, val alias: String, val encryptedPasswords: String) {
        fun profile() = SavedSigningProfile(path, alias)
    }

    @Serializable
    private data class Passwords(val store: String, val alias: String) {
        override fun toString() = "Passwords(redacted)"
    }

    override suspend fun list(): List<SavedSigningProfile> = withContext(io) { read().map(Record::profile) }

    override suspend fun load(profile: SavedSigningProfile): SigningCredentials? = withContext(io) {
        val record = read().firstOrNull { it.profile() == profile } ?: return@withContext null
        val encrypted = Base64.getDecoder().decode(record.encryptedPasswords)
        val iv = encrypted.copyOfRange(0, IV_SIZE)
        val key = vaultMutex.withLock { vaultKey() }
        val cipher = cipher(Cipher.DECRYPT_MODE, key, iv, profile)
        val decoded = cipher.doFinal(encrypted, IV_SIZE, encrypted.size - IV_SIZE)
        try {
            val passwords = Json.decodeFromString<Passwords>(decoded.decodeToString())
            SigningCredentials(profile.path, passwords.store, profile.alias, passwords.alias)
        } finally {
            decoded.fill(0)
        }
    }

    override suspend fun save(credentials: SigningCredentials): List<SavedSigningProfile> = withContext(io) {
        val profile = SavedSigningProfile(credentials.path, requireNotNull(credentials.alias))
        vaultMutex.withLock {
            Files.createDirectories(file.parent)
            // 多个应用进程也按顺序读取和替换文件，共用同一个凭据库密钥。
            FileChannel.open(file.resolveSibling("${file.fileName}.lock"), CREATE, WRITE).use { channel ->
                channel.lock().use {
                    val records = read()
                    val iv = ByteArray(IV_SIZE).also(random::nextBytes)
                    val cipher = cipher(Cipher.ENCRYPT_MODE, vaultKey(createIfMissing = records.isEmpty()), iv, profile)
                    val passwords = Json.encodeToString(Passwords(credentials.storePassword, credentials.aliasPassword)).encodeToByteArray()
                    val encrypted = try {
                        Base64.getEncoder().encodeToString(iv + cipher.doFinal(passwords))
                    } finally {
                        passwords.fill(0)
                    }
                    val updated = listOf(Record(profile.path, profile.alias, encrypted)) + records.filterNot { it.profile() == profile }
                    write(updated)
                    updated.map(Record::profile)
                }
            }
        }
    }

    private fun read(): List<Record> = try {
        Json.decodeFromString(Files.readString(file))
    } catch (_: NoSuchFileException) {
        emptyList()
    }

    private fun write(records: List<Record>) {
        val temporary = Files.createTempFile(file.parent, "signing-profiles-", ".tmp")
        try {
            Files.writeString(temporary, Json.encodeToString(records))
            Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun vaultKey(createIfMissing: Boolean = false): SecretKeySpec {
        val candidate = if (createIfMissing) ByteArray(32).also(random::nextBytes) else null
        try {
            val key = keyProvider(candidate)
            try {
                return SecretKeySpec(key, "AES")
            } finally {
                key.fill(0)
            }
        } finally {
            candidate?.fill(0)
        }
    }

    private fun cipher(mode: Int, key: SecretKeySpec, iv: ByteArray, profile: SavedSigningProfile): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(128, iv))
            updateAAD(Json.encodeToString(listOf(profile.path, profile.alias)).encodeToByteArray())
        }

    private companion object {
        const val IV_SIZE = 12
        val random = SecureRandom()
        val vaultMutex = Mutex()
    }
}

private fun signingProfilesFile(): Path {
    val home = Path.of(System.getProperty("user.home"))
    val data = when {
        isMac -> home.resolve("Library/Application Support")
        isWindows -> System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)?.let(Path::of) ?: home.resolve("AppData/Local")
        else -> System.getenv("XDG_DATA_HOME")?.takeIf(String::isNotBlank)?.let(Path::of)
            ?.takeIf(Path::isAbsolute) ?: home.resolve(".local/share")
    }
    return data.resolve(BuildConfig.APP_NAME).resolve("signing-profiles.json")
}
