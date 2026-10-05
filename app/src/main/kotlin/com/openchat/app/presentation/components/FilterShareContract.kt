package com.openchat.app.presentation.components

import com.openchat.app.core.UiEffect
import com.openchat.app.core.UiEvent
import com.openchat.app.core.UiState
import com.openchat.app.data.model.FilterPack

data class FilterShareState(
    val isLoading: Boolean = false,
    val myPacks: List<FilterPack> = emptyList(),
    val publicPacks: List<FilterPack> = emptyList(),
    val sendingPackId: String? = null,
    val browseMode: Boolean = false
) : UiState

sealed class FilterShareEvent : UiEvent {
    data object LoadMyPacks : FilterShareEvent()
    data class SendFilterPack(val packId: String, val targetUserId: String) : FilterShareEvent()
    data object SwitchToBrowseMode : FilterShareEvent()
}

sealed class FilterShareEffect : UiEffect {
    data object PackSent : FilterShareEffect()
    data class ShowError(val message: String) : FilterShareEffect()
}
