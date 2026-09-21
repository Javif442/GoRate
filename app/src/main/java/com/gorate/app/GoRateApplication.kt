package com.gorate.app

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.gorate.app.data.repository.PreferencesRepository
import com.gorate.app.data.repository.TripRepositoryImpl

/**
 * Global Application class.
 * Handles initialization of repositories and app-wide configurations.
 */
class GoRateApplication : Application() {
    
    lateinit var tripRepository: TripRepositoryImpl

    override fun onCreate() {
        super.onCreate()
        
        try {
            tripRepository = TripRepositoryImpl(this)
            applyThemeConfiguration()
        } catch (e: Exception) {
            Log.e("GoRateApp", "Critical initialization failure", e)
        }
    }

    private fun applyThemeConfiguration() {
        val prefs = PreferencesRepository(this)
        val mode = when (prefs.getThemeMode()) {
            1 -> AppCompatDelegate.MODE_NIGHT_NO
            2 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> {
                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                if (hour in 7..18) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
            }
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
