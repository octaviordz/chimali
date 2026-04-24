package com.chimali.core.common

import kotlin.native.Platform

/**
 * iOS implementation of build variant detection.
 */
actual val isDebug: Boolean = Platform.isDebugBinary
