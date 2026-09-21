package com.gorate.app.presentation.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.gorate.app.data.repository.PreferencesRepository
import com.gorate.app.databinding.ActivityAuthBinding
import com.gorate.app.presentation.main.MainActivity

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateUi()

        binding.btnAuthAction.setOnClickListener {
            handleAuth()
        }

        binding.tvToggleAuth.setOnClickListener {
            isLoginMode = !isLoginMode
            updateUi()
        }

        binding.tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }
    }

    private fun updateUi() {
        if (isLoginMode) {
            binding.tvAuthTitle.text = "Bienvenido a GoRate"
            binding.tvAuthSubtitle.text = "Inicia sesión para continuar"
            binding.btnAuthAction.text = "Iniciar Sesión"
            binding.tvToggleAuth.text = "¿No tienes cuenta? Regístrate"
            binding.layoutFirstName.visibility = View.GONE
            binding.layoutLastName.visibility = View.GONE
            binding.layoutDriverMode.visibility = View.GONE
            binding.tvForgotPassword.visibility = View.VISIBLE
        } else {
            binding.tvAuthTitle.text = "Crear Cuenta Profesional"
            binding.tvAuthSubtitle.text = "Únete a la red oficial de conductores GoRate"
            binding.btnAuthAction.text = "Registrarse"
            binding.tvToggleAuth.text = "¿Ya tienes cuenta? Inicia sesión"
            binding.layoutFirstName.visibility = View.VISIBLE
            binding.layoutLastName.visibility = View.VISIBLE
            binding.layoutDriverMode.visibility = View.VISIBLE
            binding.tvForgotPassword.visibility = View.GONE
        }
    }

    private fun showForgotPasswordDialog() {
        val prefilledEmail = binding.etEmail.text.toString().trim()
        val input = EditText(this).apply {
            hint = "Correo electrónico registrado"
            setText(prefilledEmail)
            setPadding(48, 40, 48, 40)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Recuperar Contraseña")
            .setMessage("Ingrese su correo electrónico registrado. Le enviaremos un enlace seguro para restablecer su contraseña.")
            .setView(input)
            .setPositiveButton("Enviar enlace") { _, _ ->
                val email = input.text.toString().trim()
                if (email.isNotEmpty()) {
                    auth.sendPasswordResetEmail(email)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                showPasswordResetSuccessDialog(email)
                            } else {
                                Toast.makeText(this, getFriendlyAuthError(task.exception), Toast.LENGTH_LONG).show()
                            }
                        }
                } else {
                    Toast.makeText(this, "Por favor, ingrese un correo electrónico válido.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showPasswordResetSuccessDialog(email: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("📧 Correo Enviado con Éxito")
            .setMessage("Hemos enviado las instrucciones para restablecer su contraseña a:\n\n$email\n\nPor favor, revise su bandeja de entrada o la carpeta de spam. Si no lo encuentra, puede presionar 'Reenviar Correo'.")
            .setPositiveButton("Aceptar", null)
            .setNeutralButton("Reenviar Correo") { _, _ ->
                auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "¡Correo de recuperación reenviado con éxito!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, getFriendlyAuthError(task.exception), Toast.LENGTH_LONG).show()
                    }
                }
            }
            .show()
    }

    private fun isValidStrongPassword(password: String): Boolean {
        val passwordRegex = Regex("^(?=.*[A-Z])(?=.*\\d)(?=.*[@\$!%*?&])[A-Za-z\\d@\$!%*?&]{8,}$")
        return passwordRegex.matches(password)
    }

    private fun getFriendlyAuthError(e: Exception?): String {
        val message = e?.message ?: ""
        return when {
            message.contains("email address is already in use", ignoreCase = true) -> 
                "Este correo ya se encuentra registrado. Por favor, inicie sesión o recupere su contraseña."
            message.contains("password is invalid", ignoreCase = true) || message.contains("auth/wrong-password", ignoreCase = true) -> 
                "Contraseña incorrecta. Por favor, verifique sus datos."
            message.contains("no user record", ignoreCase = true) || message.contains("auth/user-not-found", ignoreCase = true) -> 
                "No existe una cuenta registrada con este correo electrónico."
            message.contains("network error", ignoreCase = true) -> 
                "Error de conexión. Por favor, verifique su acceso a internet."
            else -> "Error en la autenticación. Por favor, verifique sus datos e intente nuevamente."
        }
    }

    private fun handleAuth() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Por favor, complete todos los campos obligatorios.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isLoginMode) {
            if (!isValidStrongPassword(password)) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Contraseña Poco Segura")
                    .setMessage("Por seguridad, la contraseña debe tener al menos 8 caracteres, incluir una letra mayúscula, un número y un carácter especial (ej: @, $, !, %, *, ?, &).")
                    .setPositiveButton("Entendido", null)
                    .show()
                return
            }
        }

        if (isLoginMode) {
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val prefs = PreferencesRepository(this)
                        prefs.setUserEmail(email)
                        val authCreated = auth.currentUser?.metadata?.creationTimestamp ?: 0L
                        if (authCreated > 0L) {
                            prefs.syncInstallTimestamp(authCreated)
                        }
                        val uid = auth.currentUser?.uid
                        if (uid != null) {
                            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                                if (doc.exists()) {
                                    val isPro = doc.getBoolean("isPro") == true
                                    prefs.setProUser(isPro)
                                    val mode = doc.getString("driverMode")
                                    if (!mode.isNullOrBlank()) {
                                        prefs.setDriverMode(mode)
                                    }
                                    val firestoreCreatedAt = doc.getLong("createdAt") ?: 0L
                                    if (firestoreCreatedAt > 0L) {
                                        prefs.syncInstallTimestamp(firestoreCreatedAt)
                                    }
                                }
                            }
                        }
                        Toast.makeText(this, "¡Bienvenido de nuevo!", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, getFriendlyAuthError(task.exception), Toast.LENGTH_LONG).show()
                    }
                }
        } else {
            val firstName = binding.etFirstName.text.toString().trim()
            val lastName = binding.etLastName.text.toString().trim()
            val selectedMode = if (binding.radioEntregas.isChecked) "ENTREGA" else "CHOFER"

            if (firstName.isEmpty() || lastName.isEmpty()) {
                Toast.makeText(this, "Por favor, ingrese sus nombres y apellidos.", Toast.LENGTH_SHORT).show()
                return
            }

            val now = System.currentTimeMillis()
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        user?.let {
                            it.sendEmailVerification()
                            val userMap = mapOf(
                                "uid" to it.uid,
                                "email" to email,
                                "firstName" to firstName,
                                "lastName" to lastName,
                                "driverMode" to selectedMode,
                                "createdAt" to now,
                                "isPro" to false
                            )
                            db.collection("users").document(it.uid).set(userMap)
                        }

                        val prefs = PreferencesRepository(this)
                        prefs.setUserEmail(email)
                        prefs.setDriverMode(selectedMode)
                        prefs.setProUser(false)
                        prefs.syncInstallTimestamp(now)

                        Toast.makeText(this, "¡Cuenta creada con éxito! Se ha enviado un correo de verificación.", Toast.LENGTH_LONG).show()
                        val intent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                    } else {
                        val errorMsg = task.exception?.message ?: ""
                        if (errorMsg.contains("email address is already in use", ignoreCase = true)) {
                            MaterialAlertDialogBuilder(this)
                                .setTitle("Cuenta ya Existente")
                                .setMessage("Este correo electrónico ya está registrado. ¿Desea iniciar sesión o recuperar su contraseña?")
                                .setPositiveButton("Iniciar Sesión") { _, _ ->
                                    isLoginMode = true
                                    updateUi()
                                }
                                .setNeutralButton("Recuperar") { _, _ ->
                                    showForgotPasswordDialog()
                                }
                                .setNegativeButton("Cancelar", null)
                                .show()
                        } else {
                            Toast.makeText(this, getFriendlyAuthError(task.exception), Toast.LENGTH_LONG).show()
                        }
                    }
                }
        }
    }
}
