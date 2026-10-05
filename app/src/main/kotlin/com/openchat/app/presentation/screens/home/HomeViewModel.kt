package com.openchat.app.presentation.screens.home

import com.openchat.app.core.BaseViewModel
import com.openchat.app.core.UiEffect
import com.openchat.app.core.UiEvent
import com.openchat.app.core.UiState
import com.openchat.app.data.model.Chat
import android.app.Application
import com.openchat.app.domain.repository.AppLockRepository
import com.openchat.app.domain.repository.AuthMode
import com.openchat.app.domain.repository.AuthRepository
import com.openchat.app.domain.repository.ChatLockRepository
import com.openchat.app.domain.usecase.chat.ObserveChatsUseCase
import com.openchat.app.data.repository.MessagingRepository
import com.openchat.app.domain.repository.UserRepository
import com.openchat.app.service.fcm.MessageNotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.openchat.app.webrtc.WebRTCManager
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.first
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val chats: List<Chat> = emptyList(),
    val pinnedChatIds: List<String> = emptyList(),
    val lockedChatIds: Set<String> = emptySet(),
    /** Mirrors ChatLockRepository.isChatLockEnabled(). */
    val isChatLockEnabled: Boolean = false,
    val isPrivateMode: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
) : UiState {
    /**
     * Pinned first, then most recent. Locked chats are hidden ONLY while the chat
     * lock feature is on - previously the filter ran unconditionally, so after
     * disabling the lock the previously-hidden chats stayed invisible forever.
     */
    val sortedChats: List<Chat>
        get() {
            val visible = if (isPrivateMode) {
                chats
            } else {
                chats.filterNot { isChatLockEnabled && lockedChatIds.contains(it.id) }
            }
            return visible.sortedWith(
                compareByDescending<Chat> { pinnedChatIds.contains(it.id) }
                    .thenByDescending { it.lastMessageTimestamp }
            )
        }
}

sealed class HomeEvent : UiEvent {
    data object RefreshChats : HomeEvent()
    data class OpenChat(val chatId: String) : HomeEvent()
    data class TogglePinChat(val chatId: String) : HomeEvent()
    data object SignOut : HomeEvent()
    data object SwitchToStandardMode : HomeEvent()
    data object RefreshLockState : HomeEvent()
}

