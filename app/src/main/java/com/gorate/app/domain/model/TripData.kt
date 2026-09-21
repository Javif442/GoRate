package com.gorate.app.domain.model

data class TripData(
    val price: Double = 0.0,
    val distanceKm: Double = 0.0,
    val timeMin: Double = 0.0,
    val clientRating: Float? = null,
    val rawText: String? = null,
    val isDelivery: Boolean = false,
    val pickupLocation: String? = null,
    val dropoffLocation: String? = null
)
