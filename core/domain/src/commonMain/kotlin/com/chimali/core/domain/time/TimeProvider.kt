package com.chimali.core.domain.time

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.koin.core.annotation.Single

@Single
class TimeProvider {
    fun now(): Instant = Clock.System.now()

    fun epochMillis(): Long = Clock.System.now().toEpochMilliseconds()
}