sealed class HomeEffect : UiEffect {
    data class NavigateToChat(val chatId: String) : HomeEffect()
    data object NavigateToAuth : HomeEffect()
    data class ShowError(val message: String) : HomeEffect()
    data class IncomingCall(val callerId: String, val callId: String, val isVideo: Boolean) : HomeEffect()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val observeChatsUseCase: ObserveChatsUseCase,
    private val messagingRepository: MessagingRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val appLockRepository: AppLockRepository,
    private val chatLockRepository: ChatLockRepository,
    private val firebaseAuth: FirebaseAuth,
    private val webRTCManager: WebRTCManager,
    @ApplicationContext private val context: android.content.Context
) : BaseViewModel<HomeState, HomeEvent, HomeEffect>() {
    
    override val _uiState = MutableStateFlow(HomeState(
        lockedChatIds = chatLockRepository.getLockedChatIds(),
        isPrivateMode = appLockRepository.getCurrentAuthMode() == AuthMode.PRIVATE
    ))
    private var incomingCallListener: ChildEventListener? = null
    private var isCallActive = false

    init {
        webRTCManager.cleanupStaleSignalingData {
            listenForIncomingCalls()
        }
        observeChatsAndPinned()
        // Reset call active flag when WebRTC returns to IDLE
        webRTCManager.callState.onEach { state ->
            if (state == WebRTCManager.CallState.IDLE) {
                isCallActive = false
            }
        }.launchIn(viewModelScope)
    }

    private fun listenForIncomingCalls() {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return
        
        incomingCallListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val status = snapshot.child("status").getValue(String::class.java)
                if (status != "calling") return
                
                val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                val now = System.currentTimeMillis()
                if (now - timestamp > 60_000L) {
                    android.util.Log.d("HomeViewModel", "Ignoring stale incoming call (age: ${(now - timestamp) / 1000}s)")
                    return
                }
                
                if (isCallActive) {
                    android.util.Log.d("HomeViewModel", "Already in a call, ignoring incoming call")
                    return
                }
                
                val callerId = snapshot.child("callerId").getValue(String::class.java) ?: return
                val callId = snapshot.key ?: return
                val isVideo = snapshot.child("isVideo").getValue(Boolean::class.java) ?: false
                
                android.util.Log.d("HomeViewModel", "Incoming call detected from $callerId, callId: $callId")
                isCallActive = true
                sendEffect(HomeEffect.IncomingCall(callerId, callId, isVideo))
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                android.util.Log.d("HomeViewModel", "onChildChanged: ${snapshot.key}")
            }
            override fun onChildRemoved(snapshot: DataSnapshot) {
                android.util.Log.d("HomeViewModel", "onChildRemoved: ${snapshot.key}")
            }
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                android.util.Log.d("HomeViewModel", "onChildMoved: ${snapshot.key}, previousChildName: $previousChildName")
            }
            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("HomeViewModel", "Incoming call listener cancelled: ${error.message}")
            }
        }
        
        incomingCallListener?.let { listener ->
            FirebaseDatabase.getInstance()
                .reference
                .child("/webrtc_signaling")
                .child(currentUserId)
                .addChildEventListener(listener)
        }
    }
    
    fun resetCallState() {
        isCallActive = false
    }

    override fun onCleared() {
        super.onCleared()
        incomingCallListener?.let { listener ->
            val currentUserId = firebaseAuth.currentUser?.uid
            if (currentUserId != null) {
                FirebaseDatabase.getInstance()
                    .reference
                    .child("/webrtc_signaling")
                    .child(currentUserId)
                    .removeEventListener(listener)
            }
        }
    }
    
    override fun onEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.RefreshChats -> observeChatsAndPinned()
            is HomeEvent.OpenChat -> {
                sendEffect(HomeEffect.NavigateToChat(event.chatId))
            }
            is HomeEvent.TogglePinChat -> togglePinChat(event.chatId)
            HomeEvent.SignOut -> signOut()
            HomeEvent.SwitchToStandardMode -> {
                appLockRepository.setCurrentAuthMode(AuthMode.STANDARD)
                setState { copy(isPrivateMode = false) }
            }
            HomeEvent.RefreshLockState -> {
                val currentMode = appLockRepository.getCurrentAuthMode()
                setState {
                    copy(
                        isPrivateMode = currentMode == AuthMode.PRIVATE,
                        isChatLockEnabled = chatLockRepository.isChatLockEnabled(),
                        lockedChatIds = chatLockRepository.getLockedChatIds()
                    )
                }
            }
        }
    }
    
    private var previousChats: List<Chat> = emptyList()

    /** Resolved display names, keyed by user id, so lookups happen once per user. */
    private val titleCache = mutableMapOf<String, String>()

    /** Chat ids with a pin/unpin write currently in flight. */
    private val pendingPins = mutableSetOf<String>()

    /** Held so a RefreshChats cancels the previous collector instead of stacking one. */
    private var observeChatsJob: kotlinx.coroutines.Job? = null

    private fun observeChatsAndPinned() {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return
        observeChatsJob?.cancel()
        observeChatsJob = combine(
            observeChatsUseCase(),
            messagingRepository.getPinnedChats(currentUserId)
        ) { chats, pinnedIds ->
            android.util.Log.d("HomeViewModel", "Received ${chats.size} chats and ${pinnedIds.size} pinned IDs")
            chats to pinnedIds
        }
            .onEach { (chats, pinnedIds) ->
                android.util.Log.d("HomeViewModel", "Updating state with ${chats.size} chats")
                
                // Detect new messages and show notifications
                if (previousChats.isNotEmpty()) {
                    for (chat in chats) {
                        val prevChat = previousChats.find { it.id == chat.id }
                        if (prevChat != null && chat.lastMessageTimestamp > prevChat.lastMessageTimestamp) {
                            val lastMsg = chat.lastMessage
                            if (lastMsg != null && lastMsg.senderId != currentUserId) {
                                val title = chat.title ?: "Chat"
                                val content = lastMsg.content.take(100)
                                MessageNotificationHelper.showMessageNotification(
                                    context,
                                    chat.id,
                                    title,
                                    content
                                )
                            }
                        }
                    }
                }
                previousChats = chats
                
                // Enrich chat titles for private chats without titles.
                // Only unresolved ids trigger a lookup, and the result is cached: the
                // old code called userRepository.getUser() (potentially a Firestore
                // round trip) for every untitled chat on every emission, i.e. on
                // every incoming message in any chat, delaying the whole list.
                val enrichedChats = chats.map { chat ->
                    if (chat.title == "Chat" && chat.participants.size == 2) {
                        val otherUserId = chat.participants.firstOrNull { it != currentUserId }
                        if (otherUserId != null) {
                            val cached = titleCache[otherUserId]
                            if (cached != null) {
                                chat.copy(title = cached)
                            } else {
                                titleCache[otherUserId] = ""
                                viewModelScope.launch {
                                    val otherUser = userRepository.getUser(otherUserId).getOrNull()
                                    val resolved = otherUser?.displayName
                                        ?: otherUser?.username
                                        ?: otherUser?.email
                                        ?: otherUser?.phoneNumber
                                    if (!resolved.isNullOrBlank()) {
                                        titleCache[otherUserId] = resolved
                                        // Only apply if the row is still untitled.
                                        val current = _uiState.value
                                        val idx = current.chats.indexOfFirst {
                                            it.id == chat.id && it.title == "Chat"
                                        }
                                        if (idx >= 0) {
                                            val updated = current.chats.toMutableList()
                                            updated[idx] = updated[idx].copy(title = resolved)
                                            setState { copy(chats = updated) }
                                        }
                                    } else {
                                        titleCache.remove(otherUserId)
                                    }
                                }
                                chat
                            }
                        } else chat
                    } else chat
                }

                setState { 
                    copy(
                        chats = enrichedChats, 
                        pinnedChatIds = pinnedIds,
                        isLoading = false, 
                        error = null 
                    ) 
                }
            }
            .catch { e ->
                android.util.Log.e("HomeViewModel", "Error loading chats: ${e.message}", e)
                setState { copy(isLoading = false, error = e.message) }
                sendEffect(HomeEffect.ShowError(e.message ?: "Failed to load chats"))
            }
            .launchIn(viewModelScope)
    }
    
    private fun togglePinChat(chatId: String) {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return
        // Guard against a double-tap: both taps would otherwise read the same stale
        // `pinnedChatIds` snapshot before the observer re-emits, compute the same
        // target value, and the second write would appear to do nothing.
        if (!pendingPins.add(chatId)) return

        viewModelScope.launch {
            try {
                // Re-read from the repository so the decision is based on committed
                // state rather than a possibly-stale local list.
                val currentlyPinned = runCatching {
                    messagingRepository.getPinnedChats(currentUserId).first().contains(chatId)
                }.getOrElse { _uiState.value.pinnedChatIds.contains(chatId) }

                messagingRepository.pinChat(chatId, currentUserId, !currentlyPinned)
            } catch (e: Exception) {
                sendEffect(HomeEffect.ShowError("Failed to pin chat: ${e.message}"))
            } finally {
                pendingPins.remove(chatId)
            }
        }
    }
    
    private fun signOut() {
        viewModelScope.launch {
            try {
                authRepository.signOut()
                firebaseAuth.signOut()
                sendEffect(HomeEffect.NavigateToAuth)
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Sign out failed", e)
                sendEffect(HomeEffect.ShowError("Sign out failed: ${e.message}"))
            }
        }
    }
}
