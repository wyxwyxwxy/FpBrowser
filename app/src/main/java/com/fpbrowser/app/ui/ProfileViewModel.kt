package com.fpbrowser.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fpbrowser.app.data.ProfileEntity
import com.fpbrowser.app.data.ProfileRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ProfileRepository.get(app)

    val profiles: StateFlow<List<ProfileEntity>> =
        repo.profiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { repo.ensureSeeded() }
    }

    fun save(profile: ProfileEntity) = viewModelScope.launch { repo.save(profile) }

    fun delete(profile: ProfileEntity) = viewModelScope.launch { repo.delete(profile) }

    fun touch(profile: ProfileEntity) = viewModelScope.launch {
        repo.save(profile.copy(lastUsedAt = System.currentTimeMillis()))
    }
}
