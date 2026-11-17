package com.darkcoin.widget.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.darkcoin.widget.R
import com.darkcoin.widget.api.ProfileRepository
import com.darkcoin.widget.auth.AuthManager
import com.darkcoin.widget.databinding.ActivityMainBinding
import com.darkcoin.widget.scanner.QrScannerActivity
import com.darkcoin.widget.widget.DarkCoinWidgetProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val profileListener: () -> Unit = { updateUiState() }

    override fun onStart() {
        super.onStart()
        ProfileRepository.addListener(profileListener)
    }

    override fun onStop() {
        ProfileRepository.removeListener(profileListener)
        super.onStop()
    }

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
            AuthManager.clearSession()
            ProfileRepository.sessionChanged(applicationContext)
            binding.btnLogin.isEnabled = false
            AuthManager.clearWebSession {
                if (!isDestroyed) binding.btnLogin.isEnabled = true
            }
            updateUiState()
            Toast.makeText(this, "Вы вышли из аккаунта", Toast.LENGTH_SHORT).show()
        }

        binding.btnPinWidget.setOnClickListener {
            requestPinWidget()
        }
    }

    private fun updateUiState() {
        binding.btnRefresh.isEnabled = !ProfileRepository.isRefreshing
        if (AuthManager.isLoggedIn) {
            binding.cardLoggedIn.visibility = View.VISIBLE
            binding.cardNotLoggedIn.visibility = View.GONE

            binding.tvUserName.text = AuthManager.userName
            val formattedBalance = String.format(Locale.getDefault(), "%,d", AuthManager.balance).replace(',', ' ')
            binding.tvBalance.text = formattedBalance

            if (ProfileRepository.isRefreshing) {
                binding.tvLastSync.text = "Синхронизация…"
            } else if (ProfileRepository.lastError != null) {
                binding.tvLastSync.text = ProfileRepository.lastError
            } else if (AuthManager.lastSyncTimestamp > 0) {
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
        if (!AuthManager.isLoggedIn) return
        if (showToast) {
            Toast.makeText(this, "Обновление данных...", Toast.LENGTH_SHORT).show()
        }

        ProfileRepository.refresh(applicationContext)
    }

    private fun requestPinWidget() {
        val appWidgetManager = getSystemService(AppWidgetManager::class.java)
        val myProvider = ComponentName(this, DarkCoinWidgetProvider::class.java)

        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            appWidgetManager.requestPinAppWidget(myProvider, null, null)
            Toast.makeText(this, "Запрос на добавление виджета отправлен", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Добавьте виджет через долгое нажатие на рабочий стол", Toast.LENGTH_LONG).show()
        }
    }
}
