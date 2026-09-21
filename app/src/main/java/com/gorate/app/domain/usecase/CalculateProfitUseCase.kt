package com.gorate.app.domain.usecase

import com.gorate.app.domain.model.ProfitResult
import com.gorate.app.domain.model.TripData
import kotlin.math.roundToInt

class CalculateProfitUseCase {
    operator fun invoke(data: TripData, fuelCostPerKm: Double = 0.0): ProfitResult {
        val price = roundTo2Decimals(data.price)
        val dist = roundTo2Decimals(data.distanceKm)
        val time = data.timeMin

        val perKm = if (dist > 0) roundTo2Decimals(price / dist) else 0.0
        val perHour = if (time > 0) roundTo2Decimals((price / time) * 60.0) else 0.0
        
        val expenses = dist * fuelCostPerKm
        val netEarnings = roundTo2Decimals(price - expenses)

        return ProfitResult(
            perKm = perKm,
            perHour = perHour,
            price = price,
            distanceKm = dist,
            timeMin = time,
            clientRating = data.clientRating,
            netEarnings = netEarnings,
            pickupLocation = data.pickupLocation,
            dropoffLocation = data.dropoffLocation
        )
    }

    private fun roundTo2Decimals(value: Double): Double {
        return (value * 100.0).roundToInt() / 100.0
    }
}
