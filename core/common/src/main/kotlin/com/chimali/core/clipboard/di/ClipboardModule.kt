package com.chimali.core.clipboard.di

import com.chimali.core.clipboard.AndroidClipboardManagerService
import com.chimali.core.clipboard.ClipboardManagerService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ClipboardModule {

    @Binds
    abstract fun bindClipboardManagerService(
        impl: AndroidClipboardManagerService
    ): ClipboardManagerService
}
