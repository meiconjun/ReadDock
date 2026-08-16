package com.readdock.source.runtime

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.security.MessageDigest
import java.util.Base64

@Serializable
data class PluginSignature(
    val algorithm: String,
    val keyId: String,
    val value: String
)

@Serializable
data class SignedPluginEnvelope(
    val formatVersion: Int = 1,
    val payload: String,
    val signature: PluginSignature? = null
)

class PluginTrustStore(
    private val publicKeys: Map<String, String>
) {
    fun find(keyId: String): PublicKey? = runCatching {
        val encoded = Base64.getDecoder().decode(publicKeys[keyId] ?: return null)
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(encoded))
    }.getOrNull()
}

class PluginSignatureVerifier(
    private val trustStore: PluginTrustStore
) {
    fun verify(payload: String, signature: PluginSignature): Boolean {
        if (signature.algorithm != "SHA256withRSA") return false
        val publicKey = trustStore.find(signature.keyId) ?: return false
        return runCatching {
            Signature.getInstance(signature.algorithm).apply {
                initVerify(publicKey)
                update(payload.toByteArray(StandardCharsets.UTF_8))
            }.verify(Base64.getDecoder().decode(signature.value))
        }.getOrDefault(false)
    }
}

sealed interface SignedPayloadResult {
    data class Ready(val payload: String) : SignedPayloadResult
    data class Rejected(val errors: List<String>) : SignedPayloadResult
}

class SignedPayloadReader(
    private val verifier: PluginSignatureVerifier? = null,
    private val requireSignature: Boolean = false
) {
    fun read(packageJson: String, json: Json): SignedPayloadResult {
        val root = runCatching { json.parseToJsonElement(packageJson) }
            .getOrElse { return SignedPayloadResult.Rejected(listOf("JSON 格式错误")) }
        val payloadElement = root.jsonObject["payload"]

        if (payloadElement == null) {
            return if (requireSignature) {
                SignedPayloadResult.Rejected(listOf("远程插件必须带签名"))
            } else {
                SignedPayloadResult.Ready(packageJson)
            }
        }

        val envelope = runCatching {
            json.decodeFromJsonElement<SignedPluginEnvelope>(root)
        }.getOrElse {
            return SignedPayloadResult.Rejected(listOf("插件签名信封格式错误"))
        }
        val signature = envelope.signature
            ?: return SignedPayloadResult.Rejected(listOf("插件缺少签名"))
        val trustedVerifier = verifier
            ?: return SignedPayloadResult.Rejected(listOf("当前没有配置可信公钥"))
        if (!trustedVerifier.verify(envelope.payload, signature)) {
            return SignedPayloadResult.Rejected(listOf("插件签名校验失败或公钥不受信任"))
        }
        return SignedPayloadResult.Ready(envelope.payload)
    }
}

fun sha256Hex(bytes: ByteArray): String = MessageDigest
    .getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { "%02x".format(it) }

fun verifySha256(bytes: ByteArray, expected: String): Boolean =
    sha256Hex(bytes).equals(expected.trim(), ignoreCase = true)
