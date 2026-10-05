package com.openchat.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "calls",
    indices = [
        Index(value = ["callerId"]),
        Index(value = ["receiverId"]),
        Index(value = ["startTime"])
    ]
)
data class CallEntity(
    @PrimaryKey
    val id: String,
    val callerId: String,
    val receiverId: String,
    val type: CallType,
    val status: CallStatus,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val duration: Int? = null,
    val isMissed: Boolean = false,
    val isVideo: Boolean = false,
    val isEncrypted: Boolean = true
)

enum class CallType {
    OUTGOING, INCOMING
}

enum class CallStatus {
    RINGING, CONNECTED, ENDED, MISSED, REJECTED, BUSY, FAILED
}
