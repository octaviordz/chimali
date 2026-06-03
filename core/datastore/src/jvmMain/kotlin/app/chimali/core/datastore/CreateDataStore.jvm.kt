package app.chimali.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import kotlin.coroutines.CoroutineContext
import okio.FileSystem
import okio.Path.Companion.toPath

actual fun createDataStore(context: CoroutineContext): DataStore<UserPreferences> {
    return DataStore
        .Builder(
            storage =
                OkioStorage(
                    fileSystem = FileSystem.SYSTEM,
                    serializer = UserPreferencesSerializer,
                ) { DATASTORE_FILE_NAME.toPath() },
            context = context,
        ).build()
}
