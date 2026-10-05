package com.openchat.app.presentation.screens.stories

import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.openchat.app.core.BaseViewModel
import com.openchat.app.core.UiEffect
import com.openchat.app.core.UiEvent
import com.openchat.app.core.UiState
import com.openchat.app.domain.repository.StoryRepository
import com.openchat.app.domain.model.Story
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StoriesState(
    val stories: List<Story> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
) : UiState

// Delete StoryItem class if it exists elsewhere, or keep it if needed for UI mapping.
// Here I'll just use Story domain model directly.

sealed class StoriesEvent : UiEvent {
    data class UploadStory(val uri: Uri) : StoriesEvent()
    data object Refresh : StoriesEvent()
    data class ViewStory(val story: Story) : StoriesEvent()
    data class ReactToStory(val storyId: String, val emoji: String) : StoriesEvent()
    data class ReplyToStory(val storyId: String, val reply: String) : StoriesEvent()
    data class ViewStoryViewers(val storyId: String) : StoriesEvent()
}

sealed class StoriesEffect : UiEffect {
    data class ShowError(val message: String) : StoriesEffect()
    data object StoryUploaded : StoriesEffect()
    data class OpenStoryViewer(val index: Int) : StoriesEffect()
    data class ShowViewers(val storyId: String) : StoriesEffect()
}

@HiltViewModel
class StoriesViewModel @Inject constructor(
    private val storyRepository: StoryRepository
) : BaseViewModel<StoriesState, StoriesEvent, StoriesEffect>() {

    private companion object { const val TAG = "StoriesViewModel" }

    override val _uiState = MutableStateFlow(StoriesState())

    init {
        observeStories()
    }

    private fun observeStories() {
        storyRepository.observeStories()
            .onEach { stories ->
                setState { copy(stories = stories) }
            }
            // Without .catch an upstream RTDB/Firestore failure propagates to the
            // default handler and crashes the app.
            .catch { e ->
                Log.e(TAG, "observeStories failed", e)
                setState { copy(isLoading = false, error = e.message) }
                sendEffect(StoriesEffect.ShowError(e.message ?: "Failed to load stories"))
            }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: StoriesEvent) {
        when (event) {
            is StoriesEvent.UploadStory -> uploadStory(event.uri)
            StoriesEvent.Refresh -> { /* Already observing */ }
            is StoriesEvent.ViewStory -> {
                // Match on id, not object identity: the list can refresh or reorder
                // between the tap and this handler, and indexOf returns -1 in that
                // case - which opened the viewer at an invalid index.
                val index = _uiState.value.stories.indexOfFirst { it.id == event.story.id }
                if (index >= 0) {
                    sendEffect(StoriesEffect.OpenStoryViewer(index))
                } else {
                    sendEffect(StoriesEffect.ShowError("This story is no longer available"))
                }
            }
            is StoriesEvent.ReactToStory -> reactToStory(event.storyId, event.emoji)
            is StoriesEvent.ReplyToStory -> replyToStory(event.storyId, event.reply)
            is StoriesEvent.ViewStoryViewers -> sendEffect(StoriesEffect.ShowViewers(event.storyId))
        }
    }

    private fun reactToStory(storyId: String, emoji: String) {
        viewModelScope.launch {
            // viewModelScope has no CoroutineExceptionHandler, so an uncaught throw
            // here crashes the process instead of surfacing an error.
            runCatching { storyRepository.reactToStory(storyId, emoji) }
                .onFailure {
                    Log.e(TAG, "reactToStory failed", it)
                    sendEffect(StoriesEffect.ShowError(it.message ?: "Could not send reaction"))
                }
        }
    }

    private fun replyToStory(storyId: String, reply: String) {
        viewModelScope.launch {
            runCatching { storyRepository.replyToStory(storyId, reply) }
                .onFailure {
                    Log.e(TAG, "replyToStory failed", it)
                    sendEffect(StoriesEffect.ShowError(it.message ?: "Could not send reply"))
                }
        }
    }

    private fun uploadStory(uri: Uri) {
        setState { copy(isLoading = true) }
        viewModelScope.launch {
            val result = storyRepository.uploadStory(uri)
            setState { copy(isLoading = false) }
            
            result.onSuccess {
                sendEffect(StoriesEffect.StoryUploaded)
            }.onError { error, _ ->
                sendEffect(StoriesEffect.ShowError(error.message ?: "Upload failed"))
            }
        }
    }
}
