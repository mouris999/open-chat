package com.openchat.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    // Deliberately NOT unique. Phone-less accounts were stored as "", so the second
    // one to be cached hit a UNIQUE violation and insertUser threw
    // SQLiteConstraintException - permanently failing getUser() for those users.
    // Identity here is the Firebase uid (the primary key); the index only needs to
    // make phone lookups fast.
    indices = [Index(value = ["phoneNumber"])]
)
data class UserEntity(
    @PrimaryKey
    val id: String,
    val phoneNumber: String?,
    val displayName: String,
    val username: String? = null,
    val bio: String? = null,
    val photoUrl: String? = null,
    val isOnline: Boolean = false,
    val lastSeen: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
