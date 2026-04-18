package com.chimali.core.common.di

/**
 * Koin `@Named` qualifier string constants for coroutine dispatchers.
 *
 * Used with Koin Annotations `@Named` or Koin DSL `named()` to select a specific
 * [kotlinx.coroutines.CoroutineDispatcher] from the DI graph.
 *
 * These replace the Hilt `@Qualifier` annotation classes. Provided by
 * [DispatchersModule] (KSP-generated from `@Single @Named(...)` factory methods).
 *
 * Injection examples:
 * ```kotlin
 * // Koin Annotations (constructor injection):
 * class MyService(
 *     @Named(DISPATCHER_DEFAULT) private val dispatcher: CoroutineDispatcher
 * )
 *
 * // Koin DSL (get with qualifier):
 * val dispatcher: CoroutineDispatcher = get(named(DISPATCHER_IO))
 * ```
 */
object DispatcherQualifiers {
    /** CPU-bound work (cryptography, computation). Maps to [kotlinx.coroutines.Dispatchers.Default]. */
    const val DISPATCHER_DEFAULT = "DefaultDispatcher"

    /** I/O-bound work (disk, network). Maps to [kotlinx.coroutines.Dispatchers.IO]. */
    const val DISPATCHER_IO = "IoDispatcher"

    /** UI-thread work. Maps to [kotlinx.coroutines.Dispatchers.Main]. */
    const val DISPATCHER_MAIN = "MainDispatcher"
}
