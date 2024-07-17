package platform

import app.cash.sqldelight.db.SqlDriver
import kit.ToolKitDatabase

expect fun createDriver(): SqlDriver

fun createDatabase(driver: SqlDriver): ToolKitDatabase {
    return ToolKitDatabase(driver)
}

@Throws(RustException::class)
expect fun resizePng(inputPath: String, outputPath: String, width: UInt, height: UInt, typIdx: UByte = 3u)

@Throws(RustException::class)
expect fun resizeFir(inputPath: String, outputPath: String, width: UInt, height: UInt)

@Throws(RustException::class)
expect fun quantize(
    inputPath: String,
    outputPath: String,
    minimum: UByte = 90u,
    target: UByte = 100u,
    speed: Int = 1,
    preset: UByte = 6u
)

@Throws(RustException::class)
expect fun mozJpeg(inputPath: String, outputPath: String, quality: Float = 85f)

class RustException(
    message: String?
) : Exception(message)