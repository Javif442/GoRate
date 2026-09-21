package com.gorate.app.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ImageReader
import android.media.ToneGenerator
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.gorate.app.GoRateApplication
import com.gorate.app.R
import com.gorate.app.data.repository.PreferencesRepository
import com.gorate.app.data.repository.TripRepositoryImpl
import com.gorate.app.domain.usecase.CalculateProfitUseCase
import com.gorate.app.parser.UberParser
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Robust MediaProjection Screen Capture OCR Engine.
 * Captures screen frames in real time and analyzes them via ML Kit OCR.
 */
class OverlayService : Service() {

    companion object {
        const val ACTION_STOP = "com.gorate.app.ACTION_STOP"
        private const val NOTIFICATION_ID = 1001
        private const val HEADS_UP_NOTIFICATION_ID = 2002
        private const val PICO_PLACA_NOTIFICATION_ID = 4004
        private const val CHANNEL_ID = "com.gorate.app.ocr_service_channel"
        private const val HEADS_UP_CHANNEL_ID = "com.gorate.app.heads_up_channel"
        private const val PICO_PLACA_CHANNEL_ID = "com.gorate.app.pico_placa_channel"
        private const val TAG = "OCR_ScannerService"
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private lateinit var prefsRepository: PreferencesRepository
    private lateinit var tripRepository: TripRepositoryImpl
    private val mainHandler = Handler(Looper.getMainLooper())
    private val calculateProfitUseCase = CalculateProfitUseCase()

    private val uberParser = UberParser()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught error in OverlayService", throwable)
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + exceptionHandler)
    private var wakeLock: PowerManager.WakeLock? = null

    private var projectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private var currentState = ScannerState.INACTIVE
    private var lastEmittedData: String = ""
    private var lastEmittedTimestamp: Long = 0L
    private var missedScanCount = 0
    private var isAnalysisInProgress = false
    private var lastSuccessfulSyncTime = 0L

    private var toneGenerator: ToneGenerator? = null
    private val dismissTask = Runnable { hideOverlay() }

    enum class ScannerState {
        INACTIVE, STARTING, ACTIVE, RECOVERING, ERROR, REAUTH_REQUIRED
    }

    enum class AlertLevel { ACCEPT, CONSIDER, REJECT }

    private val monitorTask = object : Runnable {
        override fun run() {
            if (currentState == ScannerState.ACTIVE && !isAnalysisInProgress) {
                performSync()
            }
            val delay = if (prefsRepository.isTurboModeEnabled()) 300L else 1200L
            mainHandler.postDelayed(this, delay)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefsRepository = PreferencesRepository(this)
        tripRepository = (application as GoRateApplication).tripRepository
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // Inicio limpio sin tarjetas viejas ni elementos flotantes en reposo
        serviceScope.launch { tripRepository.emitTrip(null, "") }

        transitionTo(ScannerState.STARTING)

        lastSuccessfulSyncTime = System.currentTimeMillis()
        mainHandler.post(monitorTask)

        tripRepository.currentTrip
            .onEach { result ->
                result?.let {
                    updateUI(it.price, it.distanceKm, it.timeMin, it.perKm, it.perHour, it.clientRating, it.netEarnings, it.tripTag)
                }
            }
            .launchIn(serviceScope)

        val plate = prefsRepository.getVehiclePlate()
        val isPicoEnabled = prefsRepository.isPicoPlacaEnabled()
        val picoStatus = com.gorate.app.data.util.PicoPlacaChecker.checkPlate(plate, isPicoEnabled)
        if (picoStatus.isRestricted) {
            sendPicoPlacaNotification(picoStatus.message)
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GoRate::ScannerWakeLock").apply {
            try { acquire(30 * 60 * 1000L) } catch (e: Exception) {}
        }

        Log.i(TAG, "OCR Scanner Service initialized.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
            return START_NOT_STICKY
        }

        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("projection_data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("projection_data")
        }

        if (data != null) {
            setupMediaProjection(data)
        } else if (mediaProjection == null) {
            transitionTo(ScannerState.INACTIVE)
            stopSelf()
            return START_NOT_STICKY
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(HEADS_UP_NOTIFICATION_ID)
            nm.cancel(PICO_PLACA_NOTIFICATION_ID)
        } catch (_: Exception) {}

        tripRepository.setServiceRunningState(false)
        tripRepository.clearCurrentTrip()
        serviceScope.cancel()
        mainHandler.removeCallbacks(monitorTask)
        mainHandler.removeCallbacks(dismissTask)

        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {}

        try {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
        } catch (e: Exception) {}

        releaseResources()
        if (overlayView != null) {
            try { windowManager.removeView(overlayView) } catch (e: Exception) {}
        }
        super.onDestroy()
    }

    private fun setupMediaProjection(data: Intent) {
        if (mediaProjection != null) {
            releaseResources()
        }
        try {
            mediaProjection = projectionManager?.getMediaProjection(android.app.Activity.RESULT_OK, data)
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    mainHandler.post {
                        transitionTo(ScannerState.INACTIVE)
                        stopSelf()
                    }
                }
            }, mainHandler)

