package com.example.ytnowplaying.data.user

import android.util.Log
import com.google.gson.Gson
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private const val TAG = "UserApi"

data class ApiCallResult<T>(
    val code: Int,
    val body: T? = null,
    val message: String? = null,
    val error: Throwable? = null,
)

class UserClient(baseUrl: String) {

    private val gson = Gson()

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttp)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(UserApi::class.java)

    suspend fun login(userId: String, password: String): ApiCallResult<LoginResponse> {
        val uid = userId.trim()
        val pw = password.trim()

        return try {
            Log.d(TAG, "[REQ login] user_id='${uid.take(40)}'")
            val res = api.login(LoginRequest(uid, pw))
            Log.d(TAG, "[RES login] success=${res.success} msg='${res.message}'")
            ApiCallResult(code = 200, body = res, message = res.message)
        } catch (e: HttpException) {
            val code = e.code()
            val parsed = parseErrorBody<LoginResponse>(e)
            Log.w(TAG, "[HTTP login] code=$code msg='${parsed?.message}'")
            ApiCallResult(code = code, body = parsed, message = parsed?.message, error = e)
        } catch (t: Throwable) {
            Log.e(TAG, "[ERR login] ${t.message}", t)
            ApiCallResult(code = -1, body = null, message = "통신 오류가 발생했습니다.", error = t)
        }
    }

    suspend fun register(req: RegisterRequest): ApiCallResult<RegisterResponse> {
        return try {
            Log.d(TAG, "[REQ register] user_id='${req.userId.take(40)}' email='${req.userEmail.take(60)}'")
            val res = api.register(req)
            Log.d(TAG, "[RES register] success=${res.success} msg='${res.message}'")
            // 정상은 201이지만 Retrofit은 2xx면 그대로 들어옴. 여기서는 201 가정.
            ApiCallResult(code = 201, body = res, message = res.message)
        } catch (e: HttpException) {
            val code = e.code()
            val parsed = parseErrorBody<RegisterResponse>(e)
            Log.w(TAG, "[HTTP register] code=$code msg='${parsed?.message}'")
            ApiCallResult(code = code, body = parsed, message = parsed?.message, error = e)
        } catch (t: Throwable) {
            Log.e(TAG, "[ERR register] ${t.message}", t)
            ApiCallResult(code = -1, body = null, message = "통신 오류가 발생했습니다.", error = t)
        }
    }

    private inline fun <reified T> parseErrorBody(e: HttpException): T? {
        return try {
            val raw = e.response()?.errorBody()?.string()?.trim().orEmpty()
            if (raw.isBlank()) return null
            gson.fromJson(raw, T::class.java)
        } catch (_: Throwable) {
            null
        }
    }
}