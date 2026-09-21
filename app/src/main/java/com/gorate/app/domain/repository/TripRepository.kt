package com.gorate.app.domain.repository

import com.gorate.app.domain.model.ProfitResult
import kotlinx.coroutines.flow.StateFlow

interface TripRepository {
    val currentTrip: StateFlow<ProfitResult?>
    val isServiceRunning: StateFlow<Boolean>
    suspend fun emitTrip(result: ProfitResult?, origin: String = "Uber")
    fun setServiceRunningState(running: Boolean)
    fun clearCurrentTrip()
}
