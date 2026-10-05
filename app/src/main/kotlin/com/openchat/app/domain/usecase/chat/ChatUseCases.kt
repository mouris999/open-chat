package com.openchat.app.domain.usecase.chat

import com.openchat.app.core.Result
import com.openchat.app.data.model.Chat
import com.openchat.app.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveChatsUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(): Flow<List<Chat>> {
        return chatRepository.observeChats()
    }
}

class GetChatUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(chatId: String): Result<Chat> {
        return chatRepository.getChat(chatId)
    }
}

class CreatePrivateChatUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(userId: String): Result<Chat> {
        return chatRepository.createPrivateChat(userId)
    }
}

class CreateGroupChatUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(title: String, members: List<String>): Result<Chat> {
        return chatRepository.createGroupChat(title, members)
    }
}

class PinChatUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(chatId: String, isPinned: Boolean): Result<Unit> {
        return chatRepository.pinChat(chatId, isPinned)
    }
}

class MuteChatUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(chatId: String, isMuted: Boolean): Result<Unit> {
        return chatRepository.muteChat(chatId, isMuted)
    }
}

class MarkChatAsReadUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(chatId: String): Result<Unit> {
        return chatRepository.markChatAsRead(chatId)
    }
}

class DeleteChatUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(chatId: String): Result<Unit> {
        return chatRepository.deleteChat(chatId)
    }
}

class LeaveChatUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(chatId: String): Result<Unit> {
        return chatRepository.leaveChat(chatId)
    }
}
