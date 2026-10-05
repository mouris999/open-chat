package com.openchat.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.openchat.app.data.local.dao.CallDao
import com.openchat.app.data.local.dao.ChatDao
import com.openchat.app.data.local.dao.ContactDao
import com.openchat.app.data.local.dao.MessageDao
import com.openchat.app.data.local.dao.StoryDao
import com.openchat.app.data.local.dao.UserDao
import com.openchat.app.data.local.entity.CallEntity
import com.openchat.app.data.local.entity.ChatEntity
import com.openchat.app.data.local.entity.ChatMemberEntity
import com.openchat.app.data.local.entity.ContactEntity
import com.openchat.app.data.local.entity.MessageEntity
import com.openchat.app.data.local.entity.MessageReactionEntity
import com.openchat.app.data.local.entity.StoryEntity
import com.openchat.app.data.local.entity.StoryViewEntity
import com.openchat.app.data.local.entity.UserEntity
import com.openchat.app.data.local.typeconverter.Converters

@Database(
    entities = [
        UserEntity::class,
        ChatEntity::class,
        ChatMemberEntity::class,
        MessageEntity::class,
        MessageReactionEntity::class,
        ContactEntity::class,
        CallEntity::class,
        StoryEntity::class,
        StoryViewEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class OpenChatDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun contactDao(): ContactDao
    abstract fun callDao(): CallDao
    abstract fun storyDao(): StoryDao
}
