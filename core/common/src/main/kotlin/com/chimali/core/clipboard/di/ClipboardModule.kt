package com.chimali.core.clipboard.di

import com.chimali.core.clipboard.AndroidClipboardManagerService
import com.chimali.core.clipboard.ClipboardManagerService
import org.koin.dsl.module

val clipboardModule = module {
    single<ClipboardManagerService> { AndroidClipboardManagerService(get()) }
}
