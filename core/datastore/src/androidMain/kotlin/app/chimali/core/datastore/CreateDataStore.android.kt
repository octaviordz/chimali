package app.chimali.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import kotlin.coroutines.CoroutineContext
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.mp.KoinPlatformTools

actual fun createDataStore(context: CoroutineContext): DataStore<UserPreferences> {
    val androidContext = KoinPlatformTools.defaultContext().get().get<android.content.Context>()

    return DataStore
        .Builder(
            storage =
                OkioStorage(
                    fileSystem = FileSystem.SYSTEM,
                    serializer = UserPreferencesSerializer,
                ) {
                    androidContext.filesDir
                        .resolve(DATASTORE_FILE_NAME)
                        .absolutePath
                        .toPath()
                },
            context = context,
        ).build()
}
