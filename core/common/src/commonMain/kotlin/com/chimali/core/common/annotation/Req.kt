package com.chimali.core.common.annotation

/**
 * Links implementation and verification code to one or more stable BRD/TRD requirement IDs.
 * IDs are documentation traceability labels and are not feature-local FR numbers.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Req(
    vararg val value: String,
)
