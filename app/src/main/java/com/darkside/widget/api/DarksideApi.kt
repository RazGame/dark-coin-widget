package com.darkside.widget.api

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class UserProfile(
    val name: String,
    val balance: Int,
    val uuid: String?
)

sealed class ScanResponse {
    data class Success(val reward: Int) : ScanResponse()
    data class Error(val code: Int?, val message: String) : ScanResponse()
}

object DarksideApi {

    private const val BASE_URL = "https://darkside-world.com/backend/api"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Fetch user profile (balance, name, uuid) from Darkside World API
     */
    @Throws(IOException::class)
    fun getProfile(token: String): UserProfile? {
        val request = Request.Builder()
            .url("$BASE_URL/profile")
            .header("X-Auth-Token", token)
            .header("Accept", "application/json")
            .header("User-Agent", "DarksideWidget/1.0 (Android)")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return null
            }
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            val data = json.optJSONObject("data") ?: json.optJSONObject("user") ?: return null
            val name = data.optString("name", data.optString("nickname", "Пользователь"))
            val balance = data.optInt("balance", 0)
            val uuid = if (data.has("uuid") && !data.isNull("uuid")) data.getString("uuid") else null

            return UserProfile(name = name, balance = balance, uuid = uuid)
        }
    }

    /**
     * Submit scanned QR code from pack to Darkside World API
     */
    @Throws(IOException::class)
    fun submitQrCode(token: String, userUuid: String, qrCode: String): ScanResponse {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("userUuid", userUuid)
            .addFormDataPart("qrAsString", qrCode)
            .addFormDataPart("string", qrCode)
            .addFormDataPart("source", "widget-qr-scan")
            .addFormDataPart("channel", "")
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/qr")
            .header("X-Auth-Token", token)
            .header("Accept", "application/json")
            .header("User-Agent", "DarksideWidget/1.0 (Android)")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return ScanResponse.Error(null, "Пустой ответ сервера")
            val json = JSONObject(body)

            val isResult = json.optBoolean("result", false)
            val code = if (json.has("code") && !json.isNull("code")) json.optInt("code") else null
            val errorMsg = json.optString("error", "")

            if (isResult) {
                val data = json.optJSONObject("data")
                val reward = data?.optInt("reward", 100) ?: 100
                return ScanResponse.Success(reward = reward)
            } else {
                val resolvedMessage = when (code) {
                    1 -> "Этот QR-код уже был активирован ранее"
                    3 -> "Код не распознан. Попробуйте еще раз"
                    4 -> "Превышен дневной лимит активаций пачек"
                    else -> if (errorMsg.isNotBlank()) errorMsg else "Не удалось активировать QR-код"
                }
                return ScanResponse.Error(code = code, message = resolvedMessage)
            }
        }
    }
}
