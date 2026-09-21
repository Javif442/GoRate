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

            // 1. FILTRO ANTI-PARPADEO Y ESTABILIZADOR DE KILOMETRAJE
            // Si llega una lectura dentro de los 15 segundos
            if ((currentTime - lastSavedTimestamp) < 15000) {
                // Caso A: Glitch de 100x en precio (ej. $2.88 vs $288.00 por omisión de coma en OCR)
                if (lastSavedPrice > 0.0 && result.price > 0.0) {
                    val ratio = result.price / lastSavedPrice
                    val is100xGlitch = (ratio in 80.0..120.0) || (1.0 / ratio in 80.0..120.0)
                    if (is100xGlitch) {
                        if (result.price > lastSavedPrice) {
                            return@withLock // Descartamos el spike erróneo de 288.0
                        }
                    }
                }

                // Caso B: MISMA OFERTA EN PANTALLA (Mismo precio) -> Estabilización absoluta de KM
                if (kotlin.math.abs(result.price - lastSavedPrice) < 0.1) {
                    if (lastSavedDistance > 0.0) {
                        // 1. Si la nueva lectura perdió la distancia o leyó 0: descartar
                        if (result.distanceKm <= 0.0) {
                            return@withLock
                        }
                        // 2. Si la nueva lectura leyó solo la recogida (ej. 1.5 km en vez de 20.0 km total): descartar
                        if (result.distanceKm < lastSavedDistance * 0.85) {
                            return@withLock
                        }
                        // 3. Si la distancia es coherente con la actual (variación menor a 3.0 km o menor al 25%):
                        // Mantener firme el kilometraje ya mostrado, bloqueando cualquier parpadeo en pantalla
                        val diffDist = kotlin.math.abs(result.distanceKm - lastSavedDistance)
                        if (diffDist < 3.0 || (diffDist / lastSavedDistance) < 0.25) {
                            return@withLock
                        }
                    }
                }
            }

            // 2. FILTRO DE INTEGRIDAD: Soporta tarifas estándar y monedas en miles (COP, CLP), descartando resúmenes acumulados
            if (result.price < 0.50 || result.price > 500000.00 || (result.price > 250.0 && result.perKm > 20.0)) {
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
                note = result.tripTag,
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
