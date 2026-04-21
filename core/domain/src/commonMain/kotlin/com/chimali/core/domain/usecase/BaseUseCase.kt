package com.chimali.core.domain.usecase

/**
 * Base abstract class for Use Cases that take parameters and return a Result.
 */
abstract class BaseUseCase<in P, out R> : UseCase {
    abstract suspend operator fun invoke(parameters: P): Result<R>
}

/**
 * Base abstract class for Use Cases that take no parameters and return a Result.
 */
abstract class BaseUseCaseNoParams<out R> : UseCase {
    abstract suspend operator fun invoke(): Result<R>
}

/**
 * Base abstract class for Use Cases that take parameters but return no specific result (Unit).
 */
abstract class BaseUseCaseIn<in P> : UseCase {
    abstract suspend operator fun invoke(parameters: P): Result<Unit>
}
