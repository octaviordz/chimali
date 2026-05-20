package com.chimali.core.common.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.okio.OkioSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import okio.BufferedSink
import okio.BufferedSource

@OptIn(ExperimentalSerializationApi::class)
object UserPreferencesSerializer : OkioSerializer<UserPreferences> {
    override val defaultValue: UserPreferences = UserPreferences()

    override suspend fun readFrom(source: BufferedSource): UserPreferences {
        try {
            val bytes = source.readByteArray()
            if (bytes.isEmpty()) return defaultValue
            return ProtoBuf.decodeFromByteArray(UserPreferences.serializer(), bytes)
        } catch (exception: kotlinx.serialization.SerializationException) {
            throw CorruptionException("Cannot read proto.", exception)
        } catch (exception: okio.IOException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(
        t: UserPreferences,
        sink: BufferedSink,
    ) {
        val bytes = ProtoBuf.encodeToByteArray(UserPreferences.serializer(), t)
        sink.write(bytes)
    }
}
