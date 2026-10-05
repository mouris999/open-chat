package com.openchat.app.data.repository

import android.content.SharedPreferences
import com.openchat.app.domain.repository.ChatLockRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatLockRepositoryImpl @Inject constructor(
    @com.openchat.app.di.ChatLockPrefs private val prefs: SharedPreferences
) : ChatLockRepository {

    private val lockEnabledKey = "chat_lock_enabled"
    private val lockedChatsKey = "locked_chats"

    override fun isChatLocked(chatId: String): Boolean {
        return getLockedChatIds().contains(chatId)
    }

    override fun getLockedChatIds(): Set<String> {
        return prefs.getStringSet(lockedChatsKey, emptySet()) ?: emptySet()
    }

    override fun lockChat(chatId: String) {
        val current = getLockedChatIds().toMutableSet()
        current.add(chatId)
        prefs.edit().putStringSet(lockedChatsKey, current).apply()
    }

    override fun unlockChat(chatId: String) {
        val current = getLockedChatIds().toMutableSet()
        current.remove(chatId)
        prefs.edit().putStringSet(lockedChatsKey, current).apply()
    }

    override fun isChatLockEnabled(): Boolean {
        return prefs.getBoolean(lockEnabledKey, false)
    }

    override fun setChatLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(lockEnabledKey, enabled).apply()
    }

    override fun migrateIfNeeded() {
        // Migration from old format if needed
    }

    override fun getAllLockedChatIds(): Set<String> {
        return getLockedChatIds()
    }
}
