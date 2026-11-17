package com.darkcoin.widget

import com.darkcoin.widget.scanner.ScannerErrors
import com.darkcoin.widget.scanner.ScannerResultPolicy
import com.darkcoin.widget.ui.LoginUrlPolicy
import com.darkcoin.widget.widget.WidgetLayoutSelector
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import com.google.mlkit.common.MlKitException
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class BoundaryTest {
    @Test fun onlyExactHttpsOriginIsAllowed() {
        assertTrue(LoginUrlPolicy.isTrusted("https://darkside-world.com/login?next=/profile"))
        assertTrue(LoginUrlPolicy.isTrusted("https://darkside-world.com:443/"))
        listOf(null, "http://darkside-world.com", "file:///data/data/app", "content://app/token",
            "javascript:alert(1)", "https://darkside-world.com.evil.test", "https://darkside-world.com@evil.test",
            "https://evil.test@darkside-world.com", "https://darkside-world.com:444", "https://darkside-world.com\\@evil.test")
            .forEach { assertFalse(it, LoginUrlPolicy.isTrusted(it)) }
    }

    @Test fun cancellationUsesExceptionDomainInsteadOfMessageOrSharedInteger() {
        assertTrue(ScannerErrors.isCancellation(MlKitException("closed", 201)))
        assertTrue(ScannerErrors.isCancellation(MlKitException("closed", 1)))
        assertTrue(ScannerErrors.isCancellation(ApiException(Status(16))))
        assertTrue(ScannerErrors.isCancellation(CancellationException()))
        assertFalse(ScannerErrors.isCancellation(MlKitException("hash mismatch 102", 102)))
        assertFalse(ScannerErrors.isCancellation(MlKitException("unauthenticated 16", 16)))
        assertFalse(ScannerErrors.isCancellation(MlKitException("Failed to scan code.", 13)))
        assertFalse(ScannerErrors.isCancellation(Exception("cannot cancel request 16 102")))
        assertFalse(ScannerErrors.isCancellation(ApiException(Status(201))))
    }

    @Test fun scannerClosedWithoutErrorBecomesCancellation() {
        // Framework RESULT_CANCELED=0 with null/empty data must not become SDK INTERNAL=13.
        val normalized = ScannerResultPolicy.errorCode(0, null)
        assertEquals(MlKitException.CODE_SCANNER_CANCELLED, normalized)
        assertTrue(ScannerErrors.isCancellation(MlKitException("closed", normalized!!)))
    }

    @Test fun scannerExplicitFailuresAndMalformedSuccessRemainFailures() {
        for (code in listOf(13, 16, 102, 202, 205, 206, 207)) {
            assertEquals(code, ScannerResultPolicy.errorCode(0, code))
            assertFalse(ScannerErrors.isCancellation(MlKitException("Failed to scan code.", code)))
        }
        assertNull(ScannerResultPolicy.errorCode(-1, null)) // RESULT_OK without payload is not cancel.
        assertNull(ScannerResultPolicy.errorCode(42, null)) // Unknown result is not cancel.
    }

    @Test fun layoutDoesNotPutDashboardInto110dpOrTallInto170dp() {
        assertEquals(WidgetLayoutSelector.Layout.SCAN_ONLY, WidgetLayoutSelector.select(64, 64))
        assertEquals(WidgetLayoutSelector.Layout.SHORT, WidgetLayoutSelector.select(140, 64))
        assertEquals(WidgetLayoutSelector.Layout.SHORT, WidgetLayoutSelector.select(140, 70))
        assertEquals(WidgetLayoutSelector.Layout.SHORT, WidgetLayoutSelector.select(220, 70))
        assertEquals(WidgetLayoutSelector.Layout.COMPACT, WidgetLayoutSelector.select(140, 140))
        assertEquals(WidgetLayoutSelector.Layout.COMPACT, WidgetLayoutSelector.select(140, 170))
        assertEquals(WidgetLayoutSelector.Layout.STRIP, WidgetLayoutSelector.select(300, 110))
        assertEquals(WidgetLayoutSelector.Layout.DASHBOARD, WidgetLayoutSelector.select(300, 220))
        assertEquals(WidgetLayoutSelector.Layout.TALL, WidgetLayoutSelector.select(400, 450))
        assertEquals(WidgetLayoutSelector.Layout.TALL, WidgetLayoutSelector.select(160, 300))
        assertEquals(WidgetLayoutSelector.Layout.COMPACT, WidgetLayoutSelector.select(160, 300, 2f))
    }
}
