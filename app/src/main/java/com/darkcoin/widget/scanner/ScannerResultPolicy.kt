package com.darkcoin.widget.scanner

import android.app.Activity
import com.google.mlkit.common.MlKitException

internal object ScannerResultPolicy {
    fun errorCode(resultCode: Int, reportedError: Int?): Int? = reportedError
        ?: MlKitException.CODE_SCANNER_CANCELLED.takeIf { resultCode == Activity.RESULT_CANCELED }
}
