package com.gorate.app.presentation.admin

import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gorate.app.R
import com.google.firebase.firestore.FirebaseFirestore

class AdminDashboardActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val email = currentUser?.email ?: ""
        if (!email.equals("admin@gorate.app", ignoreCase = true)) {
            Toast.makeText(this, "Acceso no autorizado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContentView(R.layout.activity_admin)

        findViewById<android.view.View>(R.id.btnBackAdmin).setOnClickListener { finish() }

        val tvStats = findViewById<TextView>(R.id.tvAdminStats)
        val etTargetEmail = findViewById<EditText>(R.id.etTargetEmail)
        val btnGrantPro = findViewById<android.view.View>(R.id.btnGrantPro)
        val btnRevokePro = findViewById<android.view.View>(R.id.btnRevokePro)
        val btnSyncPatterns = findViewById<android.view.View>(R.id.btnSyncPatterns)

        // Cargar estadísticas globales y listado completo de Firestore
        loadSystemStats(tvStats)

        btnGrantPro.setOnClickListener {
            val email = etTargetEmail.text.toString().trim()
            if (email.isNotEmpty()) {
                setUserProStatus(email, true)
            } else {
                Toast.makeText(this, "Ingresa un correo válido", Toast.LENGTH_SHORT).show()
            }
        }

        btnRevokePro.setOnClickListener {
            val email = etTargetEmail.text.toString().trim()
            if (email.isNotEmpty()) {
                setUserProStatus(email, false)
            } else {
                Toast.makeText(this, "Ingresa un correo válido", Toast.LENGTH_SHORT).show()
            }
        }

        btnSyncPatterns.setOnClickListener {
            syncRemotePatterns()
        }
    }

    private fun loadSystemStats(tvStats: TextView) {
        db.collection("users").get()
            .addOnSuccessListener { documents ->
                val totalUsers = documents.size()
                val proUsers = documents.documents.count { it.getBoolean("isPro") == true }

                val sb = StringBuilder()
                sb.append("• Conductores Registrados: $totalUsers\n")
                sb.append("• Suscriptores PRO Activos: $proUsers\n")
                sb.append("• Estado Cloud Firestore: Conectado (Sincronizado)\n\n")
                sb.append("📋 LISTADO DE CONDUCTORES REGISTRADOS:\n")
                sb.append("────────────────────────\n")

                if (documents.isEmpty) {
                    sb.append("(No hay usuarios registrados aún)\n")
                } else {
                    for (doc in documents) {
                        val email = doc.getString("email") ?: "Sin correo"
                        val name = "${doc.getString("firstName") ?: ""} ${doc.getString("lastName") ?: ""}".trim()
                        val mode = doc.getString("driverMode") ?: "CHOFER"
                        val isPro = doc.getBoolean("isPro") == true
                        val proBadge = if (isPro) "⭐ [PRO]" else "⏳ [PRUEBA]"
                        sb.append("• $email\n  👤 $name | 🚗 $mode | $proBadge\n\n")
                    }
                }

                tvStats.text = sb.toString()
            }
            .addOnFailureListener {
                tvStats.text = "• Estado Cloud Firestore: Error de conexión (${it.message})"
            }
    }

    private fun setUserProStatus(email: String, isPro: Boolean) {
        db.collection("users").whereEqualTo("email", email).get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    for (doc in querySnapshot.documents) {
                        doc.reference.update("isPro", isPro, "updatedAt", com.google.firebase.Timestamp.now())
                    }
                    val statusText = if (isPro) "⭐ Otorgado Pro a $email" else "⏳ Suscripción Pro revocada a $email"
                    Toast.makeText(this, statusText, Toast.LENGTH_LONG).show()
                    loadSystemStats(findViewById(R.id.tvAdminStats))
                } else {
                    db.collection("users").document(email)
                        .set(mapOf("email" to email, "isPro" to isPro, "updatedAt" to com.google.firebase.Timestamp.now()), com.google.firebase.firestore.SetOptions.merge())
                        .addOnSuccessListener {
                            val statusText = if (isPro) "⭐ Otorgado Pro a $email" else "⏳ Suscripción Pro revocada a $email"
                            Toast.makeText(this, statusText, Toast.LENGTH_LONG).show()
                            loadSystemStats(findViewById(R.id.tvAdminStats))
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun syncRemotePatterns() {
        val configMap = mapOf(
            "lastSync" to com.google.firebase.Timestamp.now(),
            "status" to "active"
        )
        db.collection("settings").document("ocr_patterns")
            .set(configMap, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "¡Patrones OCR sincronizados con Firestore!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al sincronizar: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
