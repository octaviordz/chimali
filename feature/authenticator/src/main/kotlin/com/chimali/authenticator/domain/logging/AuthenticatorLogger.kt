package com.chimali.authenticator.domain.logging

import javax.inject.Inject
import javax.inject.Singleton

interface AuthenticatorLogger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
    fun logSecurityEvent(event: String, details: Map<String, Any> = emptyMap())
    fun logUserAction(action: String, details: Map<String, Any> = emptyMap())
    fun logError(error: Throwable, context: Map<String, Any> = emptyMap())
}

@Singleton
class AndroidAuthenticatorLogger @Inject constructor() : AuthenticatorLogger {
    
    override fun d(tag: String, message: String) {
        if (android.util.Log.isLoggable(tag, android.util.Log.DEBUG)) {
            android.util.Log.d(tag, message)
        }
    }
    
    override fun i(tag: String, message: String) {
        if (android.util.Log.isLoggable(tag, android.util.Log.INFO)) {
            android.util.Log.i(tag, message)
        }
    }
    
    override fun w(tag: String, message: String) {
        if (android.util.Log.isLoggable(tag, android.util.Log.WARN)) {
            android.util.Log.w(tag, message)
        }
    }
    
    override fun e(tag: String, message: String, throwable: Throwable?) {
        if (android.util.Log.isLoggable(tag, android.util.Log.ERROR)) {
            if (throwable != null) {
                android.util.Log.e(tag, message, throwable)
            } else {
                android.util.Log.e(tag, message)
            }
        }
    }
    
    override fun logSecurityEvent(event: String, details: Map<String, Any>) {
        val detailsString = details.entries.joinToString(", ") { "${it.key}=${it.value}" }
        i("SECURITY", "$event | $detailsString")
    }
    
    override fun logUserAction(action: String, details: Map<String, Any>) {
        val detailsString = details.entries.joinToString(", ") { "${it.key}=${it.value}" }
        i("USER_ACTION", "$action | $detailsString")
    }
    
    override fun logError(error: Throwable, context: Map<String, Any>) {
        val contextString = context.entries.joinToString(", ") { "${it.key}=${it.value}" }
        e("ERROR", "Error occurred | $contextString", error)
    }
}
