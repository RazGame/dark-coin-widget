package com.darkcoin.widget.scanner

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.darkcoin.widget.auth.AuthManager
import com.darkcoin.widget.ui.LoginActivity

class QrScannerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AuthManager.isLoggedIn) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        val operation = ViewModelProvider(this)[ScannerOperation::class.java]
        // A fresh process cannot recover an external scanner task or safely replay a POST.
        if (savedInstanceState != null && !operation.started) {
            Toast.makeText(this, "Сканирование прервано. Повторите попытку", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        operation.outcome.observe(this) { result ->
            result.message?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
            if (result.success) vibrateSuccess()
            finish()
        }
        operation.start()
    }

    private fun vibrateSuccess() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
