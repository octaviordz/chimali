package com.chimali.core.domain.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module
@ComponentScan("com.chimali.core.domain")
class DomainModule {
    // Note: Dependencies are auto-wired via @Factory and @Single annotations on the actual classes.
}
