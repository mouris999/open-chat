package com.openchat.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "contacts",
    indices = [
        Index(value = ["phoneNumber"], unique = true),
        Index(value = ["userId"])
    ]
)
data class ContactEntity(
    @PrimaryKey
    val id: String,
    val userId: String? = null,
    val phoneNumber: String,
    val displayName: String,
    val photoUri: String? = null,
    val isRegistered: Boolean = false,
    val isBlocked: Boolean = false,
    val syncedAt: Long = System.currentTimeMillis()
)
