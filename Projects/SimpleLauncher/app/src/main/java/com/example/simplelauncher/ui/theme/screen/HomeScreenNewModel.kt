package com.example.simplelauncher.ui.theme.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simplelauncher.data.AppInfo
import com.example.simplelauncher.data.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UiState(
    val homeApps: List<AppInfo> = emptyList(),
    val allApps: List<AppInfo> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class HomeScreenViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val all = repository.getLaunchableApps()
                val home = repository.getHomeApps()
                _state.value = UiState(homeApps = home, allApps = all, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun addApp(packageName: String) {
        repository.addHomeApp(packageName)
        refreshHome()
    }

    fun removeApp(packageName: String) {
        repository.removeHomeApp(packageName)
        refreshHome()
    }

    private fun refreshHome() {
        viewModelScope.launch {
            val home = repository.getHomeApps()
            _state.value = _state.value.copy(homeApps = home)
        }
    }
}
