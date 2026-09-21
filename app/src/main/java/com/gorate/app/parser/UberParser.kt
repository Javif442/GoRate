package com.gorate.app.parser

import com.gorate.app.domain.model.TripData

/**
 * High-precision parsing engine for Uber Driver offers and notifications.
 * Extracts price, distance, duration, ratings, and locations.
 */
class UberParser {

    companion object {
        private var PRICE_PATTERN = """(?:\$|US\$|USD|COP)\s*(\d+[.,\d]*)|(\d+[.,\d]*)\s*(?:US\$|USD|USS|\$|COP)"""
        private var DISTANCE_PATTERN = """(\d+[.,\d]*)\s*(km|mi|millas|kil[oó]metros)\b"""
        private var TIME_PATTERN = """(\d+[.,\d]*)\s*(min|m|h|hora|horas|minutos)\b"""

        private var PRICE_REGEX = Regex(PRICE_PATTERN, RegexOption.IGNORE_CASE)
        private var DISTANCE_REGEX = Regex(DISTANCE_PATTERN, RegexOption.IGNORE_CASE)
        private var TIME_REGEX = Regex(TIME_PATTERN, RegexOption.IGNORE_CASE)
        private val RATING_REGEX = Regex("""\b([3-4][.,]\d{1,2}|5[.,]0{1,2})\b\s*[*★]?""")
    }

    /**
     * Permite actualizar los patrones desde Firebase Remote Config.
     * Valida que no sean vacíos para evitar excepciones Regex("").
     */
    fun updatePatterns(price: String?, distance: String?, time: String?) {
        if (!price.isNullOrBlank()) {
            PRICE_PATTERN = price
            PRICE_REGEX = Regex(price, RegexOption.IGNORE_CASE)
        }
        if (!distance.isNullOrBlank()) {
            DISTANCE_PATTERN = distance
            DISTANCE_REGEX = Regex(distance, RegexOption.IGNORE_CASE)
        }
        if (!time.isNullOrBlank()) {
            TIME_PATTERN = time
            TIME_REGEX = Regex(time, RegexOption.IGNORE_CASE)
        }
    }

    fun parseFromText(text: String): TripData {
        if (text.isBlank()) return TripData(0.0, 0.0, 0.0, null)

        val price = extractPrice(text)
        val timeMin = extractTime(text)
        val distanceKm = extractDistance(text, timeMin)
        val rating = extractRating(text)

        // 1. FILTRO ANTI-PARPADEO: Si no detecta dinero, es notificación de estado
        if (price == 0.0) {
            return TripData(0.0, 0.0, 0.0, null)
        }

        // 2. VALIDACIÓN DE RANGO: Soporta tarifas en USD/EUR y monedas en miles (COP, CLP)
        if (price !in 0.50..500000.00 || (distanceKm <= 0.05 && timeMin <= 0.5)) {
            return TripData(0.0, 0.0, 0.0, null)
        }

        val (pickup, dropoff) = extractLocations(text)
        val isDelivery = text.contains("Entrega", ignoreCase = true) ||
                         text.contains("Paquete", ignoreCase = true) ||
                         text.contains("Flash", ignoreCase = true) ||
                         text.contains("Envío", ignoreCase = true) ||
                         text.contains("Package", ignoreCase = true) ||
                         text.contains("Comida", ignoreCase = true)

        return TripData(
            price = price,
            distanceKm = distanceKm,
            timeMin = timeMin,
            clientRating = rating,
            rawText = text,
            isDelivery = isDelivery,
            pickupLocation = pickup,
            dropoffLocation = dropoff
        )
    }

    private fun extractLocations(text: String): Pair<String?, String?> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        var pickup: String? = null
        var dropoff: String? = null

