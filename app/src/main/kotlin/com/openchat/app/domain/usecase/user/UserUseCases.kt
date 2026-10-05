package com.openchat.app.domain.usecase.user

import android.net.Uri
import com.openchat.app.core.Result
import com.openchat.app.domain.model.User
import com.openchat.app.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveCurrentUserUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    operator fun invoke(): Flow<User?> {
        return userRepository.observeCurrentUser()
    }
}

class GetCurrentUserUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(): User? {
        return userRepository.getCurrentUser()
    }
}

class GetUserUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String): Result<User> {
        return userRepository.getUser(userId)
    }
}

class UpdateProfileUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(displayName: String? = null, bio: String? = null, photoUri: Uri? = null): Result<Unit> {
        return userRepository.updateProfile(displayName, bio, photoUri)
    }
}

class UpdateUsernameUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(username: String): Result<Unit> {
        return userRepository.updateUsername(username)
    }
}

class SearchUsersUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(query: String): Result<List<User>> {
        return userRepository.searchUsers(query)
    }
}