            setupCaptureResources()
            transitionTo(ScannerState.ACTIVE)
            lastSuccessfulSyncTime = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up MediaProjection", e)
            transitionTo(ScannerState.ERROR)
        }
    }

    private fun setupCaptureResources() {
        if (mediaProjection == null) return
        releaseCaptureResources()

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        try {
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "MirrorDisplay", width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Resource error", e)
            transitionTo(ScannerState.ERROR)
        }
    }

    private fun releaseCaptureResources() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
        } catch (e: Exception) {}
    }

    private fun releaseResources() {
        releaseCaptureResources()
        try { mediaProjection?.stop() } catch (e: Exception) {}
        mediaProjection = null
    }

    private fun transitionTo(newState: ScannerState) {
        if (currentState == newState) return
        currentState = newState
        updateNotification()
        val isRunning = (newState == ScannerState.ACTIVE || newState == ScannerState.STARTING || newState == ScannerState.RECOVERING)
        tripRepository.setServiceRunningState(isRunning)
        if (newState == ScannerState.REAUTH_REQUIRED || newState == ScannerState.ERROR || newState == ScannerState.INACTIVE) {
            tripRepository.setServiceRunningState(false)
            releaseResources()
        }
    }

    private fun performSync() {
        val reader = imageReader ?: return
        val image = try {
            reader.acquireLatestImage()
        } catch (e: Exception) {
            null
        } ?: return

        isAnalysisInProgress = true
        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val width = image.width
            val height = image.height

            val finalBitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
            finalBitmap.copyPixelsFromBuffer(buffer)

            analyzeContent(finalBitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Frame sync error", e)
            isAnalysisInProgress = false
        } finally {
            image.close()
        }
    }

    private fun analyzeContent(bitmap: Bitmap) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                lastSuccessfulSyncTime = System.currentTimeMillis()
                val rawText = visionText.text
                if (rawText.isNotBlank()) {
                    // Detección de pérdida o expiración de oferta de Radar/Emparejar
                    val isOfferLost = rawText.contains("emparejó con otro", ignoreCase = true) ||
                                      rawText.contains("emparejo con otro", ignoreCase = true) ||
                                      rawText.contains("otro conductor", ignoreCase = true) ||
                                      rawText.contains("no está disponible", ignoreCase = true) ||
                                      rawText.contains("no esta disponible", ignoreCase = true) ||
                                      rawText.contains("agotó el tiempo", ignoreCase = true) ||
                                      rawText.contains("agoto el tiempo", ignoreCase = true) ||
                                      rawText.contains("expiró", ignoreCase = true) ||
                                      rawText.contains("expiro", ignoreCase = true)
                    if (isOfferLost) {
                        mainHandler.post { hideOverlay() }
                        return@addOnSuccessListener
                    }

                    val tripData = parseAnyText(rawText)
                    if (tripData.price > 0 && (tripData.distanceKm > 0 || tripData.timeMin > 0)) {
                        missedScanCount = 0
                        val dataKey = String.format(Locale.US, "%.2f-%.1f-%d", tripData.price, tripData.distanceKm, tripData.timeMin.toInt())
                        val currentTime = System.currentTimeMillis()
                        if (dataKey != lastEmittedData) {
                            lastEmittedData = dataKey
                            lastEmittedTimestamp = currentTime
                            val fuelCost = prefsRepository.getFuelCostPerKm()
                            val result = calculateProfitUseCase(tripData, fuelCost)
                            val driverMode = prefsRepository.getDriverMode()
                            val origin = when (driverMode) {
                                "CHOFER" -> if (tripData.isRadar) "Uber Radar Chofer" else "Uber Chofer"
                                "ENTREGA" -> if (tripData.isRadar) "Uber Radar Entrega" else "Uber Entrega"
                                else -> when {
                                    tripData.isRadar && tripData.isDelivery -> "Uber Radar Entrega"
                                    tripData.isRadar -> "Uber Radar Chofer"
                                    tripData.isDelivery -> "Uber Entrega"
                                    else -> "Uber Chofer"
                                }
                            }
                            serviceScope.launch { tripRepository.emitTrip(result, origin) }
                        }
                    } else {
                        checkAutoDismiss()
                    }
                } else {
                    checkAutoDismiss()
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "ML Kit processing failed", it)
            }
            .addOnCompleteListener {
                bitmap.recycle()
                isAnalysisInProgress = false
            }
    }

    private fun parseAnyText(text: String): com.gorate.app.domain.model.TripData {
        // SOLO UBER: Se usa exclusivamente el UberParser
        return uberParser.parseFromText(text)
    }

    private fun checkAutoDismiss() {
        if (overlayView != null) {
            missedScanCount++
            if (missedScanCount >= 2) {
                lastEmittedData = ""
            }
            if (missedScanCount >= 10) {
                mainHandler.post { hideOverlay() }
            }
        }
    }

    private fun showOverlay() {
        if (!prefsRepository.isOverlayBubbleEnabled()) {
            if (overlayView != null) {
                try { windowManager.removeView(overlayView) } catch (e: Exception) {}
                overlayView = null
            }
            return
        }
        if (overlayView != null) return
        try {
            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = prefsRepository.getOverlayX()
                y = prefsRepository.getOverlayY()
                alpha = 1.0f
            }

            overlayView = LayoutInflater.from(ContextThemeWrapper(this, R.style.Theme_GoRate)).inflate(R.layout.overlay_card, null)

            overlayView?.findViewById<View>(R.id.btnCloseOverlay)?.setOnClickListener {
                hideOverlay()
            }

            overlayView?.findViewById<View>(R.id.bubbleContainer)?.setOnClickListener {
                val cardContainer = overlayView?.findViewById<View>(R.id.cardContainer)
                val bubbleContainer = overlayView?.findViewById<View>(R.id.bubbleContainer)
                if (cardContainer?.visibility == View.VISIBLE) {
                    cardContainer.visibility = View.GONE
                    bubbleContainer?.visibility = View.VISIBLE
                    overlayView?.alpha = 0.5f
                } else {
                    bubbleContainer?.visibility = View.GONE
                    cardContainer?.visibility = View.VISIBLE
                    overlayView?.alpha = 1.0f
                }
            }

            makeOverlayDraggable(overlayView, params)

            overlayView?.findViewById<View>(R.id.cardContainer)?.visibility = View.GONE
            overlayView?.findViewById<View>(R.id.bubbleContainer)?.visibility = View.VISIBLE

            windowManager.addView(overlayView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Overlay creation error", e)
        }
    }

    private fun makeOverlayDraggable(view: View?, params: WindowManager.LayoutParams) {
        val touchSlop = android.view.ViewConfiguration.get(this).scaledTouchSlop
        view?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (!isDragging && (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)) {
                            isDragging = true
                        }
                        if (isDragging) {
                            params.x = initialX + dx
                            params.y = initialY + dy
                            try {
                                windowManager.updateViewLayout(overlayView, params)
                            } catch (e: Exception) { }
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isDragging) {
                            prefsRepository.setOverlayX(params.x)
                            prefsRepository.setOverlayY(params.y)
                            return true
                        }
                    }
                }
                return false
            }
        })
    }

    private fun updateUI(
        price: Double,
        distKm: Double,
        timeMin: Double,
        perKm: Double,
        perHour: Double,
        rating: Float?,
        netEarnings: Double,
        tripTag: String? = null
    ) {
        mainHandler.removeCallbacks(dismissTask)

        val isMiles = prefsRepository.isMilesEnabled()
        val isMinutes = prefsRepository.isMinutesEnabled()
        val isBubbleEnabled = prefsRepository.isOverlayBubbleEnabled()

        val distanceUnitLabel = if (isMiles) "mi" else "km"
        val displayDistance = if (isMiles) distKm / 1.60934 else distKm
        val displayDistanceRate = if (isMiles) perKm * 1.60934 else perKm

        val displayTimeRate = if (isMinutes) {
            if (timeMin > 0) price / timeMin else 0.0
        } else {
            perHour
        }

        val minKm = prefsRepository.getMinPerKm()
        val excKm = prefsRepository.getExcPerKm()
        val minHour = prefsRepository.getMinPerHour()
        val excHour = prefsRepository.getExcPerHour()

        val recTitle = when {
            displayDistanceRate >= excKm -> getString(R.string.rec_accept)
            displayDistanceRate >= minKm -> getString(R.string.rec_consider)
            else -> getString(R.string.rec_reject)
        }

        val alertLevel: AlertLevel = when {
            displayDistanceRate >= excKm -> AlertLevel.ACCEPT
            displayDistanceRate >= minKm -> AlertLevel.CONSIDER
            else -> AlertLevel.REJECT
        }

        val colorRes = when (alertLevel) {
            AlertLevel.ACCEPT -> R.color.status_green
            AlertLevel.CONSIDER -> R.color.status_yellow
            AlertLevel.REJECT -> R.color.status_red
        }

        val timeText = if (timeMin > 0) " • ${timeMin.toInt()} min" else ""
        val tagPrefix = if (!tripTag.isNullOrBlank()) "[$tripTag] " else ""
        val notifTitle = "$tagPrefix${String.format(Locale.US, "$%.2f", price)} • $recTitle"
        val notifContent = "${String.format(Locale.US, "%.2f", displayDistanceRate)} $/$distanceUnitLabel • ${String.format(Locale.US, "%.2f", displayDistance)} $distanceUnitLabel$timeText"

        if (!isBubbleEnabled) {
            updateNotification(notifTitle, notifContent)
            sendHeadsUpNotification(notifTitle, notifContent, colorRes)
        } else {
            try {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(HEADS_UP_NOTIFICATION_ID)
            } catch (_: Exception) {}
        }

        mainHandler.post {
            if (isBubbleEnabled) {
                showOverlay()
                overlayView?.let { view ->
                    val cardContainer = view.findViewById<View>(R.id.cardContainer)
                    val isNewOffer = cardContainer?.visibility != View.VISIBLE

                    val tvTripTag = view.findViewById<TextView>(R.id.tvTripTag)
                    if (!tripTag.isNullOrBlank()) {
                        tvTripTag?.visibility = View.VISIBLE
                        tvTripTag?.text = tripTag
                    } else {
                        tvTripTag?.visibility = View.GONE
                    }

                    view.findViewById<TextView>(R.id.tvPerKm)?.text = String.format(Locale.US, "%.2f", displayDistanceRate)
                    view.findViewById<TextView>(R.id.tvPerHour)?.text = String.format(Locale.US, "%.2f", displayTimeRate)

                    view.findViewById<TextView>(R.id.tvNetEarnings)?.text = String.format(Locale.US, "$%.2f", price)
                    view.findViewById<TextView>(R.id.tvTripSubInfo)?.text = String.format(Locale.US, "%.1f %s", displayDistance, distanceUnitLabel)
                    view.findViewById<TextView>(R.id.tvTripMainInfo)?.text = if (timeMin > 0) "${timeMin.toInt()}min" else ""

                    val tvRecommendation = view.findViewById<TextView>(R.id.tvRecommendation)
                    tvRecommendation?.text = recTitle
                    tvRecommendation?.setTextColor(ContextCompat.getColor(this@OverlayService, android.R.color.white))
                    tvRecommendation?.background = ContextCompat.getDrawable(this@OverlayService, R.drawable.card_rounded)?.mutate()?.apply {
                        setTint(ContextCompat.getColor(this@OverlayService, colorRes))
                    }

                    if (isNewOffer) {
                        triggerVibrationAndSound(alertLevel)
                        wakeUpScreen()
                    }

                    val containerRating = view.findViewById<View>(R.id.containerRating)
                    if (rating != null && rating >= 3.0f && rating <= 5.0f) {
                        containerRating?.visibility = View.VISIBLE
                        view.findViewById<TextView>(R.id.tvClientRating)?.text = String.format(Locale.US, "%.1f ★", rating)
                    } else {
                        containerRating?.visibility = View.GONE
                    }

                    view.findViewById<View>(R.id.barPerKm)?.setBackgroundColor(getSemaphorColorValue(displayDistanceRate, minKm, excKm))
                    view.findViewById<View>(R.id.barPerHour)?.setBackgroundColor(getSemaphorColorValue(displayTimeRate, minHour, excHour))

                    view.findViewById<View>(R.id.bubbleContainer)?.visibility = View.GONE
                    view.findViewById<View>(R.id.cardContainer)?.visibility = View.VISIBLE
                    // Reducimos la opacidad para que la tarjeta sea más transparente (80% opaca, 20% transparente)
                    overlayView?.alpha = 0.80f
                }
            } else {
                if (overlayView != null) {
                    try { windowManager.removeView(overlayView) } catch (e: Exception) {}
                    overlayView = null
                }
                triggerVibrationAndSound(alertLevel)
                wakeUpScreen()
            }
        }
        mainHandler.postDelayed(dismissTask, 12000)
    }

    private fun getSemaphorColorValue(value: Double, minTarget: Double, excTarget: Double): Int {
        return when {
            value >= excTarget -> ContextCompat.getColor(this, R.color.status_green)
            value >= minTarget -> ContextCompat.getColor(this, R.color.status_yellow)
            else -> ContextCompat.getColor(this, R.color.status_red)
        }
    }

    private fun hideOverlay() {
        lastEmittedData = ""
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(HEADS_UP_NOTIFICATION_ID)
        } catch (_: Exception) {}
        updateNotification("GoRate: Escaneando", "Buscando ofertas de viaje...")

        if (overlayView != null) {
            try {
                windowManager.removeView(overlayView)
            } catch (e: Exception) {}
            overlayView = null
        }
    }

    private fun wakeUpScreen() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            // Si la pantalla está apagada, la encendemos forzosamente
            if (!pm.isInteractive) {
                @Suppress("DEPRECATION")
                val wl = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "GoRate::TripWakeUp"
                )
                wl.acquire(5000L) // Mantiene la pantalla encendida por 5 segundos
            }
        } catch (e: Exception) {}
    }

    private fun triggerVibrationAndSound(level: AlertLevel) {
        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            }
            toneGenerator?.let { tg ->
                when (level) {
                    AlertLevel.ACCEPT -> tg.startTone(ToneGenerator.TONE_DTMF_1, 100)
                    AlertLevel.CONSIDER -> tg.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                    AlertLevel.REJECT -> tg.startTone(ToneGenerator.TONE_PROP_NACK, 250)
                }
            }
        } catch (e: Exception) {}

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()

                when (level) {
                    AlertLevel.ACCEPT -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 60, 80), -1), audioAttributes)
                        } else {
                            @Suppress("DEPRECATION") vibrator.vibrate(200)
                        }
                    }
                    AlertLevel.CONSIDER -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE), audioAttributes)
                        } else {
                            @Suppress("DEPRECATION") vibrator.vibrate(100)
                        }
                    }
                    AlertLevel.REJECT -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 100, 50), -1), audioAttributes)
                        } else {
                            @Suppress("DEPRECATION") vibrator.vibrate(300)
                        }
                    }
                }
            }
        } catch (e: Exception) {}
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            val channelService = NotificationChannel(CHANNEL_ID, "GoRate Escáner OCR", NotificationManager.IMPORTANCE_LOW)
            val channelHeadsUp = NotificationChannel(HEADS_UP_CHANNEL_ID, "Alertas Flotantes", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
            }
            val channelPico = NotificationChannel(PICO_PLACA_CHANNEL_ID, "Pico y Placa", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
            }
            nm.createNotificationChannels(listOf(channelService, channelHeadsUp, channelPico))
        }
    }

    private fun updateNotification(customTitle: String? = null, customContent: String? = null) {
        val intent = Intent(this, com.gorate.app.presentation.main.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent: PendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(customTitle ?: "GoRate: Escaneando")
            .setContentText(customContent ?: "Buscando ofertas de viaje...")
            .setStyle(NotificationCompat.BigTextStyle().bigText(customContent ?: "Buscando ofertas de viaje..."))
            .setSmallIcon(R.drawable.ic_notification_stat)
            .setColor(ContextCompat.getColor(this, R.color.brand_indigo))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.notification_stop), stopPendingIntent)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                }
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Foreground service start error", e)
        }
    }

    private fun sendHeadsUpNotification(title: String, content: String, colorResId: Int = R.color.brand_indigo) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val intent = Intent(this, com.gorate.app.presentation.main.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, System.currentTimeMillis().toInt(), intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, HEADS_UP_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setSmallIcon(R.drawable.ic_notification_stat)
            .setColor(ContextCompat.getColor(this, colorResId))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(HEADS_UP_NOTIFICATION_ID, notification)
    }

    private fun sendPicoPlacaNotification(content: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val intent = Intent(this, com.gorate.app.presentation.main.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, System.currentTimeMillis().toInt(), intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, PICO_PLACA_CHANNEL_ID)
            .setContentTitle("🚨 Pico y Placa")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setSmallIcon(R.drawable.ic_notification_stat)
            .setColor(ContextCompat.getColor(this, R.color.brand_indigo))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(PICO_PLACA_NOTIFICATION_ID, notification)
    }
}
