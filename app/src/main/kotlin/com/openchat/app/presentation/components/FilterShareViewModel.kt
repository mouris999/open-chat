package com.openchat.app.presentation.components

import androidx.lifecycle.viewModelScope
import com.openchat.app.core.BaseViewModel
import com.openchat.app.core.UiEffect
import com.openchat.app.core.UiEvent
import com.openchat.app.core.UiState
import com.openchat.app.data.model.FilterPack
import com.openchat.app.data.repository.FilterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject

@HiltViewModel
class FilterShareViewModel @Inject constructor(
    private val filterRepository: FilterRepository,
    private val firebaseAuth: FirebaseAuth
) : BaseViewModel<FilterShareState, FilterShareEvent, FilterShareEffect>() {

    private val currentUserId: String get() = firebaseAuth.currentUser?.uid ?: ""

    override val _uiState = MutableStateFlow(FilterShareState())

    override fun onEvent(event: FilterShareEvent) {
        when (event) {
            is FilterShareEvent.LoadMyPacks -> loadMyFilterPacks()
            is FilterShareEvent.SendFilterPack -> {
                viewModelScope.launch {
                    sendFilterPackInternal(event.packId, event.targetUserId)
                }
            }
            is FilterShareEvent.SwitchToBrowseMode -> switchToBrowseMode()
        }
    }

    fun loadMyFilterPacks() {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            
            try {
                val officialPack = filterRepository.getOfficialPack()
                val userPacks = filterRepository.getUserFilterPacks(currentUserId)
                val myPacks = mutableListOf(officialPack)
                myPacks.addAll(userPacks)
                
                setState { 
                    copy(
                        myPacks = myPacks,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("FilterShareVM", "Failed to load packs", e)
                setState { 
                    copy(
                        isLoading = false,
                        myPacks = listOf(filterRepository.getOfficialPack())
                    )
                }
            }
        }
    }

    internal fun switchToBrowseMode() {
        setState { copy(browseMode = true) }
        
        // Load public filter packs
        viewModelScope.launch {
            try {
                filterRepository.observePublicFilterPacks()
                    .collect { packs ->
                        setState { copy(publicPacks = packs) }
                    }
            } catch (e: Exception) {
                android.util.Log.e("FilterShareVM", "Failed to load public packs", e)
            }
        }
    }

    private suspend fun sendFilterPackInternal(packId: String, targetUserId: String) {
        setState { copy(sendingPackId = packId) }
        
        val result = filterRepository.sendFilterPackToUser(packId, targetUserId)
        
        setState { copy(sendingPackId = null) }
        
        if (result.isSuccess) {
            sendEffect(FilterShareEffect.PackSent)
        } else {
            sendEffect(
                FilterShareEffect.ShowError(
                    result.exceptionOrNull()?.message ?: "Failed to send filter pack"
                )
            )
        }
    }
}
