package com.dailer.app.ui.relatives

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.dailer.app.DailerApplication
import com.dailer.app.data.local.entity.RelativeEntity
import kotlinx.coroutines.launch

class RelativesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as DailerApplication).repository

    val relatives: LiveData<List<RelativeEntity>> = repository.getAllRelatives().asLiveData()

    fun refreshFromServer() {
        viewModelScope.launch {
            repository.syncEnrollmentsFromServer()
        }
    }

    fun removeRelative(relative: RelativeEntity) {
        viewModelScope.launch {
            repository.deleteEnrollment(relative)
        }
    }
}
