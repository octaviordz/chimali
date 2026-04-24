package com.chimali.core.common.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

actual fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.Default // Fallback for iOS

actual fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
