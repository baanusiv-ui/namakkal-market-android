package com.namakkalmarket.app

/**
 * Registration is currently device-local. No Supabase service is configured
 * in this standalone build, so do not attempt a remote request.
 */
class RegistrationRepository {
    suspend fun register(mobileNumber: String) {
        require(mobileNumber.matches(Regex("^[6-9][0-9]{9}$"))) { "Invalid mobile number" }
    }
}
