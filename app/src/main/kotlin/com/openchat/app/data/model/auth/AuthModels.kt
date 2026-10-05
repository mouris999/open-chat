package com.openchat.app.data.model

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String? = null
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class PhoneVerificationRequest(
    @SerializedName("phoneNumber")
    val phoneNumber: String
)

data class PhoneVerificationResponse(
    @SerializedName("verificationId")
    val verificationId: String,
    val message: String
)

data class VerifyPhoneRequest(
    @SerializedName("verificationId")
    val verificationId: String,
    val code: String
)

data class AuthResponse(
    val token: String,
    val user: User
)

data class User(
    val id: String,
    val email: String? = null,
    val phoneNumber: String? = null,
    val name: String
)