package com.openchat.app.di

import android.content.Context
import androidx.room.Room
import com.openchat.app.core.Constants
import com.openchat.app.data.local.OpenChatDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): OpenChatDatabase {
        return Room.databaseBuilder(
            context,
            OpenChatDatabase::class.java,
            Constants.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }
    
    @Provides
    @Singleton
    fun provideUserDao(database: OpenChatDatabase) = database.userDao()
    
    @Provides
    @Singleton
    fun provideChatDao(database: OpenChatDatabase) = database.chatDao()
    
    @Provides
    @Singleton
    fun provideMessageDao(database: OpenChatDatabase) = database.messageDao()
    
    @Provides
    @Singleton
    fun provideContactDao(database: OpenChatDatabase) = database.contactDao()
    
    @Provides
    @Singleton
    fun provideCallDao(database: OpenChatDatabase) = database.callDao()
    
    @Provides
    @Singleton
    fun provideStoryDao(database: OpenChatDatabase) = database.storyDao()
}
