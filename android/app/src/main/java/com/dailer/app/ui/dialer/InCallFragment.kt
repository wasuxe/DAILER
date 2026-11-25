package com.dailer.app.ui.dialer

import android.os.Bundle
import android.telecom.Call
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.dailer.app.R
import com.dailer.app.databinding.FragmentInCallBinding
import com.dailer.app.util.Constants
import java.util.Locale

class InCallFragment : Fragment() {

    private var _binding: FragmentInCallBinding? = null
    private val binding get() = _binding!!
    private val viewModel: InCallViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInCallBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnAnswer.setOnClickListener { viewModel.answerCall() }
        binding.btnDecline.setOnClickListener { viewModel.declineCall() }
        binding.btnHangup.setOnClickListener { viewModel.hangupCall() }
        binding.btnMute.setOnClickListener { viewModel.toggleMute() }
        binding.btnSpeaker.setOnClickListener { viewModel.toggleSpeaker() }

        observeCall()
    }

    private fun observeCall() {
        viewModel.callerNumber.observe(viewLifecycleOwner) { number ->
            val displayNumber = number.ifBlank { getString(R.string.unknown_caller) }
            binding.tvCallerNumber.text = displayNumber
            if (viewModel.callerRelative.value == null) {
                binding.tvCallerName.text = displayNumber
            }
            updateBanner()
        }

        viewModel.callerRelative.observe(viewLifecycleOwner) { relative ->
            val number = viewModel.callerNumber.value.orEmpty()
            binding.tvCallerName.text = relative?.name
                ?: number.ifBlank { getString(R.string.unknown_caller) }
            updateBanner()
        }

        viewModel.callState.observe(viewLifecycleOwner) { state ->
            val statusRes = when (state) {
                Call.STATE_RINGING -> R.string.call_state_ringing
                Call.STATE_DIALING, Call.STATE_CONNECTING -> R.string.call_state_dialing
                Call.STATE_ACTIVE -> R.string.call_state_active
                else -> R.string.call_state_ended
            }
            binding.tvCallStatus.setText(statusRes)

            val ringing = viewModel.isRinging(state)
            binding.btnAnswer.visibility = if (ringing) View.VISIBLE else View.GONE
            binding.btnDecline.visibility = if (ringing) View.VISIBLE else View.GONE
            binding.btnHangup.visibility = if (!ringing) View.VISIBLE else View.GONE
            binding.tvCallTimer.visibility =
                if (state == Call.STATE_ACTIVE) View.VISIBLE else View.GONE
        }

        viewModel.callDurationSeconds.observe(viewLifecycleOwner) { seconds ->
            val mins = seconds / 60
            val secs = seconds % 60
            binding.tvCallTimer.text = String.format(Locale.US, "%02d:%02d", mins, secs)
        }

        viewModel.isMuted.observe(viewLifecycleOwner) { muted ->
            binding.btnMute.setText(if (muted) R.string.btn_unmute else R.string.btn_mute)
            binding.btnMute.backgroundTintList = ContextCompat.getColorStateList(
                requireContext(),
                if (muted) R.color.amber_dark else android.R.color.transparent
            )
        }

        viewModel.isSpeakerOn.observe(viewLifecycleOwner) { speakerOn ->
            binding.btnSpeaker.backgroundTintList = ContextCompat.getColorStateList(
                requireContext(),
                if (speakerOn) R.color.primary_green_dark else android.R.color.transparent
            )
        }

        viewModel.recentRecords.observe(viewLifecycleOwner) {
            updateBanner()
        }
    }

    private fun updateBanner() {
        val records = viewModel.recentRecords.value.orEmpty()
        val number = viewModel.callerNumber.value.orEmpty()
        val verdict = viewModel.latestVerdictForCaller(records, number)
        val relativeName = viewModel.callerRelative.value?.name
        binding.verificationBanner.setVerdict(verdict, relativeName)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
