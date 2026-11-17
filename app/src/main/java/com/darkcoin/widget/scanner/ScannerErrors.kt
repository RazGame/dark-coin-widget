package com.darkcoin.widget.scanner

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.mlkit.common.MlKitException
import java.util.concurrent.CancellationException

object ScannerErrors {
    fun isCancellation(error: Exception): Boolean = when (error) {
        is MlKitException -> error.errorCode == MlKitException.CODE_SCANNER_CANCELLED ||
            error.errorCode == MlKitException.CANCELLED
        is ApiException -> error.statusCode == CommonStatusCodes.CANCELED
        is CancellationException -> true
        else -> false
    }

    fun message(error: Exception): String = when {
        error is MlKitException && error.errorCode == MlKitException.CODE_SCANNER_GOOGLE_PLAY_SERVICES_VERSION_TOO_OLD ->
            "Обновите Google Play Services для сканирования"
        error is MlKitException && error.errorCode == MlKitException.CODE_SCANNER_UNAVAILABLE ->
            "Модуль сканера ещё недоступен. Проверьте интернет и Google Play Services"
        else -> "Не удалось запустить сканер. Проверьте Google Play Services и повторите попытку"
    }
}
