package com.gorate.app.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.gorate.app.data.repository.PreferencesRepository
import com.gorate.app.domain.repository.TripRepository

class MainViewModelFactory(
    private val preferences: PreferencesRepository,
    private val tripRepository: TripRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(preferences, tripRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
