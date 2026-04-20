package com.chimali.core.clipboard.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

/**
 * Koin Annotations module for clipboard management.
 * Uses @ComponentScan to discover platform-specific implementations.
 */
@Module
@ComponentScan("com.chimali.core.clipboard")
class ClipboardModule
