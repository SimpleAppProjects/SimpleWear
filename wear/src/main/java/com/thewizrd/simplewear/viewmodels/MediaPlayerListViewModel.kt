package com.thewizrd.simplewear.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.thewizrd.simplewear.controls.AppItemViewModel

class MediaPlayerListViewModel : ViewModel() {
    val mediaAppsList: MutableLiveData<List<AppItemViewModel>> = MutableLiveData()
    val filteredAppsList: MutableLiveData<Set<String>> = MutableLiveData()
}