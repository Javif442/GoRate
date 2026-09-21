package com.gorate.app.presentation.main

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.google.firebase.auth.FirebaseAuth
import com.gorate.app.databinding.ActivitySupportBinding
import java.net.URLEncoder

class SupportActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySupportBinding
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySupportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val categories = arrayOf(
            "No puedo iniciar sesión / Registro",
            "Problemas con el escáner OCR / Viajes",
            "Suscripción PRO / Pagos",
            "Otro inconveniente"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        binding.spinnerIssueCategory.adapter = adapter

        binding.btnSendSupport.setOnClickListener {
            val message = binding.etSupportMessage.text.toString().trim()
            if (message.length < 5) {
                Toast.makeText(this, "Por favor, describe con un poco más de detalle el problema.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val user = auth.currentUser
            val email = user?.email ?: "invitado@gorate.app"
            val category = binding.spinnerIssueCategory.selectedItem.toString()

            val fullText = "🚨 *NUEVO REPORTE DE SOPORTE - GoRate*\n\n" +
                    "📌 *Categoría:* $category\n" +
                    "💬 *Mensaje:* $message\n" +
                    "📧 *Usuario:* $email"

            try {
                val encodedMessage = URLEncoder.encode(fullText, "UTF-8")
                val whatsappUrl = "https://wa.me/593969609268?text=$encodedMessage"
                val intent = Intent(Intent.ACTION_VIEW, whatsappUrl.toUri())

                Toast.makeText(this, "¡Reporte enviado! En un momento nos comunicaremos con usted.", Toast.LENGTH_LONG).show()
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Toast.makeText(this, "Error al abrir WhatsApp: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
