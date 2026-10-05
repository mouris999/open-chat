package com.openchat.app.domain.repository

interface ChatLockRepository {
    fun isChatLocked(chatId: String): Boolean
    fun getLockedChatIds(): Set<String>
    fun lockChat(chatId: String)
    fun unlockChat(chatId: String)
    fun isChatLockEnabled(): Boolean
    fun setChatLockEnabled(enabled: Boolean)
    fun migrateIfNeeded()
    fun getAllLockedChatIds(): Set<String>
}
