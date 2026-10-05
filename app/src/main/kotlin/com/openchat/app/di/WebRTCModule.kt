package com.openchat.app.di

import android.app.Application
import com.openchat.app.webrtc.WebRTCManager
import com.google.firebase.auth.FirebaseAuth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WebRTCModule {
    
    @Provides
    @Singleton
    fun provideWebRTCManager(
        application: Application,
        firebaseAuth: FirebaseAuth
    ): WebRTCManager {
        return WebRTCManager(application, firebaseAuth)
    }
}
