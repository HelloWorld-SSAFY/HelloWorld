package com.ms.helloworld.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ms.helloworld.repository.StepsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SubmissionState {
    object Loading : SubmissionState()
    object Success : SubmissionState()
    data class Error(val message: String) : SubmissionState()
}

@HiltViewModel
class StepsViewModel @Inject constructor(
    private val stepsRepository: StepsRepository
) : ViewModel() {

    private val _submissionState = MutableLiveData<SubmissionState>()
    val submissionState: LiveData<SubmissionState> = _submissionState

    fun submitSteps(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            try {
                _submissionState.value = SubmissionState.Loading
                val result = stepsRepository.submitStepsData(latitude, longitude)
                if (result.isSuccess) {
                    _submissionState.value = SubmissionState.Success
                } else {
                    _submissionState.value = SubmissionState.Error(
                        result.exceptionOrNull()?.message ?: "알 수 없는 오류"
                    )
                }
            } catch (e: Exception) {
                _submissionState.value = SubmissionState.Error(e.message ?: "알 수 없는 오류")
            }
        }
    }
}