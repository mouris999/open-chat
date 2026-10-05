package com.openchat.app.data.api

import com.openchat.app.data.model.AuthResponse
import com.openchat.app.data.model.PhoneVerificationRequest
import com.openchat.app.data.model.PhoneVerificationResponse
import com.openchat.app.data.model.RegisterRequest
import com.openchat.app.data.model.LoginRequest
import com.openchat.app.data.model.User
import com.openchat.app.data.model.VerifyPhoneRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

interface AuthApiService {

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("auth/send-phone-verification")
    suspend fun sendPhoneVerification(@Body request: PhoneVerificationRequest): PhoneVerificationResponse

    @POST("auth/verify-phone")
    suspend fun verifyPhone(@Body request: VerifyPhoneRequest): AuthResponse

    @GET("auth/profile")
    suspend fun getProfile(): User

    @PUT("auth/profile")
    suspend fun updateProfile(@Body request: Map<String, String>): User
}