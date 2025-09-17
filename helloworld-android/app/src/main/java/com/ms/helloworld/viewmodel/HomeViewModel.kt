package com.ms.helloworld.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ms.helloworld.dto.response.MomProfile
import com.ms.helloworld.repository.MomProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val momProfileRepository: MomProfileRepository
) : ViewModel() {
    
    private val _momProfile = MutableStateFlow(
        MomProfile(
            nickname = "로딩중...",
            pregnancyWeek = 1,
            dueDate = LocalDate.now()
        )
    )
    val momProfile: StateFlow<MomProfile> = _momProfile.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    init {
        loadMomProfile()
    }
    
    private fun loadMomProfile() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val profile = momProfileRepository.getMomProfile()
                if (profile != null) {
                    _momProfile.value = profile
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun refreshProfile() {
        loadMomProfile()
    }
}