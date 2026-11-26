package com.dailer.app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.dailer.app.DailerApplication
import com.dailer.app.data.local.entity.CallRecordEntity
import com.dailer.app.data.local.entity.RelativeEntity

class CallHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as DailerApplication).repository

    val callRecords: LiveData<List<CallRecordEntity>> = repository.getAllCallRecords().asLiveData()
    val relatives: LiveData<List<RelativeEntity>> = repository.getAllRelatives().asLiveData()
}
