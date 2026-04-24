package com.chimali.core.domain.time

import org.koin.core.annotation.Single
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Single
class TimeProvider {
    fun now(): Instant = Clock.System.now()

    fun epochMillis(): Long = Clock.System.now().toEpochMilliseconds()
}
