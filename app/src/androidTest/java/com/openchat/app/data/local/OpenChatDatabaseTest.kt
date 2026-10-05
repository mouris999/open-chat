package com.openchat.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openchat.app.data.local.entity.ChatEntity
import com.openchat.app.data.local.entity.ChatType
import com.openchat.app.data.local.entity.UserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class OpenChatDatabaseTest {

    private lateinit var db: OpenChatDatabase
    private lateinit var userDao: UserDao
    private lateinit var chatDao: ChatDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, OpenChatDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        userDao = db.userDao()
        chatDao = db.chatDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndRetrieveUser() = runBlocking {
        val user = UserEntity(
            id = "user1",
            phoneNumber = "+1234567890",
            displayName = "Test User"
        )
        userDao.insertUser(user)

        val retrieved = userDao.getUserById("user1")
        assertNotNull(retrieved)
        assertEquals(user.displayName, retrieved?.displayName)
    }

    @Test
    fun observeUsersEmitsUpdates() = runBlocking {
        val user = UserEntity(
            id = "user1",
            phoneNumber = "+1234567890",
            displayName = "Test User"
        )
        userDao.insertUser(user)

        val observed = userDao.observeAllUsers().first()
        assertEquals(1, observed.size)
        assertEquals(user.displayName, observed[0].displayName)
    }

    @Test
    fun insertAndRetrieveChat() = runBlocking {
        val chat = ChatEntity(
            id = "chat1",
            type = ChatType.PRIVATE,
            title = "Test Chat"
        )
        chatDao.insertChat(chat)

        val retrieved = chatDao.getChatById("chat1")
        assertNotNull(retrieved)
        assertEquals(chat.title, retrieved?.chat?.title)
    }
}
