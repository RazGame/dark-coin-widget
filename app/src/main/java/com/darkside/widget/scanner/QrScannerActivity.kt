package com.darkside.widget.scanner

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.darkside.widget.R
import com.darkside.widget.api.DarksideApi
import com.darkside.widget.api.ScanResponse
import com.darkside.widget.auth.AuthManager
import com.darkside.widget.ui.LoginActivity
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlin.concurrent.thread

class QrScannerActivity : AppCompatActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Check Authentication
        if (!AuthManager.isLoggedIn) {
            Toast.makeText(this, R.string.tap_to_login, Toast.LENGTH_LONG).show()
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        // 2. Launch Google Code Scanner
        startGoogleCodeScanner()
    }

    private fun startGoogleCodeScanner() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()

        val scanner = GmsBarcodeScanning.getClient(this, options)

        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val rawValue = barcode.rawValue
                if (!rawValue.isNullOrBlank()) {
                    performHapticFeedback(isSuccess = true)
                    processScannedCode(rawValue)
                } else {
                    finish()
                }
            }
            .addOnCanceledListener {
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, getString(R.string.scan_error_generic, e.localizedMessage), Toast.LENGTH_LONG).show()
                finish()
            }
    }

    private fun processScannedCode(qrCode: String) {
        val token = AuthManager.token ?: run {
            finish()
            return
        }
        val userUuid = AuthManager.userUuid ?: ""

        Toast.makeText(this, "Активируем QR-код пачки...", Toast.LENGTH_SHORT).show()

        thread {
            try {
                val response = DarksideApi.submitQrCode(token, userUuid, qrCode)
                mainHandler.post {
                    when (response) {
                        is ScanResponse.Success -> {
                            performHapticFeedback(isSuccess = true)
                            // Update balance
                            val newBalance = AuthManager.balance + response.reward
                            AuthManager.updateBalance(applicationContext, newBalance)

                            val successMessage = getString(R.string.scan_success, response.reward)
                            Toast.makeText(this, "🎉 $successMessage", Toast.LENGTH_LONG).show()

                            // Also refresh full profile in background
                            refreshProfileInBackground(token)
                        }
                        is ScanResponse.Error -> {
                            performHapticFeedback(isSuccess = false)
                            Toast.makeText(this, response.message, Toast.LENGTH_LONG).show()
                        }
                    }
                    finish()
                }
            } catch (e: Exception) {
                mainHandler.post {
                    performHapticFeedback(isSuccess = false)
                    Toast.makeText(this, "Ошибка сети: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun refreshProfileInBackground(token: String) {
        thread {
            try {
                val profile = DarksideApi.getProfile(token)
                if (profile != null) {
                    mainHandler.post {
                        AuthManager.userName = profile.name
                        AuthManager.updateBalance(applicationContext, profile.balance)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun performHapticFeedback(isSuccess: Boolean) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (isSuccess) {
                    VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                } else {
                    VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 100), -1)
                }
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(if (isSuccess) 100L else 300L)
            }
        } catch (_: Exception) {
        }
    }
}
