package com.dailer.app.ui.enrollment

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.dailer.app.R
import com.dailer.app.databinding.FragmentEnrollBinding
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.util.Locale

class EnrollFragment : Fragment() {

    private var _binding: FragmentEnrollBinding? = null
    private val binding get() = _binding!!
    private val viewModel: EnrollViewModel by viewModels()

    private var inviteToken: String? = null

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startRecording()
        } else {
            Toast.makeText(
                requireContext(),
                R.string.status_permissions_pending,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result ->
        val contents = result.contents ?: return@registerForActivityResult
        applyDeepLinkContents(contents)
        Toast.makeText(requireContext(), "Invite details loaded", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEnrollBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        populateFromArguments()

        binding.btnRecordSample.setOnClickListener {
            if (viewModel.recordingState.value == EnrollViewModel.RecordingState.RECORDING) {
                viewModel.stopRecording()
            } else {
                startRecordingWithPermissionCheck()
            }
        }

        binding.cbConsent.setOnCheckedChangeListener { _, _ ->
            updateSubmitButtonState()
        }

        binding.btnSubmitEnrollment.setOnClickListener {
            viewModel.submitEnrollment(
                name = binding.etName.text?.toString().orEmpty(),
                phoneNumber = binding.etPhone.text?.toString().orEmpty(),
                relationship = binding.etRelationship.text?.toString().orEmpty(),
                inviteToken = inviteToken
            )
        }

        binding.btnScanQr.setOnClickListener {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt(getString(R.string.qr_instructions))
                setBeepEnabled(false)
            }
            qrScanLauncher.launch(options)
        }

        observeState()
    }

    private fun populateFromArguments() {
        val args = arguments ?: return
        inviteToken = args.getString("inviteToken")?.takeIf { it.isNotBlank() }
        args.getString("relativeName")?.takeIf { it.isNotBlank() }?.let {
            binding.etName.setText(it)
        }
        args.getString("phoneNumber")?.takeIf { it.isNotBlank() }?.let {
            binding.etPhone.setText(it)
        }
        args.getString("relationship")?.takeIf { it.isNotBlank() }?.let {
            binding.etRelationship.setText(it)
        }
    }

    private fun applyDeepLinkContents(rawContents: String) {
        val parsed = Uri.parse(rawContents)
        if (parsed.scheme == "dailer") {
            inviteToken = parsed.getQueryParameter("token")?.takeIf { it.isNotBlank() }
                ?: rawContents.substringAfter("token=", rawContents)
            parsed.getQueryParameter("name")?.takeIf { it.isNotBlank() }?.let {
                binding.etName.setText(it)
            }
            parsed.getQueryParameter("phone")?.takeIf { it.isNotBlank() }?.let {
                binding.etPhone.setText(it)
            }
            parsed.getQueryParameter("relationship")?.takeIf { it.isNotBlank() }?.let {
                binding.etRelationship.setText(it)
            }
        } else {
            inviteToken = rawContents.substringAfter("token=", rawContents)
        }
    }

    private fun startRecordingWithPermissionCheck() {
        val hasAudioPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasAudioPermission) {
            viewModel.startRecording()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun observeState() {
        viewModel.recordingState.observe(viewLifecycleOwner) { state ->
            when (state) {
                EnrollViewModel.RecordingState.IDLE -> {
                    binding.btnRecordSample.setText(R.string.btn_start_recording)
                    binding.btnRecordSample.setIconResource(R.drawable.ic_mic)
                    binding.tvRecordingStatus.setText(R.string.recording_status_idle)
                }
                EnrollViewModel.RecordingState.RECORDING -> {
                    binding.btnRecordSample.setText(R.string.btn_stop_recording)
                    binding.btnRecordSample.setIconResource(R.drawable.ic_stop)
                    binding.tvRecordingStatus.setText(R.string.recording_status_active)
                }
                EnrollViewModel.RecordingState.RECORDED -> {
                    binding.btnRecordSample.setText(R.string.btn_start_recording)
                    binding.btnRecordSample.setIconResource(R.drawable.ic_mic)
                    val formatted = formatSeconds(viewModel.elapsedSeconds.value ?: 0)
                    binding.tvRecordingStatus.text = getString(R.string.recording_status_done, formatted)
                }
                null -> Unit
            }
            updateSubmitButtonState()
        }

        viewModel.elapsedSeconds.observe(viewLifecycleOwner) { seconds ->
            binding.tvRecordingTimer.text = formatSeconds(seconds)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressEnroll.visibility = if (loading) View.VISIBLE else View.GONE
            updateSubmitButtonState()
        }

        viewModel.enrollStatus.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            result.onSuccess {
                Toast.makeText(
                    requireContext(),
                    "Relative enrolled successfully",
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.clearEnrollStatus()
                findNavController().popBackStack()
            }.onFailure { error ->
                Toast.makeText(
                    requireContext(),
                    error.message ?: "Enrollment failed",
                    Toast.LENGTH_LONG
                ).show()
                viewModel.clearEnrollStatus()
            }
        }
    }

    private fun updateSubmitButtonState() {
        val hasSample = viewModel.recordingState.value == EnrollViewModel.RecordingState.RECORDED &&
            viewModel.recordedFile.value?.exists() == true
        val hasConsent = binding.cbConsent.isChecked
        val isLoading = viewModel.isLoading.value == true
        binding.btnSubmitEnrollment.isEnabled = hasSample && hasConsent && !isLoading
    }

    private fun formatSeconds(totalSeconds: Int): String {
        val mins = totalSeconds / 60
        val secs = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    override fun onDestroyView() {
        if (viewModel.recordingState.value == EnrollViewModel.RecordingState.RECORDING) {
            viewModel.stopRecording()
        }
        super.onDestroyView()
        _binding = null
    }
}
