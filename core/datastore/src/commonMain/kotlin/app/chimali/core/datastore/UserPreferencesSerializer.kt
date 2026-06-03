package app.chimali.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.okio.OkioSerializer
import app.chimali.core.datastore.UserPreferences
import okio.BufferedSink
import okio.BufferedSource

object UserPreferencesSerializer : OkioSerializer<UserPreferences> {
    override val defaultValue: UserPreferences = UserPreferences()

    override suspend fun readFrom(source: BufferedSource): UserPreferences = UserPreferences.ADAPTER.decode(source)

    override suspend fun writeTo(
        t: UserPreferences,
        sink: BufferedSink,
    ) {
        UserPreferences.ADAPTER.encode(sink, t)
    }
}
