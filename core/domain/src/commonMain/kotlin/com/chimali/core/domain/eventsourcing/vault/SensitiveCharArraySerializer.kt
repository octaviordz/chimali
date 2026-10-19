package com.chimali.core.domain.eventsourcing.vault

import com.chimali.core.common.annotation.Req
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Compatibility serializer for mutable text fields that must retain the v1 JSON string shape.
 * Encoding and decoding still materialize immutable Strings through the generic serialization API;
 * this serializer is not an erasure boundary for sensitive production payloads. The passkey event
 * and snapshot repositories use their mutable-field JSON codec instead.
 */
@Req("NFR-SEC-041", "TR-AUTH-031")
object SensitiveCharArraySerializer : KSerializer<CharArray> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("SensitiveCharArray", PrimitiveKind.STRING)

    /** Encodes mutable text using the historical JSON string scalar shape. */
    @Req("FR-003", "FR-013", "NFR-SEC-041", "TR-AUTH-031")
    override fun serialize(
        encoder: Encoder,
        value: CharArray,
    ) {
        encoder.encodeString(value.concatToString())
    }

    /** Decodes the historical JSON string scalar into a caller-owned character array. */
    @Req("FR-003", "FR-013", "NFR-SEC-041", "TR-AUTH-031")
    override fun deserialize(decoder: Decoder): CharArray = decoder.decodeString().toCharArray()
}
