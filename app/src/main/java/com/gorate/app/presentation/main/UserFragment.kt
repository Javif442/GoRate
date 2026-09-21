package com.gorate.app.presentation.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.gorate.app.GoRateApplication
import com.gorate.app.R
import com.gorate.app.data.repository.PreferencesRepository
import com.gorate.app.databinding.FragmentUserBinding

/**
 * Fragment responsible for user account settings, subscription management,
 * and application appearance customization.
 */
class UserFragment : Fragment() {

    private var _binding: FragmentUserBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by viewModels {
        val app = requireActivity().application as GoRateApplication
        MainViewModelFactory(PreferencesRepository(requireContext()), app.tripRepository)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateUserUi()
        initializeThemeSettings()
        attachEventHandlers()
    }

    private fun updateUserUi() {
        val prefs = PreferencesRepository(requireContext())
        if (viewModel.isAdminUser()) {
            binding.tvExpiryInfo.text = "⚡ Administrador • Pro Ilimitado"
        } else if (prefs.isProUser()) {
            binding.tvExpiryInfo.text = "⭐ Suscripción Pro Activa"
        } else {
            binding.tvExpiryInfo.text = "Tu prueba termina el ${viewModel.getTrialExpiryDate()}"
        }

        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user != null) {
            binding.tvUserEmail.text = user.email
            binding.tvLoginStatus.text = "Sesión Activa"
            binding.btnLogout.text = "Cerrar Sesión"
        } else {
            binding.tvUserEmail.text = "invitado@gorate.app"
            binding.tvLoginStatus.text = "Sesión no iniciada"
            binding.btnLogout.text = "Iniciar Sesión"
        }
    }

    private fun attachEventHandlers() {
        binding.apply {
            imgProfile.setOnClickListener {
                showImageZoom()
            }

            btnLogout.setOnClickListener {
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                if (auth.currentUser != null) {
                    auth.signOut()
                    Toast.makeText(requireContext(), "Sesión cerrada", Toast.LENGTH_SHORT).show()
                }
                startActivity(Intent(requireContext(), com.gorate.app.presentation.auth.AuthActivity::class.java))
            }

            btnManageSub.setOnClickListener {
                startActivity(Intent(requireContext(), SubscriptionActivity::class.java))
            }

            btnSupportWhatsapp.setOnClickListener {
                startActivity(Intent(requireContext(), SupportActivity::class.java))
            }

            btnHowItWorks.setOnClickListener {
                startActivity(Intent(requireContext(), PrivacyPolicyActivity::class.java))
            }

            btnPrivacy.setOnClickListener {
                startActivity(Intent(requireContext(), PrivacyPolicyActivity::class.java))
            }
        }
    }

    private fun initializeThemeSettings() {
        // Apariencia desactivada temporalmente por XML comentado
        /*
        val activeTheme = viewModel.getThemeMode()
        
        when (activeTheme) {
            1 -> binding.toggleTheme.check(R.id.btnThemeLight)
            2 -> binding.toggleTheme.check(R.id.btnThemeDark)
            else -> binding.toggleTheme.check(R.id.btnThemeAuto)
        }

        binding.toggleTheme.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val selectedMode = when (checkedId) {
                    R.id.btnThemeLight -> 1
                    R.id.btnThemeDark -> 2
                    else -> 0
                }
                
                if (selectedMode != viewModel.getThemeMode()) {
                    viewModel.setThemeMode(selectedMode)
                    updateAppTheme(selectedMode)
                }
            }
        }
        */
    }

    // private fun updateAppTheme(mode: Int) {
    //     val nightMode = when (mode) {
    //         1 -> AppCompatDelegate.MODE_NIGHT_NO
    //         2 -> AppCompatDelegate.MODE_NIGHT_YES
    //         else -> {
    //             val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    //             if (hour in 7..18) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
    //         }
    //     }
    //     AppCompatDelegate.setDefaultNightMode(nightMode)
    // }

    private fun showImageZoom() {
        val dialog = android.app.Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_image_zoom)
        dialog.findViewById<View>(R.id.btnCloseZoom).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        updateUserUi()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
