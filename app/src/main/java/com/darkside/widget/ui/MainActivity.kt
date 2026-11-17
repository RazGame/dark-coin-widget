package com.darkside.widget.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.darkside.widget.R
import com.darkside.widget.api.DarksideApi
import com.darkside.widget.auth.AuthManager
import com.darkside.widget.databinding.ActivityMainBinding
import com.darkside.widget.scanner.QrScannerActivity
import com.darkside.widget.widget.DarksideWidgetProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle edge-to-edge system bar insets (status bar & navigation bar)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }

        // Ensure status bar & navigation bar icons match day/night appearance
        val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isNightMode
            isAppearanceLightNavigationBars = !isNightMode
        }

        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        updateUiState()
        if (AuthManager.isLoggedIn) {
            refreshProfile()
        }
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        binding.btnTestScan.setOnClickListener {
            startActivity(Intent(this, QrScannerActivity::class.java))
        }

        binding.btnRefresh.setOnClickListener {
            refreshProfile(showToast = true)
        }

        binding.btnLogout.setOnClickListener {
            AuthManager.clear(this)
            updateUiState()
            Toast.makeText(this, "Вы вышли из аккаунта", Toast.LENGTH_SHORT).show()
        }

        binding.btnPinWidget.setOnClickListener {
            requestPinWidget()
        }
    }

    private fun updateUiState() {
        if (AuthManager.isLoggedIn) {
            binding.cardLoggedIn.visibility = View.VISIBLE
            binding.cardNotLoggedIn.visibility = View.GONE

            binding.tvUserName.text = AuthManager.userName
            val formattedBalance = String.format(Locale.getDefault(), "%,d", AuthManager.balance).replace(',', ' ')
            binding.tvBalance.text = formattedBalance

            if (AuthManager.lastSyncTimestamp > 0) {
                val timeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                binding.tvLastSync.text = "Синхронизировано: " + timeFormat.format(Date(AuthManager.lastSyncTimestamp))
            } else {
                binding.tvLastSync.text = "Готово к работе"
            }
        } else {
            binding.cardLoggedIn.visibility = View.GONE
            binding.cardNotLoggedIn.visibility = View.VISIBLE
        }
    }

    private fun refreshProfile(showToast: Boolean = false) {
        val token = AuthManager.token ?: return
        if (showToast) {
            Toast.makeText(this, "Обновление данных...", Toast.LENGTH_SHORT).show()
        }

        thread {
            try {
                val profile = DarksideApi.getProfile(token)
                mainHandler.post {
                    if (profile != null) {
                        AuthManager.userName = profile.name
                        if (profile.uuid != null) {
                            AuthManager.userUuid = profile.uuid
                        }
                        AuthManager.updateBalance(applicationContext, profile.balance)
                        updateUiState()
                        if (showToast) {
                            Toast.makeText(this, "Баланс успешно обновлен!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    if (showToast) {
                        Toast.makeText(this, "Ошибка сети: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun requestPinWidget() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appWidgetManager = getSystemService(AppWidgetManager::class.java)
            val myProvider = ComponentName(this, DarksideWidgetProvider::class.java)

            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                appWidgetManager.requestPinAppWidget(myProvider, null, null)
                Toast.makeText(this, "Запрос на добавление виджета отправлен", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Добавьте виджет через долгое нажатие на рабочий стол", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Добавьте виджет через долгое нажатие на рабочий стол", Toast.LENGTH_LONG).show()
        }
    }
}
