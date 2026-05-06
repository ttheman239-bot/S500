package com.s500.driver.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.s500.driver.data.DriverData
import com.s500.driver.data.DriverRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UiState {
    data object Loading : UiState
    data class Ready(val data: DriverData) : UiState
    data class Error(val message: String) : UiState
}

class MainViewModel(
    private val repo: DriverRepository = DriverRepository()
) : ViewModel() {

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _window = MutableStateFlow(5)        // sessions
    val window: StateFlow<Int> = _window.asStateFlow()

    init { refresh() }

    fun setWindow(sessions: Int) {
        if (_window.value == sessions) return
        _window.value = sessions
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                UiState.Ready(repo.load(_window.value))
            } catch (t: Throwable) {
                UiState.Error(t.message ?: "Unknown error")
            }
        }
    }
}
