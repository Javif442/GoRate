package com.gorate.app.presentation.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.gorate.app.R
import com.gorate.app.data.service.VersionController
import com.gorate.app.presentation.calculator.CalculatorFragment
import com.gorate.app.presentation.main.MainFragment
import com.gorate.app.presentation.main.UserFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Entry point activity of the application.
 * Manages the main navigation using a BottomNavigationView.
 */
class MainActivity : AppCompatActivity() {

    private val onboardingLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        initAppFlow()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        showPrivacyDisclosure {
            checkOnboarding()
        }
    }

    private fun checkOnboarding() {
        val prefs = com.gorate.app.data.repository.PreferencesRepository(this)
        if (prefs.isOnboardingCompleted()) {
            initAppFlow()
        } else {
            val intent = Intent(this, com.gorate.app.presentation.auth.OnboardingActivity::class.java)
            onboardingLauncher.launch(intent)
        }
    }

    private fun initAppFlow() {
        checkAppVersion()
        setupNavigationSystem()
        checkAuth()

        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            performFragmentTransaction(MainFragment())
        }
    }

    private fun showPrivacyDisclosure(onAccepted: () -> Unit) {
        val prefs = com.gorate.app.data.repository.PreferencesRepository(this)
        if (prefs.isPrivacyAccepted()) {
            onAccepted()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.privacy_disclosure_title)
            .setMessage(R.string.privacy_disclosure_msg)
            .setCancelable(false)
            .setPositiveButton(R.string.privacy_disclosure_btn) { _, _ ->
                prefs.setPrivacyAccepted(true)
                onAccepted()
            }
            .show()
    }

    private fun checkAuth() {
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            val intent = Intent(this, com.gorate.app.presentation.auth.AuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
            return
        }

        // Sincronización autoritativa en segundo plano con Cloud Firestore
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("users")
            .document(currentUser.uid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val isPro = doc.getBoolean("isPro") == true
                    val prefs = com.gorate.app.data.repository.PreferencesRepository(this)
                    prefs.setProUser(isPro)
                    val mode = doc.getString("driverMode")
                    if (!mode.isNullOrBlank()) {
                        prefs.setDriverMode(mode)
                    }
                }
            }

        // Sincronización con Google Play Billing
        com.gorate.app.data.billing.BillingManager(this).queryActivePurchases()
    }

    private fun checkAppVersion() {
        VersionController(this).checkVersion { isUpdateRequired ->
            if (isUpdateRequired) {
                showUpdateDialog()
            }
        }
    }

    private fun showUpdateDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.update_title)
            .setMessage(R.string.update_msg)
            .setCancelable(false)
            .setPositiveButton(R.string.update_btn) { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("market://details?id=$packageName")
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
                }
            }
            .show()
    }

    private fun setupNavigationSystem() {
        val navBar = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        navBar.setOnItemSelectedListener { menuItem ->
            when (navBar.selectedItemId != menuItem.itemId) {
                true -> {
                    when (menuItem.itemId) {
                        R.id.navigation_home -> performFragmentTransaction(MainFragment())
                        R.id.navigation_calculator -> performFragmentTransaction(CalculatorFragment())
                        R.id.navigation_user -> performFragmentTransaction(UserFragment())
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun performFragmentTransaction(target: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, target)
            .commit()
    }
}
