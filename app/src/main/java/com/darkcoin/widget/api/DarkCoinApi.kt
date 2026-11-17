package com.darkcoin.widget.api

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class UserProfile(val name: String, val balance: Int, val uuid: String?)

sealed class ScanResponse {
    data class Success(val reward: Int?) : ScanResponse()
    data class Error(val code: Int?, val message: String) : ScanResponse()
}

class ApiException(val status: Int) : IOException("HTTP $status")
class InvalidResponseException : IOException("Invalid server response")

object DarkCoinApi {
    private const val BASE_URL = "https://darkside-world.com/backend/api"
    private const val MAX_RESPONSE_BYTES = 256L * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        // X-Auth-Token is a custom credential header: never forward it through redirects.
        .followRedirects(false)
        .followSslRedirects(false)
        // POST /qr changes server state. Retrying it automatically is unsafe.
        .retryOnConnectionFailure(false)
        .build()

    // The complete profile call fits within the receiver's goAsync budget, including DNS/body.
    private val profileClient = client.newBuilder().callTimeout(6, TimeUnit.SECONDS).build()

    fun getProfile(token: String): UserProfile {
        return parseProfile(execute(profileClient, request("/profile", token).get().build()))
    }

    internal fun parseProfile(json: JSONObject): UserProfile {
        val data = json.optJSONObject("data") ?: json.optJSONObject("user")
            ?: throw InvalidResponseException()
        val balance = nonNegativeInt(data, "balance") ?: throw InvalidResponseException()
        val name = data.optString("name", "").takeIf { it.isNotBlank() && it != "null" }
            ?: data.optString("nickname", "").takeIf { it.isNotBlank() && it != "null" }
            ?: "Пользователь"
        val uuid = data.optString("uuid", "").takeIf { it.isNotBlank() && it != "null" }
        return UserProfile(name.take(200), balance, uuid)
    }

    fun submitQrCode(token: String, userUuid: String, qrCode: String): ScanResponse {
        require(isValidQrPayload(qrCode))
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("userUuid", userUuid)
            .addFormDataPart("qrAsString", qrCode)
            .addFormDataPart("string", qrCode)
            .addFormDataPart("source", "widget-qr-scan")
            .addFormDataPart("channel", "")
            .build()
        return parseScan(execute(client, request("/qr", token).post(body).build()))
    }

    internal fun parseScan(json: JSONObject): ScanResponse {
        if (json.optBoolean("result", false)) {
            return ScanResponse.Success(json.optJSONObject("data")?.let { nonNegativeInt(it, "reward") })
        }
        val code = nonNegativeInt(json, "code")
        val message = when (code) {
            1 -> "Этот QR-код уже был активирован ранее"
            3 -> "Код не распознан. Попробуйте ещё раз"
            4 -> "Превышен дневной лимит активаций пачек"
            else -> "Не удалось активировать QR-код"
        }
        return ScanResponse.Error(code, message)
    }

    fun isValidQrPayload(value: String): Boolean =
        value.isNotBlank() && value.length <= 8192 && value.none { it.isISOControl() }

    private fun request(path: String, token: String) = Request.Builder()
        .url("$BASE_URL$path")
        .header("X-Auth-Token", token)
        .header("Accept", "application/json")
        .header("User-Agent", "DarkCoinWidget/1.2.9 (Android)")

    internal fun execute(http: OkHttpClient, request: Request): JSONObject {
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ApiException(response.code)
            val body = response.body ?: throw InvalidResponseException()
            if (body.contentLength() > MAX_RESPONSE_BYTES) throw InvalidResponseException()
            val source = body.source()
            if (source.request(MAX_RESPONSE_BYTES + 1)) throw InvalidResponseException()
            return try {
                JSONObject(source.readUtf8())
            } catch (_: JSONException) {
                // Do not attach raw response bodies or credentials to exceptions.
                throw InvalidResponseException()
            }
        }
    }

    private fun nonNegativeInt(json: JSONObject, key: String): Int? {
        val text = json.opt(key)?.toString() ?: return null
        return text.toLongOrNull()?.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
    }
}
