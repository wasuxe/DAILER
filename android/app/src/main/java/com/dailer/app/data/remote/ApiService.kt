package com.dailer.app.data.remote

import com.dailer.app.data.remote.dto.EnrollRequest
import com.dailer.app.data.remote.dto.EnrollResponse
import com.dailer.app.data.remote.dto.VerifyRequest
import com.dailer.app.data.remote.dto.VerifyResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit service interface for the Dailer voice verification backend API.
 */
interface ApiService {

    @POST("api/enroll")
    suspend fun enroll(@Body request: EnrollRequest): Response<EnrollResponse>

    @POST("api/verify")
    suspend fun verify(@Body request: VerifyRequest): Response<VerifyResponse>

    @GET("api/health")
    suspend fun healthCheck(): Response<Map<String, Any>>

    @DELETE("api/enrollment/{enrollmentId}")
    suspend fun deleteEnrollment(@Path("enrollmentId") enrollmentId: String): Response<Unit>
}
