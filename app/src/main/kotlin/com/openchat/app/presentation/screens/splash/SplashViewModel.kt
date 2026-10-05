package com.openchat.app.presentation.screens.splash

import androidx.lifecycle.ViewModel
import com.openchat.app.domain.usecase.auth.IsAuthenticatedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val isAuthenticatedUseCase: IsAuthenticatedUseCase
) : ViewModel() {
    
    fun isAuthenticated(): Boolean {
        return isAuthenticatedUseCase()
    }
}