        for (line in lines) {
            if (line.contains("Listo!", ignoreCase = true) || line.contains("Recoger", ignoreCase = true) || line.contains("Punto", ignoreCase = true)) {
                pickup = line
            } else if (line.contains("&") || line.contains(" y ", ignoreCase = true) || line.contains("Av.", ignoreCase = true) || line.contains("Calle", ignoreCase = true) || line.contains("Quito", ignoreCase = true)) {
                if (dropoff == null && line != pickup) {
                    dropoff = line
                }
            }
        }
        if (pickup == null && lines.isNotEmpty()) {
            pickup = lines.firstOrNull()
        }
        if (dropoff == null && lines.size > 1) {
            dropoff = lines.lastOrNull()
        }
        return Pair(pickup, dropoff)
    }

    private fun extractPrice(text: String): Double {
        val matches = PRICE_REGEX.findAll(text).toList()
        if (matches.isEmpty()) return 0.0

        val parsedPrices = matches.mapNotNull { match ->
            val rawValue = match.groupValues.getOrNull(1)?.ifEmpty { null } ?: match.groupValues.getOrNull(2) ?: ""
            val amount = parseCurrencyAmount(rawValue)
            if (amount in 0.50..500000.0) amount else null
        }
        
        if (parsedPrices.isEmpty()) return 0.0

        // Si hay varios precios en la notificación, el mayor suele ser la tarifa total
        return parsedPrices.maxOrNull() ?: 0.0
    }

    private fun parseCurrencyAmount(rawStr: String): Double {
        val clean = rawStr.trim().replace(" ", "")
        if (clean.isEmpty()) return 0.0

        // Formato COP / CLP con miles (ej: 15.000 o 120.000) sin punto decimal
        if (clean.matches(Regex("""\d{1,3}(\.\d{3})+"""))) {
            return clean.replace(".", "").toDoubleOrNull() ?: 0.0
        }
        // Formato COP / CLP con comas como miles (ej: 15,000 o 120,000) sin decimales
        if (clean.matches(Regex("""\d{1,3}(,\d{3})+"""))) {
            return clean.replace(",", "").toDoubleOrNull() ?: 0.0
        }
        // Formato Estándar con decimales (ej: 15.50 o 15,50)
        val sanitized = clean.replace(",", ".")
        return sanitized.toDoubleOrNull() ?: 0.0
    }

    private fun calculateTrueTotal(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        if (values.size == 1) return values.first()
        
        val max = values.maxOrNull() ?: 0.0
        val sum = values.sum()
        
        val sumOfOthers = sum - max
        if (kotlin.math.abs(max - sumOfOthers) < 0.5) {
            return max
        }
        return sum
    }

    private fun extractDistance(text: String, totalTimeMin: Double): Double {
        val matches = DISTANCE_REGEX.findAll(text).toList()
        if (matches.isEmpty()) return 0.0

        val parsedDistances = matches.mapNotNull { match ->
            val group1 = match.groupValues.getOrNull(1) ?: return@mapNotNull null
            val valueStr = group1.replace(" ", "").replace(",", ".")
            var rawValue = valueStr.toDoubleOrNull() ?: return@mapNotNull null
            val unitMatched = match.groupValues.getOrNull(2)?.lowercase() ?: ""
            if (unitMatched == "mi" || unitMatched == "millas") rawValue *= 1.609344

            // Heurística de corrección OCR: Si leyó "64" en vez de "6.4"
            if (totalTimeMin > 0 && rawValue >= 10.0 && !valueStr.contains(".") && !valueStr.contains(",")) {
                val speed = (rawValue / totalTimeMin) * 60.0 
                if (speed > 130.0) {
                    rawValue /= 10.0
                }
            }
            if (rawValue > 0.0) rawValue else null
        }

        if (parsedDistances.isEmpty()) return 0.0
        val validDistances = parsedDistances.filter { it in 0.2..200.0 }
        
        return calculateTrueTotal(validDistances)
    }

    private fun extractTime(text: String): Double {
        val matches = TIME_REGEX.findAll(text).toList()
        if (matches.isEmpty()) return 0.0

        val parsedTimes = matches.mapNotNull { match ->
            val group1 = match.groupValues.getOrNull(1) ?: return@mapNotNull null
            val valueStr = group1.replace(" ", "").replace(",", ".")
            var rawValue = valueStr.toDoubleOrNull() ?: return@mapNotNull null
            val unitMatched = match.groupValues.getOrNull(2)?.lowercase() ?: ""
            if (unitMatched == "h" || unitMatched == "hora" || unitMatched == "horas") {
                rawValue *= 60.0
            }
            if (rawValue > 0.0) rawValue else null
        }

        return calculateTrueTotal(parsedTimes)
    }

    private fun extractRating(text: String): Float? {
        val match = RATING_REGEX.find(text) ?: return null
        val ratingVal = match.groupValues.getOrNull(1)?.replace(",", ".")?.toFloatOrNull() ?: return null
        return if (ratingVal in 3.0f..5.0f) ratingVal else null
    }
}
