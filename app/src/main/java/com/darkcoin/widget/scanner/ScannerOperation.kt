package com.darkcoin.widget.scanner

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.darkcoin.widget.api.ApiException
import com.darkcoin.widget.api.DarkCoinApi
import com.darkcoin.widget.api.ProfileRepository
import com.darkcoin.widget.api.ScanResponse
import com.darkcoin.widget.auth.AuthManager
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Retains only scanner operation state across rotation, never an Activity or its views. */
class ScannerOperation(app: Application) : AndroidViewModel(app) {
    data class Outcome(val message: String?, val success: Boolean = false)
    val outcome = MutableLiveData<Outcome>()
    var started = false
        private set
    private val main = Handler(Looper.getMainLooper())
    private var ownsScan = false

    fun start() {
        if (started) return
        started = true
        if (!active.compareAndSet(false, true)) {
            outcome.value = Outcome("Сканер уже открыт")
            return
        }
        ownsScan = true
        val session = AuthManager.session()
        if (session == null) {
            complete(Outcome("Войдите в аккаунт"))
            return
        }
        try {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build()
            val scannerContext = ScannerDelegateActivity.scannerContext(getApplication<Application>())
            GmsBarcodeScanning.getClient(scannerContext, options).startScan()
                .addOnCanceledListener { complete(Outcome(null)) }
                .addOnFailureListener {
                    complete(Outcome(if (ScannerErrors.isCancellation(it)) null else ScannerErrors.message(it)))
                }
                .addOnSuccessListener { barcode ->
                    val code = barcode.rawValue
                    if (code == null || !DarkCoinApi.isValidQrPayload(code)) {
                        complete(Outcome("QR-код пуст или содержит недопустимые данные"))
                    } else if (!AuthManager.isCurrent(session)) {
                        complete(Outcome("Сессия изменилась. Повторите сканирование"))
                    } else if (session.uuid.isNullOrBlank()) {
                        complete(Outcome("Профиль неполон. Обновите данные аккаунта"))
                    } else {
                        submit(session, code)
                    }
                }
        } catch (error: Exception) {
            complete(Outcome(if (ScannerErrors.isCancellation(error)) null else ScannerErrors.message(error)))
        }
    }

    private fun submit(session: AuthManager.Session, code: String) {
        val app = getApplication<Application>()
        thread(name = "dark-coin-qr") {
            val result = runCatching { DarkCoinApi.submitQrCode(session.token, session.uuid!!, code) }
            main.post {
                if (!AuthManager.isCurrent(session)) {
                    complete(Outcome("Сессия изменилась"))
                    return@post
                }
                result.fold(
                    onSuccess = { response ->
                        when (response) {
                            is ScanResponse.Success -> {
                                AuthManager.invalidateProfile(session)
                                // No guessed reward or local arithmetic: the server owns the balance.
                                ProfileRepository.refresh(app, afterMutation = true)
                                complete(Outcome(response.reward?.let { "+$it Dark Coins зачислено!" }
                                    ?: "QR-код активирован", success = true))
                            }
                            is ScanResponse.Error -> complete(Outcome(response.message))
                        }
                    },
                    onFailure = { error ->
                        if (error is ApiException && error.status == 401) {
                            AuthManager.clearSession(session)
                            AuthManager.clearWebSession()
                            ProfileRepository.sessionChanged(app)
                        } else {
                            // POST may have reached the server even when its reply was lost.
                            AuthManager.invalidateProfile(session)
                            ProfileRepository.refresh(app, afterMutation = true)
                        }
                        complete(Outcome(ProfileRepository.userMessage(error)))
                    }
                )
            }
        }
    }

    private fun complete(value: Outcome) {
        if (ownsScan) {
            ownsScan = false
            active.set(false)
        }
        outcome.value = value
    }

    companion object {
        private val active = AtomicBoolean(false)
    }
}
