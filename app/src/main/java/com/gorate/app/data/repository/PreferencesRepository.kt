package com.gorate.app.data.repository

import android.content.Context
import android.content.SharedPreferences

class PreferencesRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gorate_prefs", Context.MODE_PRIVATE)

    fun getMinPerKm(): Double = prefs.getFloat("min_per_km", 0.80f).toDouble()
    fun setMinPerKm(value: Double) = prefs.edit().putFloat("min_per_km", value.toFloat()).apply()

    fun getExcPerKm(): Double = prefs.getFloat("exc_per_km", 1.50f).toDouble()
    fun setExcPerKm(value: Double) = prefs.edit().putFloat("exc_per_km", value.toFloat()).apply()

    fun getMinPerHour(): Double = prefs.getFloat("min_per_hour", 8.00f).toDouble()
    fun setMinPerHour(value: Double) = prefs.edit().putFloat("min_per_hour", value.toFloat()).apply()

    fun getExcPerHour(): Double = prefs.getFloat("exc_per_hour", 12.00f).toDouble()
    fun setExcPerHour(value: Double) = prefs.edit().putFloat("exc_per_hour", value.toFloat()).apply()

    fun getMinClientRating(): Double = prefs.getFloat("min_client_rating", 4.5f).toDouble()
    fun setMinClientRating(value: Double) = prefs.edit().putFloat("min_client_rating", value.toFloat()).apply()

    fun isTurboModeEnabled(): Boolean = prefs.getBoolean("turbo_mode", true)
    fun setTurboModeEnabled(enabled: Boolean) = prefs.edit().putBoolean("turbo_mode", enabled).apply()

    fun isMilesEnabled(): Boolean = prefs.getBoolean("is_miles_enabled", false)
    fun setMilesEnabled(enabled: Boolean) = prefs.edit().putBoolean("is_miles_enabled", enabled).apply()

    fun isMinutesEnabled(): Boolean = prefs.getBoolean("is_minutes_enabled", false)
    fun setMinutesEnabled(enabled: Boolean) = prefs.edit().putBoolean("is_minutes_enabled", enabled).apply()

    // 0: System (Auto), 1: Light, 2: Dark
    fun getThemeMode(): Int = prefs.getInt("theme_mode", 0)
    fun setThemeMode(mode: Int) = prefs.edit().putInt("theme_mode", mode).apply()

    fun getUserEmail(): String? = prefs.getString("user_email", null)
    fun setUserEmail(email: String?) = prefs.edit().putString("user_email", email).apply()

    fun isServiceEnabled(): Boolean = prefs.getBoolean("service_enabled", false)
    fun setServiceEnabled(enabled: Boolean) = prefs.edit().putBoolean("service_enabled", enabled).apply()

    fun getFuelCostPerKm(): Double = prefs.getFloat("fuel_cost_km", 0.05f).toDouble()
    fun setFuelCostPerKm(value: Double) = prefs.edit().putFloat("fuel_cost_km", value.toFloat()).apply()

    fun isNetEarningsEnabled(): Boolean = prefs.getBoolean("net_earnings_enabled", false)
    fun setNetEarningsEnabled(enabled: Boolean) = prefs.edit().putBoolean("net_earnings_enabled", enabled).apply()

    fun isPrivacyAccepted(): Boolean = prefs.getBoolean("privacy_accepted", false)
    fun setPrivacyAccepted(accepted: Boolean) = prefs.edit().putBoolean("privacy_accepted", accepted).apply()

    fun isOverlayBubbleEnabled(): Boolean = prefs.getBoolean("is_overlay_bubble_enabled", true)
    fun setOverlayBubbleEnabled(enabled: Boolean) = prefs.edit().putBoolean("is_overlay_bubble_enabled", enabled).apply()

    fun getDriverMode(): String = prefs.getString("driver_mode", "AUTO") ?: "AUTO"
    fun setDriverMode(mode: String) = prefs.edit().putString("driver_mode", mode).apply()

    fun getVehiclePlate(): String = prefs.getString("vehicle_plate", "") ?: ""
    fun setVehiclePlate(plate: String) = prefs.edit().putString("vehicle_plate", plate).apply()

    fun isPicoPlacaEnabled(): Boolean = prefs.getBoolean("pico_placa_enabled", false)
    fun setPicoPlacaEnabled(enabled: Boolean) = prefs.edit().putBoolean("pico_placa_enabled", enabled).apply()

    fun getOverlayX(): Int = prefs.getInt("overlay_x", 0)
    fun setOverlayX(x: Int) = prefs.edit().putInt("overlay_x", x).apply()

    fun getOverlayY(): Int = prefs.getInt("overlay_y", 20)
    fun setOverlayY(y: Int) = prefs.edit().putInt("overlay_y", y).apply()

    fun isOnboardingCompleted(): Boolean = prefs.getBoolean("onboarding_completed", false)
    fun setOnboardingCompleted(completed: Boolean) = prefs.edit().putBoolean("onboarding_completed", completed).apply()

    fun getInstallTimestamp(): Long {
        var installTime = prefs.getLong("install_timestamp", 0L)
        if (installTime == 0L) {
            installTime = System.currentTimeMillis()
            prefs.edit().putLong("install_timestamp", installTime).apply()
        }
        return installTime
    }

    fun syncInstallTimestamp(timestamp: Long) {
        if (timestamp <= 0L) return
        val currentInstall = prefs.getLong("install_timestamp", 0L)
        // Usar la fecha más antigua conocida (fecha de registro de cuenta original)
        if (currentInstall == 0L || timestamp < currentInstall) {
            prefs.edit().putLong("install_timestamp", timestamp).apply()
        }
    }

    fun getTrialExpiryTimestamp(): Long {
        val thirtyDaysMillis = 30L * 24 * 60 * 60 * 1000L
        return getInstallTimestamp() + thirtyDaysMillis
    }

    fun isTrialActive(): Boolean {
        return System.currentTimeMillis() < getTrialExpiryTimestamp()
    }

    fun isTrialExpired(): Boolean {
        return !isTrialActive() && !isProUser()
    }

    fun getTrialDaysRemaining(): Int {
        val remaining = getTrialExpiryTimestamp() - System.currentTimeMillis()
        return if (remaining <= 0L) 0 else ((remaining / (24 * 60 * 60 * 1000L)) + 1).toInt()
    }

    fun isProUser(): Boolean = prefs.getBoolean("is_pro_user", false) || isAdminUser()
    fun setProUser(isPro: Boolean) = prefs.edit().putBoolean("is_pro_user", isPro).apply()

    fun canUseService(): Boolean = isTrialActive() || isProUser()

    /**
     * Verificación estricta y blindada de cuenta Administrador.
     * Requiere que el usuario esté autenticado en los servidores de Firebase
     * con el correo oficial, imposibilitando bypasses locales en dispositivos rooteados.
     */
    fun isAdminUser(): Boolean {
        val authUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val firebaseEmail = authUser?.email ?: ""
        val localEmail = getUserEmail() ?: ""
        return firebaseEmail.equals("admin@gorate.app", ignoreCase = true) &&
               localEmail.equals("admin@gorate.app", ignoreCase = true)
    }
}
