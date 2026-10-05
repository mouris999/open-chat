package com.openchat.app.domain.usecase.message

import android.net.Uri
import com.openchat.app.core.Result
import com.openchat.app.domain.model.MediaType
import com.openchat.app.domain.model.Message
import com.openchat.app.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveMessagesUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    operator fun invoke(chatId: String): Flow<List<Message>> {
        return messageRepository.observeMessages(chatId)
    }
}

class GetMessagesPagingUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    operator fun invoke(chatId: String) = messageRepository.getMessagesPaging(chatId)
}

class SendTextMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(chatId: String, content: String, replyToMessageId: String? = null): Result<Message> {
        return messageRepository.sendMessage(chatId, content, null, null, replyToMessageId)
    }
}

class SendImageMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(chatId: String, imageUri: Uri, caption: String? = null): Result<Message> {
        return messageRepository.sendImageMessage(chatId, imageUri, caption)
    }
}

class SendVideoMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(chatId: String, videoUri: Uri, caption: String? = null): Result<Message> {
        return messageRepository.sendVideoMessage(chatId, videoUri, caption)
    }
}

class SendVoiceMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(chatId: String, audioUri: Uri, duration: Int): Result<Message> {
        return messageRepository.sendVoiceMessage(chatId, audioUri, duration)
    }
}

class SendLocationMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(chatId: String, latitude: Double, longitude: Double): Result<Message> {
        return messageRepository.sendLocationMessage(chatId, latitude, longitude)
    }
}

class EditMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(messageId: String, newContent: String): Result<Unit> {
        return messageRepository.editMessage(messageId, newContent)
    }
}

class DeleteMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(messageId: String, deleteForEveryone: Boolean = false): Result<Unit> {
        return messageRepository.deleteMessage(messageId, deleteForEveryone)
    }
}

class ForwardMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(messageId: String, toChatId: String): Result<Message> {
        return messageRepository.forwardMessage(messageId, toChatId)
    }
}

class AddReactionUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(messageId: String, emoji: String): Result<Unit> {
        return messageRepository.addReaction(messageId, emoji)
    }
}

class RemoveReactionUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(messageId: String, emoji: String): Result<Unit> {
        return messageRepository.removeReaction(messageId, emoji)
    }
}

class SearchMessagesUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(chatId: String, query: String): List<Message> {
        return messageRepository.searchMessages(chatId, query)
    }
}
