package com.dailer.app.ui.setup

import android.app.Application
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.dailer.app.data.remote.RetrofitClient
import com.dailer.app.util.Constants
import com.dailer.app.util.PermissionHelper
import com.dailer.app.util.RoleManagerHelper
import com.dailer.app.util.setupDataStore
import kotlinx.coroutines.launch

class SetupViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = application.setupDataStore

    private val _permissionsGranted = MutableLiveData(false)
    val permissionsGranted: LiveData<Boolean> = _permissionsGranted

    private val _isDefaultDialer = MutableLiveData(false)
    val isDefaultDialer: LiveData<Boolean> = _isDefaultDialer

    private val _serverUrl = MutableLiveData(Constants.BASE_URL)
    val serverUrl: LiveData<String> = _serverUrl

    private val _isServerConfigured = MutableLiveData(true)
    val isServerConfigured: LiveData<Boolean> = _isServerConfigured

    private val _isSetupComplete = MutableLiveData(false)
    val isSetupComplete: LiveData<Boolean> = _isSetupComplete

    init {
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                val savedUrl = prefs[KEY_SERVER_URL]?.takeIf { it.isNotBlank() } ?: Constants.BASE_URL
                _serverUrl.value = savedUrl
                val valid = isValidServerUrl(savedUrl)
                _isServerConfigured.value = valid
                if (valid) {
                    RetrofitClient.setBaseUrl(savedUrl)
                }
                _isSetupComplete.value = prefs[KEY_SETUP_COMPLETE] ?: false
            }
        }
        refreshStatus()
    }

    fun refreshStatus() {
        val context = getApplication<Application>()
        _permissionsGranted.value = PermissionHelper.hasAllPermissions(context)
        _isDefaultDialer.value = RoleManagerHelper.isDefaultDialer(context)
    }

    fun saveServerUrl(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (!isValidServerUrl(trimmed)) {
            return false
        }
        val normalized = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        RetrofitClient.setBaseUrl(normalized)
        _serverUrl.value = normalized
        _isServerConfigured.value = true

        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_SERVER_URL] = normalized
            }
        }
        return true
    }

    fun completeSetup() {
        _isSetupComplete.value = true
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_SETUP_COMPLETE] = true
            }
        }
    }

    private fun isValidServerUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

    companion object {
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
    }
}
