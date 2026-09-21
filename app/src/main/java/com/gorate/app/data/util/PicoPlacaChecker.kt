package com.gorate.app.data.util

import java.util.Calendar
import java.util.Locale

object PicoPlacaChecker {

    data class Status(
        val isRestricted: Boolean,
        val message: String
    )

    fun checkPlate(plate: String, enabled: Boolean): Status {
        if (!enabled || plate.isBlank()) return Status(false, "")

        val cleanPlate = plate.trim().uppercase(Locale.getDefault())
        val lastChar = cleanPlate.lastOrNull { it.isDigit() } ?: return Status(false, "")
        val lastDigit = lastChar.toString().toIntOrNull() ?: return Status(false, "")

        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val totalMinutes = hour * 60 + minute

        // Fines de semana libre circulación
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            return Status(false, "Fin de semana: Libre circulación")
        }

        // Horarios: 06:00 (360 min) a 09:30 (570 min) | 16:00 (960 min) a 21:00 (1260 min)
        val isMorningRestricted = totalMinutes in 360..570
        val isEveningRestricted = totalMinutes in 960..1260

        if (!isMorningRestricted && !isEveningRestricted) {
            return Status(false, "Fuera de horario de restricción")
        }

        // Dígitos por día: Lunes(1,2), Martes(3,4), Miércoles(5,6), Jueves(7,8), Viernes(9,0)
        val restrictedDigits = when (dayOfWeek) {
            Calendar.MONDAY -> listOf(1, 2)
            Calendar.TUESDAY -> listOf(3, 4)
            Calendar.WEDNESDAY -> listOf(5, 6)
            Calendar.THURSDAY -> listOf(7, 8)
            Calendar.FRIDAY -> listOf(9, 0)
            else -> emptyList()
        }

        val restricted = restrictedDigits.contains(lastDigit)
        if (restricted) {
            return Status(true, "⚠️ ¡PICO Y PLACA ACTIVO! Placa $cleanPlate (Dígito $lastDigit)")
        }

        return Status(false, "Placa $cleanPlate circula hoy sin restricción a esta hora")
    }
}
