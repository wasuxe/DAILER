package com.dailer.app.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dailer.app.R
import com.dailer.app.data.local.entity.CallRecordEntity
import com.dailer.app.data.local.entity.RelativeEntity
import com.dailer.app.databinding.ItemCallHistoryBinding
import com.dailer.app.util.AudioUtils
import com.dailer.app.util.Constants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallHistoryAdapter :
    ListAdapter<CallRecordEntity, CallHistoryAdapter.CallHistoryViewHolder>(DiffCallback) {

    private var relativesByPhone: Map<String, RelativeEntity> = emptyMap()

    fun updateRelatives(relatives: List<RelativeEntity>) {
        relativesByPhone = relatives.associateBy { AudioUtils.normalizePhoneNumber(it.phoneNumber) }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallHistoryViewHolder {
        val binding = ItemCallHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CallHistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CallHistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CallHistoryViewHolder(
        private val binding: ItemCallHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormatter = SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault())

        fun bind(record: CallRecordEntity) {
            val ctx = binding.root.context
            val normalizedPhone = AudioUtils.normalizePhoneNumber(record.phoneNumber)
            val relative = relativesByPhone[normalizedPhone]

            binding.tvCallCaller.text = relative?.name
                ?: record.phoneNumber.ifBlank { ctx.getString(R.string.unknown_caller) }

            val durationSec = (record.durationMs / 1000L).coerceAtLeast(0L)
            val durationFormatted = String.format(Locale.US, "%02d:%02d", durationSec / 60, durationSec % 60)
            val directionLabel = if (record.callDirection.equals(Constants.CALL_DIRECTION_OUTGOING, true)) {
                "Outgoing"
            } else {
                "Incoming"
            }
            binding.tvCallMeta.text = "$directionLabel • ${dateFormatter.format(Date(record.timestamp))} • Recorded $durationFormatted"

            val verdictUpper = record.verdict.uppercase(Locale.US)
            val isClonedOrWarning = verdictUpper == Constants.VERDICT_SCAM_WARNING ||
                verdictUpper == "WARNING" ||
                record.syntheticScore >= 0.5f

            when {
                isClonedOrWarning -> {
                    binding.tvCallVerdictBadge.text = if (record.syntheticScore >= 0.5f) {
                        "AI CLONED VOICE DETECTED"
                    } else {
                        "WARNING: NOT VERIFIED"
                    }
                    binding.tvCallVerdictBadge.setTextColor(ContextCompat.getColor(ctx, R.color.warning_red))
                    binding.cardCallHistory.strokeColor = ContextCompat.getColor(ctx, R.color.warning_red)
                    binding.ivCallStatusIcon.setImageResource(R.drawable.ic_warning)
                }
                verdictUpper == Constants.VERDICT_VERIFIED -> {
                    binding.tvCallVerdictBadge.text = "REAL HUMAN • VERIFIED"
                    binding.tvCallVerdictBadge.setTextColor(ContextCompat.getColor(ctx, R.color.primary_green_dark))
                    binding.cardCallHistory.strokeColor = ContextCompat.getColor(ctx, R.color.primary_green)
                    binding.ivCallStatusIcon.setImageResource(R.drawable.ic_verified)
                }
                verdictUpper == Constants.VERDICT_PENDING || verdictUpper == Constants.VERDICT_ANALYZING -> {
                    binding.tvCallVerdictBadge.text = "RECORDING SAVED • ANALYZING…"
                    binding.tvCallVerdictBadge.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                    binding.cardCallHistory.strokeColor = ContextCompat.getColor(ctx, R.color.pending_gray_border)
                    binding.ivCallStatusIcon.setImageResource(R.drawable.ic_pending)
                }
                else -> {
                    binding.tvCallVerdictBadge.text = "UNVERIFIED / CHECK FAILED"
                    binding.tvCallVerdictBadge.setTextColor(ContextCompat.getColor(ctx, R.color.amber_dark))
                    binding.cardCallHistory.strokeColor = ContextCompat.getColor(ctx, R.color.pending_gray_border)
                    binding.ivCallStatusIcon.setImageResource(R.drawable.ic_pending)
                }
            }

            val scoresText = if (record.isUploaded && (record.confidence > 0f || record.speakerScore > 0f)) {
                val humanPct = (record.confidence * 100).toInt()
                val matchPct = (record.speakerScore * 100).toInt()
                "Human: $humanPct% • Voice Match: $matchPct%"
            } else {
                ""
            }
            val reasonText = record.reason?.takeIf { it.isNotBlank() }.orEmpty()
            binding.tvCallDetails.text = listOf(scoresText, reasonText)
                .filter { it.isNotBlank() }
                .joinToString("\n")
                .ifBlank { "Waiting for verification result" }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<CallRecordEntity>() {
        override fun areItemsTheSame(oldItem: CallRecordEntity, newItem: CallRecordEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CallRecordEntity, newItem: CallRecordEntity): Boolean {
            return oldItem == newItem
        }
    }
}
