package com.chimali.core.domain.eventsourcing.vault

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Retains sensitive text as an erasable array in aggregate objects while preserving the v1 JSON
 * string representation. The serialization framework's String is limited to this call boundary.
 */
object SensitiveCharArraySerializer : KSerializer<CharArray> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("SensitiveCharArray", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: CharArray,
    ) {
        encoder.encodeString(value.concatToString())
    }

    override fun deserialize(decoder: Decoder): CharArray = decoder.decodeString().toCharArray()
}
