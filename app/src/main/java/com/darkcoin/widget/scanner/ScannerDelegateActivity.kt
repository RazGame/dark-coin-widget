package com.darkcoin.widget.scanner

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.codescanner.internal.GmsBarcodeScanningDelegateActivity

/**
 * Compatibility adapter for play-services-code-scanner 16.1.0.
 * Its delegate drops RESULT_CANCELED and defaults a missing extra_error_code to INTERNAL.
 * Preserve the framework cancellation before passing the result to the SDK's own decoder.
 * Recheck this adapter when updating the pinned SDK; it uses the SDK's internal delegate.
 */
class ScannerDelegateActivity : GmsBarcodeScanningDelegateActivity() {
    @Deprecated("The pinned scanner SDK uses StartActivityForResult internally")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val reportedError = data?.takeIf { it.hasExtra(EXTRA_ERROR_CODE) }
            ?.getIntExtra(EXTRA_ERROR_CODE, MlKitException.INTERNAL)
        val errorCode = ScannerResultPolicy.errorCode(resultCode, reportedError)
        val normalized = if (reportedError == null && errorCode != null) {
            Intent(data ?: Intent()).putExtra(EXTRA_ERROR_CODE, errorCode)
        } else {
            data
        }
        super.onActivityResult(requestCode, resultCode, normalized)
    }

    companion object {
        private const val EXTRA_ERROR_CODE = "extra_error_code"

        fun scannerContext(application: Context): Context = object : ContextWrapper(application.applicationContext) {
            override fun startActivity(intent: Intent) {
                val component = intent.component
                val target = if (component?.packageName == packageName &&
                    component?.className == GmsBarcodeScanningDelegateActivity::class.java.name) {
                    Intent(intent).setClass(this, ScannerDelegateActivity::class.java)
                } else {
                    intent
                }
                super.startActivity(target)
            }
        }
    }
}
