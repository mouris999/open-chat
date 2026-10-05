package com.openchat.app.domain.model

data class Call(
    val id: String,
    val callerId: String,
    val caller: User? = null,
    val receiverId: String,
    val receiver: User? = null,
    val type: CallType,
    val status: CallStatus,
    val isVideo: Boolean = false,
    val isEncrypted: Boolean = true,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val duration: Int? = null,
    val isMissed: Boolean = false
)

enum class CallType {
    OUTGOING, INCOMING
}

enum class CallStatus {
    RINGING, CONNECTING, CONNECTED, ENDED, MISSED, REJECTED, BUSY, FAILED
}
