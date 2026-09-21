package com.gorate.app.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gorate.app.data.repository.PreferencesRepository
import com.gorate.app.domain.model.ProfitResult
import com.gorate.app.domain.repository.TripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class MainViewModel(
    private val preferences: PreferencesRepository,
    private val tripRepository: TripRepository
) : ViewModel() {

    companion object {
        const val DEFAULT_MIN_PER_KM = 0.80
        const val DEFAULT_MIN_PER_HOUR = 8.00
        private const val TRIAL_DAYS = 15
    }

    private val _isServiceActive = MutableStateFlow(false)
    val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

    // Real-time trip data from repository
    val currentTrip: StateFlow<ProfitResult?> = tripRepository.currentTrip

    init {
        viewModelScope.launch {
            tripRepository.isServiceRunning.collect { running ->
                _isServiceActive.value = running
            }
        }
    }

    fun getMinPerKm() = preferences.getMinPerKm()
    fun getExcPerKm() = preferences.getExcPerKm()
    fun getMinPerHour() = preferences.getMinPerHour()
    fun getExcPerHour() = preferences.getExcPerHour()

    fun isTurboModeEnabled() = preferences.isTurboModeEnabled()
    fun setTurboModeEnabled(enabled: Boolean) = preferences.setTurboModeEnabled(enabled)

    fun saveLimits(minKm: Double, excKm: Double, minHour: Double, excHour: Double) {
        preferences.setMinPerKm(minKm)
        preferences.setExcPerKm(excKm)
        preferences.setMinPerHour(minHour)
        preferences.setExcPerHour(excHour)
    }

    fun getFuelCostPerKm() = preferences.getFuelCostPerKm()
    fun saveFuelCost(cost: Double) = preferences.setFuelCostPerKm(cost)

    fun isNetEarningsEnabled() = preferences.isNetEarningsEnabled()
    fun setNetEarningsEnabled(enabled: Boolean) = preferences.setNetEarningsEnabled(enabled)

    fun isMilesEnabled(): Boolean = preferences.isMilesEnabled()
    fun setMilesEnabled(enabled: Boolean) = preferences.setMilesEnabled(enabled)

    fun isMinutesEnabled(): Boolean = preferences.isMinutesEnabled()
    fun setMinutesEnabled(enabled: Boolean) = preferences.setMinutesEnabled(enabled)

    fun isOverlayBubbleEnabled(): Boolean = preferences.isOverlayBubbleEnabled()
    fun setOverlayBubbleEnabled(enabled: Boolean) = preferences.setOverlayBubbleEnabled(enabled)

    fun getDriverMode(): String = preferences.getDriverMode()
    fun setDriverMode(mode: String) = preferences.setDriverMode(mode)

    fun getVehiclePlate(): String = preferences.getVehiclePlate()
    fun setVehiclePlate(plate: String) = preferences.setVehiclePlate(plate)

    fun isPicoPlacaEnabled(): Boolean = preferences.isPicoPlacaEnabled()
    fun setPicoPlacaEnabled(enabled: Boolean) = preferences.setPicoPlacaEnabled(enabled)

    fun isProUser(): Boolean = preferences.isProUser()
    fun canUseService(): Boolean = preferences.canUseService()

    fun getThemeMode(): Int = preferences.getThemeMode()
    fun setThemeMode(mode: Int) = preferences.setThemeMode(mode)

    fun getUserEmail(): String = preferences.getUserEmail() ?: "invitado@gorate.app"
    fun isUserLoggedIn(): Boolean = preferences.getUserEmail() != null

    /**
     * Calculates real trial expiration based on installation logic.
     * Returns a formatted string like "15 sept 2026"
     */
    fun isAdminUser(): Boolean = preferences.isAdminUser()

    fun getTrialExpiryDate(): String {
        if (preferences.isAdminUser()) {
            return "PRO ILIMITADO (Administrador)"
        }
        if (preferences.isProUser()) {
            return "MEMBRESÍA PRO ACTIVA"
        }
        val expiryTime = preferences.getTrialExpiryTimestamp()
        val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(expiryTime))
    }
}
