package com.example.mylauncher

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException


data class LauncherUiState(
    val allApps: List<AppInfo> = emptyList(),
    val homePackages: Set<String> = emptySet(),
    val isLoading: Boolean = true,
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppRepository(application)
    private val dataStore: DataStore<Preferences> = application.launcherDataStore

    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    private val preferences: Flow<Preferences> = dataStore.data.catch { throwable ->
        if (throwable is IOException) {
            emit(emptyPreferences())
        } else {
            throw throwable
        }
    }

    init {
        refreshApps(showLoading = true)
    }

    fun refreshApps(showLoading: Boolean = false) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.update { it.copy(isLoading = true) }
            }

            val packages = preferences
                .first()[LauncherPreferenceKeys.homePackages]
                .orEmpty()

            val apps = withContext(Dispatchers.Default) {
                repository.loadLaunchableApps()
            }

            _uiState.update {
                it.copy(
                    allApps = apps,
                    homePackages = packages,
                    isLoading = false,
                )
            }
        }
    }

    fun addToHome(packageName: String) {
        updateHomePackages { it + packageName }
    }

    fun removeFromHome(packageName: String) {
        updateHomePackages { it - packageName }
    }

    private fun updateHomePackages(transform: (Set<String>) -> Set<String>) {
        viewModelScope.launch {
            val current = preferences
                .first()[LauncherPreferenceKeys.homePackages]
                .orEmpty()

            val updated = transform(current)

            dataStore.edit { preferences ->
                preferences[LauncherPreferenceKeys.homePackages] = updated
            }

            _uiState.update { it.copy(homePackages = updated) }
        }
    }
}
