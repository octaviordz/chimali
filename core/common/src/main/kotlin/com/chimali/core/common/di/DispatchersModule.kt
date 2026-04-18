package com.chimali.core.common.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

/**
 * Koin Annotations module for core:common coroutine dispatchers.
 *
 * Each dispatcher is provided as a `@Single` factory method tagged with `@Named` so
 * injection sites can distinguish them:
 *
 * ```kotlin
 * // In an annotated class:
 * class MyRepo(@Named("IoDispatcher") private val io: CoroutineDispatcher)
 *
 * // In a DSL module (legacy):
 * val d: CoroutineDispatcher by inject(named("IoDispatcher"))
 * ```
 *
 * Qualifier names (unchanged from the DSL era for backward compatibility):
 * - `"DefaultDispatcher"` — CPU-bound work (cryptography, computation)
 * - `"IoDispatcher"`      — I/O-bound work (disk, network)
 * - `"MainDispatcher"`    — UI updates on the Android main thread
 *
 * Usage in [ChimaliApplication]:
 * ```kotlin
 * modules(DispatchersModule().module)
 * ```
 */
@Module
@ComponentScan("com.chimali.core.common")
class DispatchersModule {

    @Single
    @Named("DefaultDispatcher")
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Single
    @Named("IoDispatcher")
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Single
    @Named("MainDispatcher")
    fun mainDispatcher(): CoroutineDispatcher = Dispatchers.Main
}
