package com.chimali.core.common.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Provides [CoroutineDispatcher] instances for the application.
 *
 * Inject using the matching qualifier annotation:
 * - [@DefaultDispatcher] — CPU-bound work (cryptography, computation)
 * - [@IoDispatcher]      — I/O-bound work (disk, network)
 * - [@MainDispatcher]    — UI updates on the Android main thread
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
}
