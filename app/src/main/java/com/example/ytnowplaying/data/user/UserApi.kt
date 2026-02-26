package com.example.ytnowplaying.data.user

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 회원가입: POST api/users/register
 * 로그인:   POST api/users/login
 *
 * PDF 명세 기준:
 * - 회원가입 요청: user_id, password, name, user_email, sex(M/F), birth_date(YYYY-MM-DD)
 * - 로그인 요청: user_id, password
 */
interface UserApi {

    @POST("api/users/register")
    suspend fun register(@Body req: RegisterRequest): RegisterResponse

    @POST("api/users/login")
    suspend fun login(@Body req: LoginRequest): LoginResponse
}

data class LoginRequest(
    @SerializedName("user_id") val userId: String,
    @SerializedName("password") val password: String,
)

data class LoginResponse(
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("user_email") val userEmail: String? = null,
    @SerializedName("message") val message: String? = null,
)

data class RegisterRequest(
    @SerializedName("user_id") val userId: String,
    @SerializedName("password") val password: String,
    @SerializedName("name") val name: String,
    @SerializedName("user_email") val userEmail: String,
    @SerializedName("sex") val sex: String, // "M" or "F"
    @SerializedName("birth_date") val birthDate: String, // YYYY-MM-DD
)

data class RegisterResponse(
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("user_email") val userEmail: String? = null,
    @SerializedName("message") val message: String? = null,
)