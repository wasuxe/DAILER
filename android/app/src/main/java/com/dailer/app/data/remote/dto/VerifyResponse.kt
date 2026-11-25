package com.dailer.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Response from the voice verification endpoint combining Aletheia anti-spoofing
 * (human vs. AI-synthesized voice) and Eidos speaker matching.
 */
data class VerifyResponse(
    @SerializedName("verified")
    val verified: Boolean,
    @SerializedName("verdict")
    val verdict: String,
    @SerializedName("message")
    val message: String,
    @SerializedName(value = "aletheia", alternate = ["aletheia_result", "aletheiaResult"])
    val aletheia: AletheiaResult? = null,
    @SerializedName(value = "eidos", alternate = ["eidos_result", "eidosResult"])
    val eidos: EidosResult? = null
) {
    /**
     * Deepfake / synthetic voice detection output from the Aletheia engine.
     */
    data class AletheiaResult(
        @SerializedName(value = "is_human", alternate = ["isHuman"])
        val isHuman: Boolean,
        @SerializedName("confidence")
        val confidence: Float
    )

    /**
     * Speaker identity verification output from the Eidos voiceprint matcher.
     */
    data class EidosResult(
        @SerializedName(value = "match_score", alternate = ["matchScore"])
        val matchScore: Float,
        @SerializedName(value = "matched_name", alternate = ["matchedName"])
        val matchedName: String? = null,
        @SerializedName("threshold")
        val threshold: Float
    )
}
