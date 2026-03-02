package com.chimali.fido2.data.crypto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
        return json.encodeToString(data).toByteArray()
    }
    
    fun decodeFromFido2Format(data: ByteArray): Map<String, Any> {
        return try {
            val jsonString = String(data)
            val jsonElement = json.parseToJsonElement(jsonString)
            jsonElement.jsonObject.mapValues { entry ->
                entry.value.jsonPrimitive.content
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
