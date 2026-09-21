package com.gorate.app.data.service

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings

/**
 * Manages mandatory app updates using Firebase Remote Config.
 */
class VersionController(private val context: Context) {

    private val remoteConfig: FirebaseRemoteConfig by lazy {
        FirebaseRemoteConfig.getInstance()
    }

    init {
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600 // Fetch every hour in production
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(mapOf("minimum_app_version" to "1.0.0"))
    }

    fun checkVersion(onUpdateRequired: (Boolean) -> Unit) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            onUpdateRequired(false)
            return
        }

        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Actualizar patrones de Uber en tiempo real si existen en Firebase
                val pricePattern = remoteConfig.getString("uber_price_pattern")
                val distPattern = remoteConfig.getString("uber_dist_pattern")
                val timePattern = remoteConfig.getString("uber_time_pattern")
                
                if (pricePattern.isNotEmpty()) {
                    com.gorate.app.parser.UberParser().updatePatterns(pricePattern, distPattern, timePattern)
                }

                val minVersion = remoteConfig.getString("minimum_app_version")
                val currentVersion = getCurrentVersionName()
                onUpdateRequired(isUpdateMandatory(currentVersion, minVersion))
            } else {
                onUpdateRequired(false)
            }
        }
    }

    private fun isUpdateMandatory(current: String, minimum: String): Boolean {
        return try {
            val currentParts = current.split(".").map { it.toInt() }
            val minimumParts = minimum.split(".").map { it.toInt() }
            
            for (i in 0 until minOf(currentParts.size, minimumParts.size)) {
                if (currentParts[i] < minimumParts[i]) return true
                if (currentParts[i] > minimumParts[i]) return false
            }
            currentParts.size < minimumParts.size
        } catch (e: Exception) {
            false
        }
    }

    private fun getCurrentVersionName(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0.0"
        }
    }
}
