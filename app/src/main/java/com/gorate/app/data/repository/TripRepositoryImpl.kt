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
            // Si llega una oferta con el mismo precio en menos de 15 segundos
            if ((currentTime - lastSavedTimestamp) < 15000 && kotlin.math.abs(result.price - lastSavedPrice) < 0.1) {
                // Verificamos si la nueva oferta perdió la distancia por un mal escaneo del OCR
                if (result.distanceKm <= 0.0 && lastSavedDistance > 0.0) {
                    return@withLock // Descartamos la lectura mala, mantenemos la buena en pantalla
                }
                // Si la distancia varió muy poco (ej. 4.8 km vs 4.7 km por redondeos de Uber)
                if (kotlin.math.abs(result.distanceKm - lastSavedDistance) < 1.0) {
                    return@withLock // Es la misma oferta, no hacemos parpadear la pantalla
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
