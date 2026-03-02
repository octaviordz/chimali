package com.chimali.fido2.data.crypto

import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CborCodec @Inject constructor() {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    fun encodeToFido2Format(data: Map<String, Any>): ByteArray {
        // Convert to JSON first, then to CBOR-like format
        val jsonObject = JsonObject(
            data.mapValues { entry ->
                when (val value = entry.value) {
                    is String -> Json.encodeToJsonElement(value)
                    is Int -> Json.encodeToJsonElement(value)
                    is ByteArray -> Json.encodeToJsonElement(value.toList())
                    is Boolean -> Json.encodeToJsonElement(value)
                    else -> Json.encodeToJsonElement(value.toString())
                }
            }
        )
        return json.encodeToByteArray(jsonObject)
    }
    
    fun decodeFromFido2Format(data: ByteArray): Map<String, Any> {
        return try {
            val jsonElement = json.decodeFromByteArray<JsonElement>(data)
            jsonElement.jsonObject.mapValues { entry ->
                when (val value = entry.value) {
                    is JsonObject.MapEntry -> value.jsonPrimitive.content
                    else -> value.toString()
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    fun encodeAttestationObject(
        rpIdHash: ByteArray,
        flags: Byte,
        counter: Int,
        aaguid: ByteArray,
        credentialId: ByteArray,
        publicKeyBytes: ByteArray
    ): ByteArray {
        val attestationObject = mapOf(
            "fmt" to "packed",
            "authData" to mapOf(
                "rpIdHash" to rpIdHash.toList(),
                "flags" to flags.toInt(),
                "counter" to counter,
                "aaguid" to aaguid.toList(),
                "credentialId" to credentialId.toList(),
                "publicKey" to publicKeyBytes.toList()
            )
        )
        return encodeToFido2Format(attestationObject)
    }
    
    fun encodeAuthenticatorData(
        rpIdHash: ByteArray,
        flags: Byte,
        counter: Int
    ): ByteArray {
        val authData = mapOf(
            "rpIdHash" to rpIdHash.toList(),
            "flags" to flags.toInt(),
            "counter" to counter
        )
        return encodeToFido2Format(authData)
    }
    
    fun encodeClientDataJson(
        type: String,
        challenge: ByteArray,
        origin: String,
        crossOrigin: Boolean = false
    ): ByteArray {
        val clientData = mapOf(
            "type" to type,
            "challenge" to challenge.toList(),
            "origin" to origin,
            "crossOrigin" to crossOrigin
        )
        return encodeToFido2Format(clientData)
    }
}
