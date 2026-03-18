package com.chimali.core.common.di

import javax.inject.Qualifier

/**
 * Hilt qualifier for injecting [kotlinx.coroutines.Dispatchers.Default].
 *
 * Use this to offload CPU-bound work (cryptography, heavy math) off the
 * Main thread, satisfying Constitution §IV performance targets.
 *
 * Usage:
 * ```kotlin
 * class MyService @Inject constructor(
 *     @DefaultDispatcher private val dispatcher: CoroutineDispatcher
 * )
 * ```
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/**
 * Hilt qualifier for injecting [kotlinx.coroutines.Dispatchers.IO].
 * Use for I/O-bound operations (disk, network).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * Hilt qualifier for injecting [kotlinx.coroutines.Dispatchers.Main].
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher
