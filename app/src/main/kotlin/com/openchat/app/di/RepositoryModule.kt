package com.openchat.app.di

import com.openchat.app.data.repository.AppLockRepositoryImpl
import com.openchat.app.data.repository.AuthRepositoryImpl
import com.openchat.app.data.repository.CallRepositoryImpl
import com.openchat.app.data.repository.ChatLockRepositoryImpl
import com.openchat.app.data.repository.ChatRepositoryImpl
import com.openchat.app.data.repository.ContactRepositoryImpl
import com.openchat.app.data.repository.MessageRepositoryImpl
import com.openchat.app.data.repository.StoryRepositoryImpl
import com.openchat.app.data.repository.UserRepositoryImpl
import com.openchat.app.domain.repository.AppLockRepository
import com.openchat.app.domain.repository.AuthRepository
import com.openchat.app.domain.repository.CallRepository
import com.openchat.app.domain.repository.ChatLockRepository
import com.openchat.app.domain.repository.ChatRepository
import com.openchat.app.domain.repository.ContactRepository
import com.openchat.app.domain.repository.MessageRepository
import com.openchat.app.domain.repository.StoryRepository
import com.openchat.app.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    abstract fun bindChatRepository(
        chatRepositoryImpl: ChatRepositoryImpl
    ): ChatRepository

    @Binds
    abstract fun bindMessageRepository(
        messageRepositoryImpl: MessageRepositoryImpl
    ): MessageRepository

    @Binds
    abstract fun bindContactRepository(
        contactRepositoryImpl: ContactRepositoryImpl
    ): ContactRepository

    @Binds
    abstract fun bindUserRepository(
        userRepositoryImpl: UserRepositoryImpl
    ): UserRepository

    @Binds
    abstract fun bindCallRepository(
        callRepositoryImpl: CallRepositoryImpl
    ): CallRepository

    @Binds
    abstract fun bindStoryRepository(
        storyRepositoryImpl: StoryRepositoryImpl
    ): StoryRepository

    @Binds
    abstract fun bindChatLockRepository(
        chatLockRepositoryImpl: ChatLockRepositoryImpl
    ): ChatLockRepository

    @Binds
    abstract fun bindAppLockRepository(
        appLockRepositoryImpl: AppLockRepositoryImpl
    ): AppLockRepository
}
