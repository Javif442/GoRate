package com.gorate.app.domain.model

data class ProfitResult(
    val perKm: Double,
    val perHour: Double,
    val price: Double,
    val distanceKm: Double,
    val timeMin: Double,
    val clientRating: Float? = null,
    val netEarnings: Double = 0.0,
    val pickupLocation: String? = null,
    val dropoffLocation: String? = null,
    val isRadar: Boolean = false,
    val tripTag: String? = null
)
