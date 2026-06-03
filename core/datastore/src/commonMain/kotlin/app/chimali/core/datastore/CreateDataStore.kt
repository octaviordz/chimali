package app.chimali.core.datastore

import androidx.datastore.core.DataStore
import kotlin.coroutines.CoroutineContext

// Pass the execution context to the builder
expect fun createDataStore(context: CoroutineContext): DataStore<UserPreferences>

internal const val DATASTORE_FILE_NAME = "user_prefs.preferences_pb"
