package com.gorate.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trip_history")
data class TripHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val price: Double,
    val distanceKm: Double,
    val timeMin: Double,
    val perKm: Double,
    val perHour: Double,
    val clientRating: Float? = null,
    val appOrigin: String,
    val confidence: String,
    val note: String? = null,
    val netEarnings: Double = 0.0,
    val pickupLocation: String? = null,
    val dropoffLocation: String? = null
)
