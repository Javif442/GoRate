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
    
    companion object {
        var isAppInForeground: Boolean = false
            private set
    }

    lateinit var tripRepository: TripRepositoryImpl

    override fun onCreate() {
        super.onCreate()
        
        try {
            tripRepository = TripRepositoryImpl(this)
            applyThemeConfiguration()
            setupActivityLifecycleTracking()
        } catch (e: Exception) {
            Log.e("GoRateApp", "Critical initialization failure", e)
        }
    }

    private fun setupActivityLifecycleTracking() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var resumedCount = 0

            override fun onActivityResumed(activity: android.app.Activity) {
                resumedCount++
                isAppInForeground = resumedCount > 0
            }

            override fun onActivityPaused(activity: android.app.Activity) {
                resumedCount--
                isAppInForeground = resumedCount > 0
            }

            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
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
