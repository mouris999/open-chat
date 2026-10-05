package com.openchat.app.domain.usecase.auth

import com.openchat.app.core.Result
import com.openchat.app.domain.repository.AuthRepository
import javax.inject.Inject

class SignInWithTAuthUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(userId: String, email: String, name: String): Result<Unit> {
        return authRepository.signInWithTAuth(userId, email, name)
    }
}

class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return authRepository.signOut()
    }
}

class IsAuthenticatedUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    operator fun invoke(): Boolean {
        return authRepository.isAuthenticated()
    }
}
