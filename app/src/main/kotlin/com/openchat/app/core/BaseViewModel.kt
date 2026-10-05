package com.openchat.app.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

interface UiState
interface UiEvent
interface UiEffect

abstract class BaseViewModel<
    State : UiState,
    Event : UiEvent,
    Effect : UiEffect
> : ViewModel() {
    protected abstract val _uiState: MutableStateFlow<State>
    val uiState: StateFlow<State> by lazy { _uiState.asStateFlow() }
    
    protected val _effect = Channel<Effect>(Channel.BUFFERED)
    val effect: Flow<Effect> = _effect.receiveAsFlow()
    
    abstract fun onEvent(event: Event)
    
    protected fun setState(reduce: State.() -> State) {
        val newState = _uiState.value.reduce()
        _uiState.value = newState
    }
    
    protected fun sendEffect(effect: Effect) {
        viewModelScope.launch {
            _effect.send(effect)
        }
    }
    
    protected fun updateState(update: (State) -> State) {
        _uiState.value = update(_uiState.value)
    }
}
