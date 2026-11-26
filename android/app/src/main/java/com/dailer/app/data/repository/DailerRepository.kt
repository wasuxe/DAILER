package com.dailer.app.data.repository

import com.dailer.app.data.local.dao.CallRecordDao
import com.dailer.app.data.local.dao.RelativeDao
import com.dailer.app.data.local.entity.CallRecordEntity
import com.dailer.app.data.local.entity.RelativeEntity
import com.dailer.app.data.remote.ApiService
import com.dailer.app.data.remote.RetrofitClient
import com.dailer.app.data.remote.dto.EnrollRequest
import com.dailer.app.data.remote.dto.VerifyRequest
import com.dailer.app.data.remote.dto.VerifyResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import retrofit2.Response

/**
 * Single source of truth coordinating local Room persistence and remote voice
 * enrollment/verification API calls.
 */
class DailerRepository(
    private val relativeDao: RelativeDao,
    private val callRecordDao: CallRecordDao,
    private val customApiService: ApiService? = null
) {
    private val apiService: ApiService
        get() = customApiService ?: RetrofitClient.apiService

    /**
     * Enrolls a relative's voiceprint with the backend and persists the record locally.
     */
    suspend fun enrollRelative(
        name: String,
        phoneNumber: String,
        audioBase64: String,
        relationship: String,
        profileImageUri: String? = null,
        inviteToken: String? = null
    ): Result<RelativeEntity> {
        return safeCall {
            val request = EnrollRequest(
                name = name,
                phoneNumber = phoneNumber,
                audioBase64 = audioBase64,
                relationship = relationship,
                inviteToken = inviteToken?.takeIf { it.isNotBlank() }
            )
            val response = apiService.enroll(request)
            val body = response.requireBody("Enrollment")
            if (!body.success) {
                throw IllegalStateException(body.message.ifBlank { "Enrollment failed" })
            }

            val entity = RelativeEntity(
                name = name,
                phoneNumber = phoneNumber,
                enrollmentId = body.enrollmentId,
                enrolledAt = System.currentTimeMillis(),
                isVerified = true,
                relationship = relationship,
                profileImageUri = profileImageUri
            )
            val insertedId = relativeDao.insert(entity)
            entity.copy(id = insertedId)
        }
    }

    /**
     * Verifies call audio against the backend anti-spoofing and speaker matching service.
     * When [callRecordId] is provided, the corresponding local [CallRecordEntity] is updated
     * with the upload state.
     */
    suspend fun verifyCall(
        audioBase64: String,
        phoneNumber: String,
        claimedRelativeId: String? = null,
        callRecordId: Long? = null
    ): Result<VerifyResponse> {
        return safeCall {
            val request = VerifyRequest(
                audioBase64 = audioBase64,
                phoneNumber = phoneNumber,
                claimedRelativeId = claimedRelativeId
            )
            val response = apiService.verify(request)
            val body = response.requireBody("Verification")

            if (callRecordId != null) {
                callRecordDao.markUploaded(callRecordId, isUploaded = true)
            }
            body
        }
    }

    fun getAllRelatives(): Flow<List<RelativeEntity>> = relativeDao.getAll()

    suspend fun syncEnrollmentsFromServer(): Result<Int> {
        return safeCall {
            val response = apiService.getEnrollments()
            val body = response.requireBody("Sync enrollments")
            var added = 0
            for (remote in body.enrollments) {
                if (relativeDao.getByEnrollmentId(remote.enrollmentId) == null) {
                    relativeDao.insert(
                        RelativeEntity(
                            name = remote.name,
                            phoneNumber = remote.phoneNumber,
                            enrollmentId = remote.enrollmentId,
                            enrolledAt = System.currentTimeMillis(),
                            isVerified = true,
                            relationship = remote.relationship
                        )
                    )
                    added++
                }
            }
            added
        }
    }

    fun getEnrolledCount(): Flow<Int> = relativeDao.getEnrolledCount()

    suspend fun getRelativeByPhone(phoneNumber: String): RelativeEntity? {
        return relativeDao.findByPhoneNumber(phoneNumber)
    }

    suspend fun findRelativeByPhone(phoneNumber: String): RelativeEntity? {
        return getRelativeByPhone(phoneNumber)
    }

    suspend fun saveCallRecord(record: CallRecordEntity): Long {
        return callRecordDao.insert(record)
    }

    suspend fun insertCallRecord(record: CallRecordEntity): Long {
        return saveCallRecord(record)
    }

    suspend fun updateCallRecord(record: CallRecordEntity) {
        callRecordDao.update(record)
    }

    fun getAllCallRecords(): Flow<List<CallRecordEntity>> = callRecordDao.getAll()

    suspend fun getRecentCalls(limit: Int = 50): List<CallRecordEntity> {
        return callRecordDao.getRecent(limit)
    }

    suspend fun getPendingUploads(): List<CallRecordEntity> {
        return callRecordDao.getPending()
    }

    suspend fun getPendingCallRecords(): List<CallRecordEntity> {
        return getPendingUploads()
    }

    /**
     * Deletes a relative's enrollment on the backend and removes the local record.
     */
    suspend fun deleteEnrollment(relative: RelativeEntity): Result<Unit> {
        return safeCall {
            val response = apiService.deleteEnrollment(relative.enrollmentId)
            if (!response.isSuccessful && response.code() != HTTP_NOT_FOUND) {
                throw IllegalStateException("Delete enrollment failed: HTTP ${response.code()}")
            }
            relativeDao.delete(relative)
        }
    }

    /**
     * Deletes an enrollment by server-side [enrollmentId] remotely and locally if present.
     */
    suspend fun deleteEnrollment(enrollmentId: String): Result<Unit> {
        return safeCall {
            val response = apiService.deleteEnrollment(enrollmentId)
            if (!response.isSuccessful && response.code() != HTTP_NOT_FOUND) {
                throw IllegalStateException("Delete enrollment failed: HTTP ${response.code()}")
            }
            relativeDao.getByEnrollmentId(enrollmentId)?.let { local ->
                relativeDao.delete(local)
            }
        }
    }

    suspend fun deleteOldRecords(olderThanTimestamp: Long): Int {
        return callRecordDao.deleteOlderThan(olderThanTimestamp)
    }

    suspend fun checkHealth(): Result<Map<String, Any>> {
        return safeCall {
            apiService.healthCheck().requireBody("Health check")
        }
    }

    private fun mapVerificationStatus(response: VerifyResponse): String {
        if (response.verified) {
            return CallRecordEntity.STATUS_VERIFIED
        }
        val verdictLower = response.verdict.lowercase()
        return when {
            verdictLower.contains("warning") ||
                verdictLower.contains("scam") ||
                verdictLower.contains("synthetic") ||
                verdictLower.contains("fake") ||
                verdictLower.contains("spoof") ||
                response.aletheia?.isHuman == false -> CallRecordEntity.STATUS_WARNING
            verdictLower.contains("verified") -> CallRecordEntity.STATUS_VERIFIED
            else -> CallRecordEntity.STATUS_UNKNOWN
        }
    }

    private fun <T> Response<T>.requireBody(operation: String): T {
        if (!isSuccessful) {
            val errorDetail = errorBody()?.string()?.takeIf { it.isNotBlank() }
            val suffix = errorDetail?.let { " - $it" }.orEmpty()
            throw IllegalStateException("$operation failed: HTTP ${code()}$suffix")
        }
        return body() ?: throw IllegalStateException("$operation returned an empty response body")
    }

    private suspend fun <T> safeCall(block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}
