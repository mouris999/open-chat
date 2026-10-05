package com.openchat.app.domain.usecase.contact

import com.openchat.app.core.Result
import com.openchat.app.domain.model.User
import com.openchat.app.domain.repository.ContactRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveContactsUseCase @Inject constructor(
    private val contactRepository: ContactRepository
) {
    operator fun invoke(): Flow<List<User>> {
        return contactRepository.observeContacts()
    }
}

class ObserveRegisteredContactsUseCase @Inject constructor(
    private val contactRepository: ContactRepository
) {
    operator fun invoke(): Flow<List<User>> {
        return contactRepository.observeRegisteredContacts()
    }
}

class SyncContactsUseCase @Inject constructor(
    private val contactRepository: ContactRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return contactRepository.syncContacts()
    }
}

class FindUserByPhoneUseCase @Inject constructor(
    private val contactRepository: ContactRepository
) {
    suspend operator fun invoke(phoneNumber: String): Result<User?> {
        return contactRepository.findUserByPhoneNumber(phoneNumber)
    }
}

class BlockUserUseCase @Inject constructor(
    private val contactRepository: ContactRepository
) {
    suspend operator fun invoke(userId: String): Result<Unit> {
        return contactRepository.blockUser(userId)
    }
}

class UnblockUserUseCase @Inject constructor(
    private val contactRepository: ContactRepository
) {
    suspend operator fun invoke(userId: String): Result<Unit> {
        return contactRepository.unblockUser(userId)
    }
}
