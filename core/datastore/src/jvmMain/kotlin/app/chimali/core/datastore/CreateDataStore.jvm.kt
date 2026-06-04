package app.chimali.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import java.io.File
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
                ) {
                    val userHome = System.getProperty("user.home")
                    val appDataDir = File(userHome, ".chimali")

                    if (!appDataDir.exists()) {
                        appDataDir.mkdirs()
                    }

                    File(appDataDir, DATASTORE_FILE_NAME).absolutePath.toPath()
                },
            context = context,
        ).build()
}
