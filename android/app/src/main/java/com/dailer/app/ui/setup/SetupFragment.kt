package com.dailer.app.ui.setup

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.dailer.app.R
import com.dailer.app.databinding.FragmentSetupBinding
import com.dailer.app.ui.dialer.DialerActivity
import com.dailer.app.util.PermissionHelper
import com.dailer.app.util.RoleManagerHelper
import com.google.android.material.card.MaterialCardView

class SetupFragment : Fragment() {

    private var _binding: FragmentSetupBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SetupViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshStatus()
    }

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK || RoleManagerHelper.isDefaultDialer(requireContext())) {
            viewModel.refreshStatus()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSetDefaultDialer.setOnClickListener {
            val intent = RoleManagerHelper.createDefaultDialerIntent(requireContext())
            if (intent != null) {
                roleLauncher.launch(intent)
            } else {
                Toast.makeText(requireContext(), R.string.status_dialer_pending, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSaveServerUrl.setOnClickListener {
            val url = binding.etServerUrl.text?.toString().orEmpty()
            if (viewModel.saveServerUrl(url)) {
                binding.tilServerUrl.error = null
                Toast.makeText(
                    requireContext(),
                    getString(R.string.status_server_configured, url.trim()),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                binding.tilServerUrl.error = getString(R.string.status_server_invalid)
            }
        }

        binding.btnGrantPermissions.setOnClickListener {
            val missing = PermissionHelper.missingPermissions(requireContext())
            if (missing.isNotEmpty()) {
                permissionLauncher.launch(missing)
            } else {
                viewModel.refreshStatus()
            }
        }

        binding.btnContinue.setOnClickListener {
            viewModel.completeSetup()
            findNavController().navigate(R.id.action_setup_to_relatives)
        }

        binding.btnOpenDialer.setOnClickListener {
            startActivity(Intent(requireContext(), DialerActivity::class.java))
        }

        observeState()
        viewModel.refreshStatus()
    }

    private fun observeState() {
        viewModel.isDefaultDialer.observe(viewLifecycleOwner) { isDefault ->
            binding.tvDialerRoleStatus.setText(
                if (isDefault) R.string.status_dialer_granted else R.string.status_dialer_pending
            )
            binding.tvDialerRoleStatus.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (isDefault) R.color.primary_green_dark else R.color.text_secondary
                )
            )
            updateStepCompletion(binding.cardDefaultDialer, binding.ivDialerCheck, isDefault)
            updateSummary()
        }

        viewModel.serverUrl.observe(viewLifecycleOwner) { savedUrl ->
            if (binding.etServerUrl.text.isNullOrBlank()) {
                binding.etServerUrl.setText(savedUrl)
            }
            binding.tvServerUrlStatus.text = getString(R.string.status_server_configured, savedUrl)
        }

        viewModel.isServerConfigured.observe(viewLifecycleOwner) { configured ->
            updateStepCompletion(binding.cardServerUrl, binding.ivServerCheck, configured)
            updateSummary()
        }

        viewModel.permissionsGranted.observe(viewLifecycleOwner) { granted ->
            binding.tvPermissionsStatus.setText(
                if (granted) R.string.status_permissions_granted else R.string.status_permissions_pending
            )
            binding.tvPermissionsStatus.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (granted) R.color.primary_green_dark else R.color.text_secondary
                )
            )
            updateStepCompletion(binding.cardPermissions, binding.ivPermissionsCheck, granted)
            updateSummary()
        }

        viewModel.isSetupComplete.observe(viewLifecycleOwner) { complete ->
            updateStepCompletion(binding.cardComplete, binding.ivCompleteCheck, complete)
        }
    }

    private fun updateStepCompletion(
        card: MaterialCardView,
        checkIcon: ImageView,
        completed: Boolean
    ) {
        val strokeColor = ContextCompat.getColor(
            requireContext(),
            if (completed) R.color.primary_green else R.color.divider
        )
        val iconColor = ContextCompat.getColor(
            requireContext(),
            if (completed) R.color.primary_green else R.color.pending_gray_border
        )
        card.strokeColor = strokeColor
        checkIcon.setColorFilter(iconColor)
    }

    private fun updateSummary() {
        val allReady = (viewModel.isDefaultDialer.value == true) &&
            (viewModel.isServerConfigured.value == true) &&
            (viewModel.permissionsGranted.value == true)
        binding.tvSetupSummary.setText(
            if (allReady) R.string.status_setup_ready else R.string.status_setup_incomplete
        )
        binding.tvSetupSummary.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (allReady) R.color.primary_green_dark else R.color.text_secondary
            )
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStatus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
