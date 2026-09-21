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
        private val RATING_REGEX = Regex("""(?:\b([3-4][.,]\d{1,2}|5[.,]0{1,2})\s*[*★]|[*★]\s*([3-4][.,]\d{1,2}|5[.,]0{1,2})\b)""")
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

        // 0. FILTRO DE AUTO-IGNORAR: Descartar si el texto proviene de GoRate mismo (Historial, Resumen, etc.)
        if (text.contains("GoRate", ignoreCase = true) ||
            text.contains("Historial", ignoreCase = true) ||
            text.contains("Total Ganado", ignoreCase = true) ||
            text.contains("Borrar Todo", ignoreCase = true) ||
            text.contains("Cumplen Metas", ignoreCase = true) ||
            text.contains("Smart Analyst", ignoreCase = true) ||
            text.contains("Calculadora", ignoreCase = true) ||
            text.contains("Periodo de prueba", ignoreCase = true)) {
            return TripData(0.0, 0.0, 0.0, null)
        }

        // 1. FIRMA OBLIGATORIA DE OFERTA: Debe contener términos característicos de Uber
        val hasUberSignature = text.contains("Uber", ignoreCase = true) ||
                               text.contains("Entrega", ignoreCase = true) ||
                               text.contains("Recoger", ignoreCase = true) ||
                               text.contains("Listo!", ignoreCase = true) ||
                               text.contains("Punto", ignoreCase = true) ||
                               text.contains("Radar", ignoreCase = true) ||
                               text.contains("Emparejar", ignoreCase = true) ||
                               text.contains("Flash", ignoreCase = true) ||
                               text.contains("Comfort", ignoreCase = true) ||
                               text.contains("Viaje", ignoreCase = true) ||
                               text.contains("Paquete", ignoreCase = true) ||
                               text.contains("Envío", ignoreCase = true) ||
                               text.contains("Pedido", ignoreCase = true) ||
                               text.contains("Restaurante", ignoreCase = true) ||
                               text.contains("Exclusivo", ignoreCase = true) ||
                               text.contains("Aceptar", ignoreCase = true)

        if (!hasUberSignature) {
            return TripData(0.0, 0.0, 0.0, null)
        }

        var price = extractPrice(text)
        val timeMin = extractTime(text)
        val distanceKm = extractDistance(text, timeMin)
        val rating = extractRating(text)

        // Heurística de seguridad para tarifas en USD (Ecuador, EE.UU., etc.):
        // Si el precio por km es exorbitante (> 6.0 $/km) y al dividirlo entre 100 da una tarifa
        // razonable (0.05 a 3.50 $/km), corregir división por 100 (ej: 288.0 para 20km -> 2.88 para 20km)
        val isUsdContext = text.contains("US$", ignoreCase = true) || text.contains("USD", ignoreCase = true) || text.contains("USS", ignoreCase = true)
        if ((isUsdContext || price < 1000.0) && distanceKm > 1.0) {
            val ratePerKm = price / distanceKm
            if (ratePerKm > 6.0) {
                val candidatePrice = price / 100.0
                val candidateRate = candidatePrice / distanceKm
                if (candidateRate in 0.05..3.5) {
                    price = candidatePrice
                }
            }
        }

        // 2. FILTRO ANTI-PARPADEO: Si no detecta dinero, es notificación de estado
        if (price == 0.0) {
            return TripData(0.0, 0.0, 0.0, null)
        }

        // 3. VALIDACIÓN DE RANGO REALISTA:
        // En dólares (USD) una tarifa individual nunca supera los $250.00.
        // Cifras exorbitantes como $56,415 o $169,246 provienen de resúmenes acumulados, no de viajes individuales.
        val maxRealisticPrice = if (isUsdContext || text.contains("$")) 250.0 else 500000.0
        if (price !in 0.50..maxRealisticPrice || (distanceKm <= 0.05 && timeMin <= 0.5)) {
            return TripData(0.0, 0.0, 0.0, null)
        }

        val (pickup, dropoff) = extractLocations(text)

        val isRadar = text.contains("Radar", ignoreCase = true) ||
                      text.contains("Emparejar", ignoreCase = true) ||
                      text.contains("Emparejando", ignoreCase = true) ||
                      text.contains("Match", ignoreCase = true)

        val isBatch = Regex("""\b\(?([2-9])\)?\s*(entregas?|pedidos?|paradas?|env[ií]os?|orden(?:es)?)\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) ||
                      text.contains("(2)", ignoreCase = true) ||
                      text.contains("(3)", ignoreCase = true) ||
                      text.contains("2 entregas", ignoreCase = true) ||
                      text.contains("3 entregas", ignoreCase = true) ||
                      text.contains("2 pedidos", ignoreCase = true) ||
                      text.contains("3 pedidos", ignoreCase = true)

        val isAdditional = text.contains("adicional", ignoreCase = true) ||
                           text.contains("adicionales", ignoreCase = true) ||
                           (text.contains("+") && (text.contains("km", ignoreCase = true) || text.contains("min", ignoreCase = true)))

        val isDelivery = isBatch ||
                         text.contains("Entrega", ignoreCase = true) ||
                         text.contains("Paquete", ignoreCase = true) ||
                         text.contains("Flash", ignoreCase = true) ||
                         text.contains("Envío", ignoreCase = true) ||
                         text.contains("Package", ignoreCase = true) ||
                         text.contains("Comida", ignoreCase = true) ||
                         text.contains("Pedido", ignoreCase = true) ||
                         text.contains("Restaurante", ignoreCase = true)

        val tripTag: String? = when {
            isRadar && isBatch -> "📡 RADAR • 📦 2 ENTREGAS"
            isRadar -> "📡 RADAR • EMPAREJAR"
            isBatch -> "📦 2 PEDIDOS EN LOTE"
            isAdditional -> "➕ PARADA ADICIONAL"
            else -> null
        }

        return TripData(
            price = price,
            distanceKm = distanceKm,
            timeMin = timeMin,
            clientRating = rating,
            rawText = text,
            isDelivery = isDelivery,
            pickupLocation = pickup,
            dropoffLocation = dropoff,
            isRadar = isRadar,
            tripTag = tripTag
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

        val isUsdContext = text.contains("US$", ignoreCase = true) || 
                           text.contains("USD", ignoreCase = true) || 
                           text.contains("USS", ignoreCase = true)

        val parsedPrices = matches.mapNotNull { match ->
            val rawValue = match.groupValues.getOrNull(1)?.ifEmpty { null } ?: match.groupValues.getOrNull(2) ?: ""
            val amount = parseCurrencyAmount(rawValue, isUsdContext)
            if (amount in 0.50..500000.0) amount else null
        }
        
        if (parsedPrices.isEmpty()) return 0.0

        // Si hay varios precios en la notificación, el mayor suele ser la tarifa total
        return parsedPrices.maxOrNull() ?: 0.0
    }

    private fun parseCurrencyAmount(rawStr: String, isUsdContext: Boolean = false): Double {
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

        // Si estamos en contexto de dólares (US$, USD) y el OCR leyó un entero de 3 o 4 dígitos
        // sin coma/punto (ej: "288" en vez de "2,88", "1250" en vez de "12,50")
        if (isUsdContext && clean.matches(Regex("""\d{3,4}"""))) {
            val intVal = clean.toDoubleOrNull() ?: 0.0
            return intVal / 100.0
        }

        // Formato Estándar con decimales (ej: 15.50 o 15,50)
        val sanitized = clean.replace(",", ".")
        return sanitized.toDoubleOrNull() ?: 0.0
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
        if (validDistances.isEmpty()) return 0.0
        
        // En Uber, la distancia total del viaje/entrega es siempre el valor mayor que aparece en la tarjeta.
        // Tomar siempre el máximo elimina por completo que el kilometraje salte o cambie entre frames
        // por sumar o ignorar la distancia de recogida (pickup).
        return validDistances.maxOrNull() ?: 0.0
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

        if (parsedTimes.isEmpty()) return 0.0
        val validTimes = parsedTimes.filter { it in 1.0..600.0 }
        if (validTimes.isEmpty()) return 0.0

        return validTimes.maxOrNull() ?: 0.0
    }

    private fun extractRating(text: String): Float? {
        val match = RATING_REGEX.find(text) ?: return null
        val ratingStr = match.groupValues.getOrNull(1)?.ifEmpty { null }
            ?: match.groupValues.getOrNull(2)
            ?: return null
        val ratingVal = ratingStr.replace(",", ".").toFloatOrNull() ?: return null
        return if (ratingVal in 3.0f..5.0f) ratingVal else null
    }
}
