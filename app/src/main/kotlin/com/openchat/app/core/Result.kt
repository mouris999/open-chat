package com.openchat.app.core

import kotlinx.coroutines.CancellationException

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable, val message: String? = null) : Result<Nothing>()
    data object Loading : Result<Nothing>()

    fun isSuccess(): Boolean = this is Success
    fun isError(): Boolean = this is Error
    fun isLoading(): Boolean = this is Loading

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    fun exceptionOrNull(): Throwable? = when (this) {
        is Error -> exception
        else -> null
    }

    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> Error(exception, message)
        is Loading -> Loading
    }

    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(data)
        is Error -> Error(exception, message)
        is Loading -> Loading
    }

    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onError(action: (Throwable, String?) -> Unit): Result<T> {
        if (this is Error) action(exception, message)
        return this
    }

    inline fun onLoading(action: () -> Unit): Result<T> {
        if (this is Loading) action()
        return this
    }
}

fun <T> Result<T>.requireValue(): T {
    return when (this) {
        is Result.Success -> data
        is Result.Error -> throw IllegalStateException("Result is Error: ${exception.message}", exception)
        is Result.Loading -> throw IllegalStateException("Result is Loading")
    }
}

suspend fun <T> safeApiCall(
    call: suspend () -> T
): Result<T> = try {
    Result.Success(call())
} catch (e: CancellationException) {
    // CancellationException extends IllegalStateException -> Exception, so a bare
    // catch would convert coroutine cancellation into a Result.Error. That breaks
    // structured concurrency: cancelling a viewModelScope job would never actually
    // stop the work, and the cancellation would surface in the UI as a fake error.
    throw e
} catch (e: Exception) {
    Result.Error(e, e.message)
}
