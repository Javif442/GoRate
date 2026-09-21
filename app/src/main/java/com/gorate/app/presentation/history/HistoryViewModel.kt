package com.gorate.app.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gorate.app.data.local.TripHistoryEntity
import com.gorate.app.data.repository.TripRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class HistoryViewModel(private val repository: TripRepositoryImpl) : ViewModel() {

    private val _filter = MutableStateFlow(HistoryFilter.ALL)
    private val _dateRange = MutableStateFlow<Pair<Long, Long>?>(null)
    private val _searchQuery = MutableStateFlow("")

    val history: StateFlow<List<TripHistoryEntity>> = repository.getAllHistory()
        .combine(_filter) { trips, filterMode ->
            filterByMode(trips, filterMode)
        }
        .combine(_dateRange) { trips, range ->
            if (range == null) trips 
            else trips.filter { it.timestamp in range.first..range.second }
        }
        .combine(_searchQuery) { trips, query ->
            if (query.isBlank()) trips
            else trips.filter { 
                it.note?.contains(query, ignoreCase = true) == true || 
                it.appOrigin.contains(query, ignoreCase = true) ||
                it.pickupLocation?.contains(query, ignoreCase = true) == true ||
                it.dropoffLocation?.contains(query, ignoreCase = true) == true
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private fun filterByMode(trips: List<TripHistoryEntity>, mode: HistoryFilter): List<TripHistoryEntity> {
        val configuredMinKm = repository.prefsRepository.getMinPerKm()
        return when (mode) {
            HistoryFilter.ALL -> trips
            HistoryFilter.GOALS_MET -> trips.filter { it.perKm >= configuredMinKm }
            HistoryFilter.REJECTED -> trips.filter { it.perKm < configuredMinKm }
            HistoryFilter.TODAY -> {
                val today = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                trips.filter { it.timestamp >= today }
            }
        }
    }

    fun setFilter(newFilter: HistoryFilter) {
        _filter.value = newFilter
        _dateRange.value = null // Reset date range when switching filters
    }

    fun setDateRange(start: Long, end: Long) {
        // Extender el final del rango hasta las 23:59:59 del día seleccionado
        _dateRange.value = Pair(start, end + 86_399_999L)
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun deleteSelectedTrips(tripIds: List<Long>) {
        viewModelScope.launch {
            repository.deleteTrips(tripIds)
        }
    }

    fun updateNote(tripId: Long, note: String) {
        viewModelScope.launch {
            repository.updateNote(tripId, note)
        }
    }
}

enum class HistoryFilter { ALL, GOALS_MET, REJECTED, TODAY }

class HistoryViewModelFactory(private val repository: TripRepositoryImpl) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
