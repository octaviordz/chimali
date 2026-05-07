package com.chimali.core.common

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

/**
 * iOS implementation of build variant detection.
 */
@OptIn(ExperimentalNativeApi::class)
actual val isDebug: Boolean = Platform.isDebugBinary
