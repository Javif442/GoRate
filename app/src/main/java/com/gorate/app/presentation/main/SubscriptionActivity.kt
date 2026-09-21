package com.gorate.app.presentation.main

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gorate.app.data.billing.BillingManager
import com.gorate.app.data.repository.PreferencesRepository
import com.gorate.app.databinding.ActivitySubscriptionBinding
import com.google.firebase.auth.FirebaseAuth

class SubscriptionActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySubscriptionBinding
    private val auth = FirebaseAuth.getInstance()
    private lateinit var billingManager: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubscriptionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = PreferencesRepository(this)

        binding.btnBackSub.setOnClickListener {
            finish()
        }

        updateUiState(prefs.isProUser(), prefs.isTrialActive())

        // Inicialización oficial de Google Play Billing
        billingManager = BillingManager(
            context = this,
            onPriceLoaded = { formattedPrice ->
                runOnUiThread {
                    binding.tvPriceAmount.text = formattedPrice
                    binding.tvPriceSubtitle.text = "por mes • Facturación segura con Google Play"
                }
            },
            onPurchaseSuccess = {
                runOnUiThread {
                    Toast.makeText(this, "¡Felicitaciones! GoRate PRO activado con éxito.", Toast.LENGTH_LONG).show()
                    updateUiState(isPro = true, isTrial = false)
                }
            },
            onPurchaseError = { errorMsg ->
                runOnUiThread {
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                }
            }
        )

        binding.btnSubscribeNow.setOnClickListener {
            val user = auth.currentUser
            if (user == null) {
                Toast.makeText(this, "Inicia sesión con tu cuenta antes de suscribirte.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Iniciar flujo oficial de compra en Google Play
            val started = billingManager.launchBillingFlow(this)
            if (!started) {
                billingManager.querySubscriptionDetails()
            }
        }

        binding.btnRestorePurchases.setOnClickListener {
            Toast.makeText(this, "Verificando suscripciones con Google Play...", Toast.LENGTH_SHORT).show()
            billingManager.restorePurchases { success, message ->
                runOnUiThread {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(if (success) "Suscripción Restaurada" else "Estado de Suscripción")
                        .setMessage(message)
                        .setPositiveButton("Aceptar") { _, _ ->
                            if (success) {
                                updateUiState(isPro = true, isTrial = false)
                            }
                        }
                        .show()
                }
            }
        }
    }

    private fun updateUiState(isPro: Boolean, isTrial: Boolean) {
        if (isPro) {
            binding.btnSubscribeNow.text = "⭐ Membresía PRO Activa"
            binding.btnSubscribeNow.isEnabled = false
            binding.btnSubscribeNow.alpha = 0.8f
        } else if (isTrial) {
            binding.btnSubscribeNow.text = "Activar Plan PRO Mensual"
            binding.btnSubscribeNow.isEnabled = true
            binding.btnSubscribeNow.alpha = 1.0f
        } else {
            binding.btnSubscribeNow.text = "Suscribirme Ahora"
            binding.btnSubscribeNow.isEnabled = true
            binding.btnSubscribeNow.alpha = 1.0f
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        billingManager.onDestroy()
    }
}
