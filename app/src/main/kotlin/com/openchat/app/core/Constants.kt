package com.openchat.app.core

object Constants {

    const val DATABASE_NAME = "openchat_database"
    const val PREFERENCES_NAME = "openchat_preferences"
    const val KEYSTORE_ALIAS = "openchat_master_key"
    
    const val PAGE_SIZE = 30
    const val MAX_MESSAGE_LENGTH = 4096
    const val MAX_GROUP_MEMBERS = 500
    
    const val TYPING_DEBOUNCE_MS = 300L
    const val TYPING_TIMEOUT_MS = 5000L
    
    const val MESSAGE_EDIT_TIME_LIMIT_MINUTES = 48 * 60
    const val MESSAGE_DELETE_TIME_LIMIT_MINUTES = 48 * 60
    
    const val STORY_DURATION_HOURS = 24
    const val MAX_STORY_DURATION_SECONDS = 60
    
    const val CALL_RING_TIMEOUT_SECONDS = 60
    const val MAX_CALL_PARTICIPANTS = 8
    
    const val E2EE_DEVICE_ID = 1
    const val E2EE_REGISTRATION_ID = 1
    
    const val WEBRTC_ICE_SERVERS = "stun:stun.l.google.com:19302"
    
    const val FILE_MAX_SIZE_MB = 100
    const val VIDEO_MAX_SIZE_MB = 50
    const val IMAGE_MAX_SIZE_MB = 10
    
    const val ANIMATION_DURATION_MS = 300
    const val SPLASH_DURATION_MS = 2000L
}

object DeepLinks {
    const val PROFILE = "openchat://profile/{userId}"
    const val CHAT = "openchat://chat/{chatId}"
    const val INVITE = "openchat://invite/{code}"
}

object Collections {
    const val USERS = "users"
    const val CHATS = "chats"
    const val MESSAGES = "messages"
    const val GROUPS = "groups"
    const val CHANNELS = "channels"
    const val STORIES = "stories"
    const val CALLS = "calls"
    const val CONTACTS = "contacts"
    const val SETTINGS = "settings"
}

object StoragePaths {
    const val PROFILE_PHOTOS = "profile_photos"
    const val MESSAGE_MEDIA = "message_media"
    const val VOICE_MESSAGES = "voice_messages"
    const val STORIES = "stories"
    const val DOCUMENTS = "documents"
    const val STICKERS = "stickers"
}
