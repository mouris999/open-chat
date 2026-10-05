package com.openchat.app.di

import android.app.Application
import com.cloudinary.Cloudinary
import com.openchat.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CloudinaryModule {

    private const val CLOUD_NAME = "dwyvdpbev"
    private const val API_KEY = "621159536568186"

    @Provides
    @Singleton
    fun provideCloudinary(): Cloudinary {
        return Cloudinary(
            mapOf(
                "cloud_name" to CLOUD_NAME,
                "api_key" to API_KEY,
                "api_secret" to BuildConfig.CLOUDINARY_API_SECRET,
                "secure" to true
            )
        )
    }
}
