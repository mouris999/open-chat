package com.openchat.app.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.openchat.app.domain.repository.AppLockRepository
import com.openchat.app.presentation.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val appLockRepository: AppLockRepository
) : AndroidViewModel(application) {

    suspend fun getStartDestination(): String? {
        return if (appLockRepository.isAppLockEnabled()) {
            Routes.LOCK
        } else {
            null
        }
    }
}
