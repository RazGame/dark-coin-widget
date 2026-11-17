package com.darkside.widget.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.darkside.widget.R
import com.darkside.widget.api.DarksideApi
import com.darkside.widget.auth.AuthManager
import com.darkside.widget.databinding.ActivityLoginBinding
import kotlin.concurrent.thread

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isAuthCaptured = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
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

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        setupWebView()
        binding.webView.loadUrl("https://darkside-world.com/")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webSettings = binding.webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, true)

        binding.webView.addJavascriptInterface(WebAppInterface(), "AndroidBridge")

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressBar.progress = newProgress
                } else {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                injectTokenExtractor(view)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectTokenExtractor(view)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }
    }

    private fun injectTokenExtractor(webView: WebView?) {
        val js = """
            (function() {
                function check() {
                    try {
                        var token = localStorage.getItem("authorization-darkside");
                        if (token && token.length > 5) {
                            if (window.AndroidBridge) {
                                window.AndroidBridge.onTokenExtracted(token);
                            }
                        }
                    } catch(e) {}
                }
                check();
                if (!window._darksidePoll) {
                    window._darksidePoll = setInterval(check, 1000);
                }
            })();
        """.trimIndent()
        webView?.evaluateJavascript(js, null)
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun onTokenExtracted(token: String) {
            if (isAuthCaptured) return
            isAuthCaptured = true

            mainHandler.post {
                binding.progressBar.visibility = View.VISIBLE
                Toast.makeText(this@LoginActivity, "Авторизация подтверждена! Загрузка профиля...", Toast.LENGTH_SHORT).show()

                thread {
                    try {
                        val profile = DarksideApi.getProfile(token)
                        mainHandler.post {
                            if (profile != null) {
                                AuthManager.saveSession(
                                    context = applicationContext,
                                    token = token,
                                    userUuid = profile.uuid,
                                    name = profile.name,
                                    balance = profile.balance
                                )
                                Toast.makeText(this@LoginActivity, R.string.auth_success, Toast.LENGTH_SHORT).show()
                            } else {
                                AuthManager.saveSession(
                                    context = applicationContext,
                                    token = token,
                                    userUuid = null,
                                    name = "Михаил",
                                    balance = 0
                                )
                            }
                            finish()
                        }
                    } catch (e: Exception) {
                        mainHandler.post {
                            Toast.makeText(this@LoginActivity, "Ошибка получения профиля: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        binding.webView.removeJavascriptInterface("AndroidBridge")
        binding.webView.destroy()
        super.onDestroy()
    }
}
