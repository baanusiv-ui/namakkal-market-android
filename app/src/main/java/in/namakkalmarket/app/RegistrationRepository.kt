package com.namakkalmarket.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class RegistrationRepository {
    private val client = OkHttpClient()

    suspend fun register(mobileNumber: String) = withContext(Dispatchers.IO) {
        require(mobileNumber.matches(Regex("^[6-9][0-9]{9}$"))) { "Invalid mobile number" }
        val payload = JSONObject()
            .put("mobile_number", mobileNumber)
            .put("consent_given", true)
            .toString()
        val body = payload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/customer_registrations")
            .header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            .header("Authorization", "Bearer " + BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            .header("Content-Type", "application/json")
            .header("Prefer", "return=minimal")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val message = response.body?.string().orEmpty()
                throw IllegalStateException("Registration failed HTTP ${response.code}: $message")
            }
        }
    }
}
