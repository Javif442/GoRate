package com.gorate.app.accessibility

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.gorate.app.GoRateApplication
import com.gorate.app.domain.usecase.CalculateProfitUseCase
import com.gorate.app.parser.UberParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 100% Reliable Notification Listener Service.
 * Intercepts system push notifications from Uber, DiDi, InDrive, and PedidosYa the exact millisecond they arrive.
 */
class GoRateNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "GoRateNotifListener"
        var instance: GoRateNotificationListener? = null
            private set
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val calculateProfitUseCase = CalculateProfitUseCase()
    
    private val uberParser = UberParser()

    private var lastTripKey = ""
    private var lastTripTime = 0L

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "GoRateNotificationListener created.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: return
        if (packageName == "com.gorate.app") return
        val extras = sbn.notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val combinedText = buildString {
            if (title.isNotBlank()) appendLine(title)
            if (text.isNotBlank()) appendLine(text)
            if (bigText.isNotBlank() && bigText != text) appendLine(bigText)
        }.trim()
        if (combinedText.isBlank()) return

        // SOLO UBER: Filtramos cualquier notificación que no sea de Uber
        if (!packageName.contains("uber", ignoreCase = true)) return

        Log.d(TAG, "Notification from $packageName: $combinedText")

        val tripData = parseAnyText(combinedText)
        if (tripData.price > 0 && (tripData.distanceKm > 0 || tripData.timeMin > 0)) {
            val currentKey = "${tripData.price}-${tripData.distanceKm}"
            val currentTime = System.currentTimeMillis()

            if (currentKey != lastTripKey || (currentTime - lastTripTime) > 10000) {
                lastTripKey = currentKey
                lastTripTime = currentTime

                serviceScope.launch(Dispatchers.Default) {
                    val app = application as? GoRateApplication ?: return@launch
                    val prefs = app.tripRepository.prefsRepository
                    val fuelCost = prefs.getFuelCostPerKm()
                    val result = calculateProfitUseCase(tripData, fuelCost)

                    val origin = if (tripData.isDelivery) "Uber Entrega" else "Uber Chofer"

                    launch(Dispatchers.Main) {
                        app.tripRepository.emitTrip(result, origin)
                    }
                }
            }
        }
    }

    private fun parseAnyText(text: String): com.gorate.app.domain.model.TripData {
        // SOLO UBER: Se usa exclusivamente el UberParser
        return uberParser.parseFromText(text)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
        serviceScope.cancel()
    }
}
