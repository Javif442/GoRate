package com.gorate.app.presentation.main

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gorate.app.data.repository.PreferencesRepository
import com.gorate.app.databinding.ActivitySubscriptionBinding
import com.google.firebase.auth.FirebaseAuth
import java.net.URLEncoder

class SubscriptionActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySubscriptionBinding
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubscriptionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = PreferencesRepository(this)

        binding.btnBackSub.setOnClickListener {
            finish()
        }

        if (prefs.isProUser()) {
            binding.btnSubscribeNow.text = "⭐ Membresía PRO Activa"
            binding.btnSubscribeNow.isEnabled = false
        } else if (prefs.isTrialActive()) {
            binding.btnSubscribeNow.text = "Activar Plan PRO ($0.99/mes)"
        }

        binding.btnSubscribeNow.setOnClickListener {
            val user = auth.currentUser
            if (user == null) {
                Toast.makeText(this, "Inicia sesión para continuar", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            MaterialAlertDialogBuilder(this)
                .setTitle("Suscripción GoRate PRO")
                .setMessage("La facturación automática mediante Google Play estará integrada en el lanzamiento oficial.\n\nPara activar tu suscripción anticipada o consultar métodos de pago directos, presiona 'Contactar Soporte'.")
                .setPositiveButton("Contactar Soporte") { _, _ ->
                    val email = user.email ?: "usuario"
                    val msg = "Hola, deseo activar mi suscripción GoRate PRO ($0.99/mes) para mi cuenta: $email"
                    try {
                        val encoded = URLEncoder.encode(msg, "UTF-8")
                        startActivity(Intent(Intent.ACTION_VIEW, "https://wa.me/593969609268?text=$encoded".toUri()))
                    } catch (e: Exception) {
                        Toast.makeText(this, "No se pudo abrir WhatsApp", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cerrar", null)
                .show()
        }
    }
}
