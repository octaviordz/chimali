package com.chimali.core.common.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
actual fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
actual fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
