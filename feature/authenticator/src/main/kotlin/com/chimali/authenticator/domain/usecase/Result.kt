package com.chimali.authenticator.domain.usecase

import com.chimali.authenticator.domain.error.AuthenticatorError

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val error: AuthenticatorError) : Result<Nothing>()
    object Loading : Result<Nothing>()
    
    val isSuccess: Boolean
        get() = this is Success
    
    val isError: Boolean
        get() = this is Error
    
    val isLoading: Boolean
        get() = this is Loading
    
    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }
    
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw error
        is Loading -> throw IllegalStateException("Cannot get value from Loading result")
    }
    
    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }
    
    inline fun onError(action: (AuthenticatorError) -> Unit): Result<T> {
        if (this is Error) action(error)
        return this
    }
    
    inline fun onLoading(action: () -> Unit): Result<T> {
        if (this is Loading) action()
        return this
    }
    
    fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Loading -> this
    }
}

suspend inline fun <T> safeCall(crossinline block: suspend () -> T): Result<T> {
    return try {
        Result.Success(block())
    } catch (e: AuthenticatorError) {
        Result.Error(e)
    } catch (e: Exception) {
        Result.Error(AuthenticatorError.UnknownError(e))
    }
}
