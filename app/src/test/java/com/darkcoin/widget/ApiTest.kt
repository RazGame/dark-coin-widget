package com.darkcoin.widget

import com.darkcoin.widget.api.ApiException
import com.darkcoin.widget.api.DarkCoinApi
import com.darkcoin.widget.api.InvalidResponseException
import com.darkcoin.widget.api.ScanResponse
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ApiTest {
    @Test fun invalidBalanceIsNeverSilentlyReplacedByZero() {
        listOf("null", "-1", "2147483648", "1.5", "\"abc\"").forEach { value ->
            assertThrows(InvalidResponseException::class.java) {
                DarkCoinApi.parseProfile(JSONObject("{\"data\":{\"balance\":$value}}"))
            }
        }
        assertThrows(InvalidResponseException::class.java) { DarkCoinApi.parseProfile(JSONObject("{}")) }
        assertEquals(0, DarkCoinApi.parseProfile(JSONObject("{\"user\":{\"balance\":0}}")).balance)
        assertEquals(Int.MAX_VALUE, DarkCoinApi.parseProfile(JSONObject("{\"data\":{\"balance\":2147483647}}")).balance)
    }

    @Test fun missingRewardDoesNotInventCoins() {
        assertNull((DarkCoinApi.parseScan(JSONObject("{\"result\":true}")) as ScanResponse.Success).reward)
        assertTrue(DarkCoinApi.parseScan(JSONObject("{\"result\":false,\"code\":1}")) is ScanResponse.Error)
    }

    @Test fun httpErrorsWinOverMisleadingSuccessBodyAndDoNotLeakBody() {
        listOf(301, 401, 403, 429, 500).forEach { status ->
            val error = assertThrows(ApiException::class.java) { execute(status, "{\"result\":true,\"token\":\"sensitive\"}") }
            assertEquals(status, error.status)
            assertFalse(error.toString().contains("sensitive"))
        }
    }

    @Test fun malformedAndOversizedRepliesFailSafely() {
        assertThrows(InvalidResponseException::class.java) { execute(200, "<html>bad gateway</html>") }
        assertThrows(InvalidResponseException::class.java) { execute(200, "x".repeat(262145)) }
    }

    @Test fun qrIsBoundedOpaqueDataNeverAnExecutableCommand() {
        assertFalse(DarkCoinApi.isValidQrPayload(" "))
        assertFalse(DarkCoinApi.isValidQrPayload("abc\u0000xyz"))
        assertFalse(DarkCoinApi.isValidQrPayload("x".repeat(8193)))
        assertTrue(DarkCoinApi.isValidQrPayload("https://example.test/code?a=123"))
    }

    private fun execute(status: Int, body: String): JSONObject {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(status).message("test").body(body.toResponseBody()).build()
        }.build()
        return DarkCoinApi.execute(client, Request.Builder().url("https://darkside-world.com/test").build())
    }
}
