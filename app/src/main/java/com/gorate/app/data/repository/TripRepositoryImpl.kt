package com.gorate.app.data.repository

import android.content.Context
import com.gorate.app.data.local.AppDatabase
import com.gorate.app.data.local.TripHistoryEntity
import com.gorate.app.domain.model.ProfitResult
import com.gorate.app.domain.repository.TripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

class TripRepositoryImpl(context: Context) : TripRepository {
    val prefsRepository = PreferencesRepository(context)
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.tripHistoryDao()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                dao.purgeInvalidTrips()
            } catch (_: Exception) {}
        }
    }

    private val _currentTrip = MutableStateFlow<ProfitResult?>(null)
    override val currentTrip: StateFlow<ProfitResult?> = _currentTrip.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    override val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    override fun setServiceRunningState(running: Boolean) {
        _isServiceRunning.value = running
    }

    override fun clearCurrentTrip() {
        _currentTrip.value = null
    }

    private var lastSavedTripKey = ""
    private var lastSavedTimestamp = 0L
    private var lastSavedDistance = 0.0
    private var lastSavedPrice = 0.0
    private var lastSavedTimeMin = 0.0
    private val saveMutex = Mutex()

    override suspend fun emitTrip(result: ProfitResult?, origin: String) {
        if (result == null) {
            _currentTrip.emit(null)
            return
        }

        // Bloqueo de seguridad para evitar duplicados y estabilizar la UI
        saveMutex.withLock {
            val currentTime = System.currentTimeMillis()

            // 1. FILTRO ANTI-PARPADEO (Stabilizer)
            // Si llega una oferta en menos de 15 segundos
            if ((currentTime - lastSavedTimestamp) < 15000) {
                // Caso A: Si es el mismo viaje con distancia y tiempo casi idénticos
                val sameDistance = kotlin.math.abs(result.distanceKm - lastSavedDistance) < 0.5
                val sameTime = kotlin.math.abs(result.timeMin - lastSavedTimeMin) < 1.0

                if (sameDistance && sameTime && lastSavedPrice > 0.0 && result.price > 0.0) {
                    // Detección de glitch de 100x (ej. $2.88 vs $288.00 por omisión de coma en OCR)
                    val ratio = result.price / lastSavedPrice
                    val is100xGlitch = (ratio in 80.0..120.0) || (1.0 / ratio in 80.0..120.0)

                    if (is100xGlitch) {
                        // Si la nueva lectura es el spike gigante (288.0) y ya teníamos la tarifa normal (2.88), descartamos el spike
                        if (result.price > lastSavedPrice) {
                            return@withLock
                        }
                        // Si la que teníamos guardada era el spike (288.0) y ahora llegó la correcta (2.88),
                        // dejamos pasar la corrección para estabilizar la pantalla en el valor real.
                    } else if (kotlin.math.abs(result.price - lastSavedPrice) < 0.1) {
                        // Mismo precio exacto y misma distancia/tiempo: no hacer parpadear la UI
                        return@withLock
                    }
                }

                // Caso B: Mismo precio pero la nueva perdió la distancia por un mal escaneo del OCR
                if (kotlin.math.abs(result.price - lastSavedPrice) < 0.1) {
                    if (result.distanceKm <= 0.0 && lastSavedDistance > 0.0) {
                        return@withLock // Descartamos la lectura mala, mantenemos la buena en pantalla
                    }
                    if (kotlin.math.abs(result.distanceKm - lastSavedDistance) < 1.0) {
                        return@withLock // Es la misma oferta, no hacemos parpadear la pantalla
                    }
                }
            }

            // 2. FILTRO DE INTEGRIDAD: Soporta tarifas estándar y monedas en miles (COP, CLP)
            if (result.price < 0.50 || result.price > 500000.00) {
                return@withLock
            }
            if (result.distanceKm <= 0.0 && result.timeMin <= 0.0) {
                return@withLock
            }

            // 3. ACTUALIZAR MEMORIA CENTRAL
            lastSavedTripKey = String.format(Locale.US, "%.2f-%.2f", result.price, result.distanceKm)
            lastSavedTimestamp = currentTime
            lastSavedDistance = result.distanceKm
            lastSavedPrice = result.price
            lastSavedTimeMin = result.timeMin

            // 4. EMITIR A LA PANTALLA
            _currentTrip.emit(result)

            // 5. GUARDAR EN BASE DE DATOS
            val entity = TripHistoryEntity(
                timestamp = currentTime,
                price = result.price,
                distanceKm = result.distanceKm,
                timeMin = result.timeMin,
                perKm = result.perKm,
                perHour = result.perHour,
                clientRating = result.clientRating,
                appOrigin = origin,
                confidence = "ALTA",
                netEarnings = result.netEarnings,
                pickupLocation = result.pickupLocation,
                dropoffLocation = result.dropoffLocation
            )
            dao.insertTrip(entity)
        }
    }

    fun getAllHistory() = dao.getAllTrips()

    suspend fun clearHistory() {
        dao.clearHistory()
    }

    suspend fun deleteTrip(tripId: Long) {
        dao.deleteTrip(tripId)
    }

    suspend fun deleteTrips(tripIds: List<Long>) {
        dao.deleteTrips(tripIds)
    }

    suspend fun updateNote(tripId: Long, note: String) {
        dao.updateTripNote(tripId, note)
    }
}
