package com.gorate.app.presentation.main

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gorate.app.GoRateApplication
import com.gorate.app.R
import com.gorate.app.data.repository.PreferencesRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gorate.app.databinding.BottomSheetLimitsBinding
import com.gorate.app.databinding.BottomSheetPermissionsBinding
import com.gorate.app.databinding.FragmentMainBinding
import com.gorate.app.overlay.OverlayService
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main dashboard for the GoRate scanning service.
 * Handles overlay activation, turbo mode settings, and permission management.
 */
class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by viewModels {
        val app = requireActivity().application as GoRateApplication
        MainViewModelFactory(PreferencesRepository(requireContext()), app.tripRepository)
    }

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            _binding?.tvRealTimeClock?.text = dateFormat.format(Date())
            clockHandler.postDelayed(this, 1000)
        }
    }

    private val postNotifLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            openNotificationSettings()
        }
    }

    private val screenCaptureLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK && result.data != null) {
            launchOverlayService(result.data)
        } else {
            binding.switchService.isChecked = false
            terminateOverlayService()
            Toast.makeText(requireContext(), getString(R.string.permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchOverlayService(resultData: Intent?) {
        val intent = Intent(requireContext(), OverlayService::class.java).apply {
            putExtra("projection_data", resultData)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(intent)
        } else {
            requireContext().startService(intent)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeInterface()
        setupStateObservation()
    }

    override fun onResume() {
        super.onResume()
        clockHandler.post(clockRunnable)
        updatePicoPlacaStatus()
        updateSubscriptionBadge()
    }

    override fun onPause() {
        super.onPause()
        clockHandler.removeCallbacks(clockRunnable)
    }

    private fun updateSubscriptionBadge() {
        binding.apply {
            val statusMsg = viewModel.getTrialStatusMessage()
            tvTrialExpiry.text = statusMsg

            if (viewModel.isAdminUser()) {
                btnAdmin.text = "Panel Administrador"
                tvTrialExpiry.setBackgroundColor(android.graphics.Color.parseColor("#1A000000"))
                tvTrialExpiry.setTextColor(android.graphics.Color.parseColor("#A7F3D0"))
                tvTrialExpiry.setOnClickListener(null)
            } else if (viewModel.isProUser()) {
                btnAdmin.text = "⭐ Miembro PRO"
                tvTrialExpiry.setBackgroundColor(android.graphics.Color.parseColor("#1A000000"))
                tvTrialExpiry.setTextColor(android.graphics.Color.parseColor("#A7F3D0"))
                tvTrialExpiry.setOnClickListener(null)
            } else if (viewModel.isTrialExpired()) {
                btnAdmin.text = "⭐ Suscribirme a PRO"
                tvTrialExpiry.setBackgroundColor(android.graphics.Color.parseColor("#DC2626"))
                tvTrialExpiry.setTextColor(android.graphics.Color.WHITE)
                tvTrialExpiry.isClickable = true
                tvTrialExpiry.setOnClickListener {
                    showTrialExpiredDialog()
                }
            } else {
                btnAdmin.text = "Ver Planes y Suscripción"
                tvTrialExpiry.setBackgroundColor(android.graphics.Color.parseColor("#1A000000"))
                tvTrialExpiry.setTextColor(android.graphics.Color.parseColor("#E0E7FF"))
                tvTrialExpiry.setOnClickListener {
                    startActivity(Intent(requireContext(), SubscriptionActivity::class.java))
                }
            }
        }
    }

    private fun showTrialExpiredDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("⛔ Prueba Gratuita Finalizada")
            .setMessage("Tu mes de prueba gratis (30 días) ha concluido.\n\nPara seguir calculando la rentabilidad de tus viajes y analizando tarifas en tiempo real, activa tu suscripción a GoRate PRO.")
            .setCancelable(true)
            .setPositiveButton("Suscribirme Ahora") { _, _ ->
                startActivity(Intent(requireContext(), SubscriptionActivity::class.java))
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun initializeInterface() {
        binding.apply {
            updateSubscriptionBadge()
            
            updatePicoPlacaStatus()
            
            when (viewModel.getDriverMode()) {
                "CHOFER" -> toggleWorkMode.check(R.id.btnModeDriver)
                "ENTREGA" -> toggleWorkMode.check(R.id.btnModeDelivery)
                else -> toggleWorkMode.check(R.id.btnModeAuto)
            }

            toggleWorkMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    val mode = when (checkedId) {
                        R.id.btnModeDriver -> "CHOFER"
                        R.id.btnModeDelivery -> "ENTREGA"
                        else -> "AUTO"
                    }
                    viewModel.setDriverMode(mode)
                }
            }

            switchTurbo.isChecked = viewModel.isTurboModeEnabled()
            switchTurbo.setOnCheckedChangeListener { _, isChecked ->
                viewModel.setTurboModeEnabled(isChecked)
                if (viewModel.isServiceActive.value) {
                    radarScanner.startRadar(isChecked)
                }
            }

            // El switch siempre inicia en OFF al abrir la app para mayor control del usuario
            switchService.isChecked = false

            switchService.setOnCheckedChangeListener { switchView, isChecked ->
                // SEGURO ANTI-BUCLES: Solo responde si el usuario tocó físicamente el interruptor
                if (!switchView.isPressed) return@setOnCheckedChangeListener

                if (isChecked) {
                    if (!viewModel.canUseService()) {
                        switchService.isChecked = false
                        showTrialExpiredDialog()
                        return@setOnCheckedChangeListener
                    }

                    if (!isOverlayPermissionGranted()) {
                        switchService.isChecked = false
                        requestOverlayPermission()
                        Toast.makeText(requireContext(), "Activa el permiso de Superposición (Mostrar encima de otras apps)", Toast.LENGTH_LONG).show()
                        return@setOnCheckedChangeListener
                    }

                    val projectionManager = requireContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
                } else {
                    terminateOverlayService()
                }
            }

            btnAdmin.setOnClickListener { 
                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                if (user?.email.equals("admin@gorate.app", ignoreCase = true)) {
                    startActivity(Intent(requireContext(), com.gorate.app.presentation.admin.AdminDashboardActivity::class.java))
                } else {
                    startActivity(Intent(requireContext(), SubscriptionActivity::class.java))
                }
            }
            btnPermissionsCard.setOnClickListener { displayPermissionsSheet() }
            btnLimits.setOnClickListener { displayLimitsSheet() }
            btnHistory.setOnClickListener {
                startActivity(Intent(requireContext(), com.gorate.app.presentation.history.HistoryActivity::class.java))
            }
            btnAppSettings.setOnClickListener {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }
                startActivity(intent)
            }
            btnHowItWorks.setOnClickListener { startActivity(Intent(requireContext(), PrivacyPolicyActivity::class.java)) }
        }
    }

    private fun setupStateObservation() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isServiceActive.collect { isActive ->
                        binding.switchService.isChecked = isActive
                        if (isActive) {
                            binding.tvStatusTitle.text = getString(R.string.status_title_active)
                            binding.tvStatusDesc.text = getString(R.string.status_desc_active)
                            binding.radarScanner.startRadar(viewModel.isTurboModeEnabled())
                        } else {
                            binding.tvStatusTitle.text = getString(R.string.status_title_inactive)
                            binding.tvStatusDesc.text = getString(R.string.status_desc_inactive)
                            binding.radarScanner.stopRadar()
                        }
                    }
                }
                // Mostrar mensaje de bienvenida personalizado
                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val userName = user?.email?.split("@")?.get(0) ?: "Usuario"
                binding.tvWelcomeUser.text = "¡Hola, $userName!"
            }
        }
    }

    private fun isOverlayPermissionGranted(): Boolean {
        return Settings.canDrawOverlays(requireContext())
    }



    private fun displayPermissionsSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = BottomSheetPermissionsBinding.inflate(layoutInflater)
        val handler = Handler(Looper.getMainLooper())
        
        val updateTask = object : Runnable {
            override fun run() {
                if (!dialog.isShowing) return
                
                val isOverlayOk = isOverlayPermissionGranted()
                val isNotificationsOk = areNotificationsEnabled()
                val isNotifListenerOk = isNotificationListenerGranted()
                val isBatteryOk = isBatteryOptimizationsIgnored()

                sheetBinding.cardCheckOverlay.visibility = if (isOverlayOk) View.VISIBLE else View.INVISIBLE
                sheetBinding.cardCheckNotifications.visibility = if (isNotificationsOk) View.VISIBLE else View.INVISIBLE
                sheetBinding.cardCheckNotifListener.visibility = if (isNotifListenerOk) View.VISIBLE else View.INVISIBLE
                sheetBinding.cardCheckBattery.visibility = if (isBatteryOk) View.VISIBLE else View.INVISIBLE
                
                handler.postDelayed(this, 1000)
            }
        }

        sheetBinding.apply {
            itemOverlay.setOnClickListener { requestOverlayPermission() }
            itemNotifications.setOnClickListener { requestPostNotifications() }
            itemNotifListener.setOnClickListener { openNotificationListenerSettings() }
            itemBattery.setOnClickListener { requestIgnoreBatteryOptimizations() }
            btnContinue.setOnClickListener { 
                if (isOverlayPermissionGranted() && areNotificationsEnabled()) {
                    dialog.dismiss() 
                } else {
                    Toast.makeText(requireContext(), "Activa 'Superposición' y 'Notificaciones' para continuar", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.setOnShowListener { handler.post(updateTask) }
        dialog.setOnDismissListener { handler.removeCallbacksAndMessages(null) }
        
        dialog.setContentView(sheetBinding.root)
        dialog.show()
    }

    private fun areNotificationsEnabled(): Boolean {
        return NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
    }

    private fun isNotificationListenerGranted(): Boolean {
        val flat = Settings.Secure.getString(requireContext().contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(requireContext().packageName)
    }

    private fun openNotificationListenerSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No se pudo abrir Ajustes de acceso a notificaciones", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestPostNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            postNotifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings()
        }
    }

    private fun isBatteryOptimizationsIgnored(): Boolean {
        val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(requireContext().packageName)
    }

    private fun openNotificationSettings() {
        val intent = Intent().apply {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                }
                else -> {
                    action = "android.settings.APP_NOTIFICATION_SETTINGS"
                    putExtra("app_package", requireContext().packageName)
                    putExtra("app_uid", requireContext().applicationInfo.uid)
                }
            }
        }
        startActivity(intent)
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                // Intentar abrir el diálogo de sistema directo (requiere el permiso en el Manifest)
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${requireContext().packageName}")
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Si el fabricante bloquea el diálogo directo, ir a la lista general
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                } catch (ex: Exception) {
                    Toast.makeText(requireContext(), "Por favor, busca GoRate en Ajustes > Batería y cámbialo a 'Sin restricciones'", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${requireContext().packageName}"))
            startActivity(intent)
        }
    }

    private fun terminateOverlayService() {
        val intent = Intent(requireContext(), OverlayService::class.java)
        requireContext().stopService(intent)
    }

    private fun displayLimitsSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = BottomSheetLimitsBinding.inflate(layoutInflater)

        val minKmValue = viewModel.getMinPerKm()
        val excKmValue = viewModel.getExcPerKm()
        val minHourValue = viewModel.getMinPerHour()
        val excHourValue = viewModel.getExcPerHour()

        sheetBinding.apply {
            etMinKm.setText(String.format(Locale.US, "%.2f", minKmValue))
            etExcKm.setText(String.format(Locale.US, "%.2f", excKmValue))
            etMinHour.setText(String.format(Locale.US, "%.2f", minHourValue))
            etExcHour.setText(String.format(Locale.US, "%.2f", excHourValue))
            etFuelCost.setText(String.format(Locale.US, "%.2f", viewModel.getFuelCostPerKm()))

            // Lógica de calculadora rápida
            val watcher = object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val spend = etDailySpend.text.toString().toDoubleOrNull() ?: 0.0
                    val km = etDailyKm.text.toString().toDoubleOrNull() ?: 1.0
                    if (km > 0) {
                        val result = spend / km
                        etFuelCost.setText(String.format(Locale.US, "%.2f", result))
                    }
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            }
            etDailySpend.addTextChangedListener(watcher)
            etDailyKm.addTextChangedListener(watcher)

            if (viewModel.isMilesEnabled()) {
                toggleDistanceUnit.check(R.id.btnMiles)
            } else {
                toggleDistanceUnit.check(R.id.btnKm)
            }

            if (viewModel.isMinutesEnabled()) {
                toggleTimeUnit.check(R.id.btnMinutes)
            } else {
                toggleTimeUnit.check(R.id.btnHours)
            }

            // Estado inicial de interruptores y campos
            switchNetEarnings.isChecked = viewModel.isNetEarningsEnabled()
            switchOverlayBubble.isChecked = viewModel.isOverlayBubbleEnabled()
            switchPicoPlaca.isChecked = viewModel.isPicoPlacaEnabled()
            etVehiclePlate.setText(viewModel.getVehiclePlate())
            updateExpensesVisibility(sheetBinding, viewModel.isNetEarningsEnabled())

            switchNetEarnings.setOnCheckedChangeListener { _, isChecked ->
                updateExpensesVisibility(sheetBinding, isChecked)
            }

            btnSave.setOnClickListener {
                val minKm = etMinKm.text.toString().toDoubleOrNull() ?: MainViewModel.DEFAULT_MIN_PER_KM
                val excKm = etExcKm.text.toString().toDoubleOrNull() ?: (minKm * 1.5)
                val minHour = etMinHour.text.toString().toDoubleOrNull() ?: MainViewModel.DEFAULT_MIN_PER_HOUR
                val excHour = etExcHour.text.toString().toDoubleOrNull() ?: (minHour * 1.5)
                val fuelCost = etFuelCost.text.toString().toDoubleOrNull() ?: 0.05
                val netEnabled = switchNetEarnings.isChecked
                val bubbleEnabled = switchOverlayBubble.isChecked
                val picoPlacaEnabled = switchPicoPlaca.isChecked
                val vehiclePlate = etVehiclePlate.text.toString().trim()

                val isMiles = toggleDistanceUnit.checkedButtonId == R.id.btnMiles
                val isMinutes = toggleTimeUnit.checkedButtonId == R.id.btnMinutes

                viewModel.setMilesEnabled(isMiles)
                viewModel.setMinutesEnabled(isMinutes)
                viewModel.saveLimits(minKm, excKm, minHour, excHour)
                viewModel.saveFuelCost(fuelCost)
                viewModel.setNetEarningsEnabled(netEnabled)
                viewModel.setOverlayBubbleEnabled(bubbleEnabled)
                viewModel.setPicoPlacaEnabled(picoPlacaEnabled)
                viewModel.setVehiclePlate(vehiclePlate)

                Toast.makeText(requireContext(), getString(R.string.limits_saved), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.setContentView(sheetBinding.root)
        dialog.show()
    }

    private fun updateExpensesVisibility(binding: BottomSheetLimitsBinding, isEnabled: Boolean) {
        val visibility = if (isEnabled) View.VISIBLE else View.GONE
        binding.apply {
            tvFuelCostTitle.visibility = visibility
            layoutFuelCost.visibility = visibility
            tvQuickCalcTitle.visibility = visibility
            layoutQuickCalc.visibility = visibility
            tvExpensesDesc.visibility = visibility
        }
    }

    private fun updatePicoPlacaStatus() {
        val plate = viewModel.getVehiclePlate()
        val isEnabled = viewModel.isPicoPlacaEnabled()
        val status = com.gorate.app.data.util.PicoPlacaChecker.checkPlate(plate, isEnabled)

        if (isEnabled && plate.isNotBlank()) {
            binding.cardPicoPlacaStatus.visibility = View.VISIBLE
            binding.tvPicoPlacaStatus.text = status.message
            val textColor = if (status.isRestricted) {
                ContextCompat.getColor(requireContext(), R.color.status_red)
            } else {
                ContextCompat.getColor(requireContext(), R.color.status_green)
            }
            binding.tvPicoPlacaStatus.setTextColor(textColor)
        } else {
            binding.cardPicoPlacaStatus.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
