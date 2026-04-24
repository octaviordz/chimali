package com.chimali.core.common.di

import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Koin Annotations module for coroutine dispatchers.
 * Uses platform-specific provider functions to bridge implementations.
 */
@Module
class DispatchersModule {
    @Single
    @Named(DispatcherQualifiers.DISPATCHER_DEFAULT)
    fun default(): CoroutineDispatcher = provideDefaultDispatcher()

    @Single
    @Named(DispatcherQualifiers.DISPATCHER_IO)
    fun io(): CoroutineDispatcher = provideIoDispatcher()

    @Single
    @Named(DispatcherQualifiers.DISPATCHER_MAIN)
    fun main(): CoroutineDispatcher = provideMainDispatcher()
}

/**
 * Platform-specific dispatcher providers.
 */
expect fun provideDefaultDispatcher(): CoroutineDispatcher

expect fun provideIoDispatcher(): CoroutineDispatcher

expect fun provideMainDispatcher(): CoroutineDispatcher
