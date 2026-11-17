package com.darkcoin.widget.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.darkcoin.widget.R
import com.darkcoin.widget.api.DarkCoinApi
import com.darkcoin.widget.api.ProfileRepository
import com.darkcoin.widget.auth.AuthManager
import com.darkcoin.widget.databinding.ActivityLoginBinding
import org.json.JSONTokener
import java.util.concurrent.Executors

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var capturing = false
    private var resumed = false
    private var loginGeneration = 0L
    private val poll = object : Runnable {
        override fun run() {
            if (!resumed || capturing) return
            val webView = binding.webView
            if (LoginUrlPolicy.isTrusted(webView.url)) {
                // evaluateJavascript runs in the main frame; no native bridge is exposed to iframes.
                webView.evaluateJavascript(
                    "if (location.origin === 'https://darkside-world.com') localStorage.getItem('authorization-darkside'); else null;"
                ) { result ->
                    if (!resumed || capturing || !LoginUrlPolicy.isTrusted(webView.url)) return@evaluateJavascript
                    val token = runCatching { JSONTokener(result).nextValue() as? String }.getOrNull()
                    if (token != null && token.length in 6..8192 && token.none { it <= ' ' || it >= '\u007f' }) {
                        captureToken(token)
                    }
                }
            }
            mainHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        loginGeneration = AuthManager.currentGeneration()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val night = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupWebView()
        binding.webView.loadUrl("https://darkside-world.com/")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.settings.apply {
            // The site's login app stores its token in localStorage and requires JavaScript.
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().setAcceptCookie(true)
        // Preserve the existing login/captcha cookie policy until the live auth flow is verified.
        // No native bridge is exposed to embedded third-party frames.
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, true)
        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.visibility = if (newProgress < 100 || capturing) View.VISIBLE else View.GONE
            }
        }
        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean =
                !LoginUrlPolicy.isTrusted(request.url.toString())

            @Deprecated("Used by old WebView implementations")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                !LoginUrlPolicy.isTrusted(url)

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                if (!LoginUrlPolicy.isTrusted(url)) view?.stopLoading()
            }
        }
        // Default SSL handling rejects certificate errors. Never override it with proceed().
    }

    private fun captureToken(token: String) {
        capturing = true
        binding.progressBar.visibility = View.VISIBLE
        executor.execute {
            val result = runCatching { DarkCoinApi.getProfile(token) }
            mainHandler.post {
                if (isDestroyed || isFinishing) return@post
                result.fold(
                    onSuccess = { profile ->
                        if (AuthManager.saveSession(loginGeneration, token, profile)) {
                            CookieManager.getInstance().flush()
                            ProfileRepository.sessionChanged(applicationContext)
                            Toast.makeText(this, R.string.auth_success, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onFailure = { Toast.makeText(this, ProfileRepository.userMessage(it), Toast.LENGTH_LONG).show() }
                )
                // A failed profile check never creates a fictitious logged-in session.
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        binding.webView.resumeTimers()
        binding.webView.onResume()
        mainHandler.post(poll)
    }

    override fun onPause() {
        resumed = false
        mainHandler.removeCallbacks(poll)
        binding.webView.onPause()
        // onPause alone does not pause JavaScript. Login is this app's only WebView.
        binding.webView.pauseTimers()
        super.onPause()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        binding.webView.apply {
            stopLoading()
            webChromeClient = null
            (parent as? ViewGroup)?.removeView(this)
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }
}
