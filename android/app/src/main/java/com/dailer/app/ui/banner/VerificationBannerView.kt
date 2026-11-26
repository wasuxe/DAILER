package com.dailer.app.ui.banner

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.transition.TransitionManager
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import com.dailer.app.R
import com.dailer.app.databinding.ViewVerificationBannerBinding
import com.dailer.app.util.Constants
import com.google.android.material.card.MaterialCardView
import java.util.Locale

class VerificationBannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

    enum class BannerState {
        PENDING,
        VERIFIED,
        WARNING
    }

    private val binding: ViewVerificationBannerBinding =
        ViewVerificationBannerBinding.inflate(LayoutInflater.from(context), this, true)

    private var currentState: BannerState? = null
    private var currentBgColor: Int = ContextCompat.getColor(context, R.color.pending_gray_light)
    private var currentStrokeColor: Int = ContextCompat.getColor(context, R.color.pending_gray_border)

    init {
        setCardBackgroundColor(Color.TRANSPARENT)
        cardElevation = 0f
        strokeWidth = 0
        setBannerState(BannerState.PENDING)
    }

    fun setVerdict(status: String, name: String? = null, reason: String? = null) {
        val mappedState = when (status.uppercase(Locale.US)) {
            Constants.VERDICT_VERIFIED -> BannerState.VERIFIED
            Constants.VERDICT_SCAM_WARNING, "WARNING" -> BannerState.WARNING
            Constants.VERDICT_UNKNOWN -> BannerState.WARNING  // Timeout / unavailable
            else -> BannerState.PENDING
        }
        setBannerState(mappedState, name, reason)
    }

    fun setBannerState(state: BannerState, name: String? = null, reason: String? = null) {
        val targetBgColor: Int
        val targetStrokeColor: Int

        when (state) {
            BannerState.PENDING -> {
                targetBgColor = ContextCompat.getColor(context, R.color.pending_gray_light)
                targetStrokeColor = ContextCompat.getColor(context, R.color.pending_gray_border)
                binding.ivBannerIcon.setImageResource(R.drawable.ic_pending)
                binding.tvBannerTitle.setText(R.string.banner_verifying_title)
                binding.tvBannerSubtitle.setText(R.string.banner_verifying_subtitle)
            }
            BannerState.VERIFIED -> {
                targetBgColor = ContextCompat.getColor(context, R.color.primary_green_light)
                targetStrokeColor = ContextCompat.getColor(context, R.color.primary_green)
                binding.ivBannerIcon.setImageResource(R.drawable.ic_verified)
                if (!name.isNullOrBlank()) {
                    binding.tvBannerTitle.text = context.getString(R.string.banner_verified_named_title, name)
                } else {
                    binding.tvBannerTitle.setText(R.string.banner_verified_title)
                }
                if (!reason.isNullOrBlank()) {
                    binding.tvBannerSubtitle.text = reason
                } else {
                    binding.tvBannerSubtitle.setText(R.string.banner_verified_subtitle)
                }
            }
            BannerState.WARNING -> {
                targetBgColor = ContextCompat.getColor(context, R.color.warning_red_light)
                targetStrokeColor = ContextCompat.getColor(context, R.color.warning_red)
                binding.ivBannerIcon.setImageResource(R.drawable.ic_warning)
                if (!name.isNullOrBlank()) {
                    binding.tvBannerTitle.text = context.getString(R.string.banner_warning_named_title, name)
                } else {
                    binding.tvBannerTitle.setText(R.string.banner_warning_title)
                }
                if (!reason.isNullOrBlank()) {
                    binding.tvBannerSubtitle.text = reason
                } else {
                    binding.tvBannerSubtitle.setText(R.string.banner_warning_subtitle)
                }
            }
        }

        if (currentState != state) {
            TransitionManager.beginDelayedTransition(this)
            animateCardColors(targetBgColor, targetStrokeColor)
            animateIconAttention(state)
            currentState = state
        }
    }

    private fun animateCardColors(targetBg: Int, targetStroke: Int) {
        val startBg = currentBgColor
        val startStroke = currentStrokeColor
        val evaluator = ArgbEvaluator()

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320L
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                val bg = evaluator.evaluate(fraction, startBg, targetBg) as Int
                val stroke = evaluator.evaluate(fraction, startStroke, targetStroke) as Int
                binding.bannerCard.setCardBackgroundColor(bg)
                binding.bannerCard.strokeColor = stroke
            }
            start()
        }
        currentBgColor = targetBg
        currentStrokeColor = targetStroke
    }

    private fun animateIconAttention(state: BannerState) {
        val targetScale = if (state == BannerState.WARNING) 1.2f else 1.12f
        binding.ivBannerIcon.animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .setDuration(180L)
            .withEndAction {
                binding.ivBannerIcon.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180L)
                    .start()
            }
            .start()
    }
}
