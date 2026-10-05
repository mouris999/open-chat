package com.openchat.app.data.local.typeconverter

import androidx.room.TypeConverter
import com.openchat.app.data.local.entity.CallStatus
import com.openchat.app.data.local.entity.CallType
import com.openchat.app.data.local.entity.ChatType
import com.openchat.app.data.local.entity.MediaType
import com.openchat.app.data.local.entity.MemberRole
import com.openchat.app.data.local.entity.MessageStatus
import com.openchat.app.data.local.entity.MessageType
import com.openchat.app.data.local.entity.StoryMediaType
import java.util.Date

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time

    @TypeConverter
    fun fromChatType(value: ChatType): String = value.name

    @TypeConverter
    fun toChatType(value: String): ChatType = ChatType.valueOf(value)

    @TypeConverter
    fun fromMemberRole(value: MemberRole): String = value.name

    @TypeConverter
    fun toMemberRole(value: String): MemberRole = MemberRole.valueOf(value)

    @TypeConverter
    fun fromMessageType(value: MessageType): String = value.name

    @TypeConverter
    fun toMessageType(value: String): MessageType = MessageType.valueOf(value)

    @TypeConverter
    fun fromMediaType(value: MediaType?): String? = value?.name

    @TypeConverter
    fun toMediaType(value: String?): MediaType? = value?.let { MediaType.valueOf(it) }

    @TypeConverter
    fun fromMessageStatus(value: MessageStatus): String = value.name

    @TypeConverter
    fun toMessageStatus(value: String): MessageStatus = MessageStatus.valueOf(value)

    @TypeConverter
    fun fromCallType(value: CallType): String = value.name

    @TypeConverter
    fun toCallType(value: String): CallType = CallType.valueOf(value)

    @TypeConverter
    fun fromCallStatus(value: CallStatus): String = value.name

    @TypeConverter
    fun toCallStatus(value: String): CallStatus = CallStatus.valueOf(value)

    @TypeConverter
    fun fromStoryMediaType(value: StoryMediaType): String = value.name

    @TypeConverter
    fun toStoryMediaType(value: String): StoryMediaType = StoryMediaType.valueOf(value)

    @TypeConverter
    fun fromStringList(value: List<String>?): String? = value?.joinToString(",")

    @TypeConverter
    fun toStringList(value: String?): List<String>? = value?.split(",")?.filter { it.isNotEmpty() }
}
