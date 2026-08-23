package com.droidates.wallpapers.core.viewmodel

import androidx.lifecycle.ViewModel
import com.droidates.wallpapers.core.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PreferencesManagerViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    fun isPrivacyPolicyAccepted(): Boolean {
        return preferencesManager.isPrivacyPolicyAccepted()
    }
    
    fun setPrivacyPolicyAccepted(accepted: Boolean) {
        preferencesManager.setPrivacyPolicyAccepted(accepted)
    }
    
    fun isOnboardingCompleted(): Boolean {
        return preferencesManager.isOnboardingCompleted()
    }
    
    fun setOnboardingCompleted(completed: Boolean) {
        preferencesManager.setOnboardingCompleted(completed)
    }
    
    fun shouldCheckForUpdates(): Boolean {
        return preferencesManager.shouldCheckForUpdates()
    }
    
    fun setLastUpdateCheck(timestamp: Long) {
        preferencesManager.setLastUpdateCheck(timestamp)
    }
} 