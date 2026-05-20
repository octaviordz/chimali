package com.chimali.core.common.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioStorage
import okio.FileSystem
import okio.Path.Companion.toPath

fun createUserPreferencesDataStore(producePath: () -> String): DataStore<UserPreferences> =
    DataStoreFactory.create(
        storage =
            OkioStorage(
                fileSystem = FileSystem.SYSTEM,
                serializer = UserPreferencesSerializer,
                producePath = { producePath().toPath() },
            ),
    )
