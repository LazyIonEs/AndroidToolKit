package org.tool.kit.model

/**
 * @author      : LazyIonEs
 * @description : 描述
 * @createDate  : 2026/1/29 09:42
 */

/**
 * Apk签名信息结果
 */
data class VerifierResult(
    val isSuccess: Boolean, // 验证是否成功
    val isApk: Boolean, // 是否是apk验证
    val path: String, // 文件路径
    val name: String, // 文件名称
    val data: ArrayList<Verifier> // 验证信息
)

/**
 * 签名信息结果
 */
data class Verifier(
    val version: String,
    val subject: String,
    val validFrom: String,
    val validUntil: String,
    val publicKeyType: String,
    val modulus: String,
    val signatureType: String,
    val md5: String,
    val sha1: String,
    val sha256: String
)