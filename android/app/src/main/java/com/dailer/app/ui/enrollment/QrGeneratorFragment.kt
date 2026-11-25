package com.dailer.app.ui.enrollment

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.dailer.app.R
import com.dailer.app.databinding.FragmentQrGeneratorBinding
import com.dailer.app.util.AudioUtils
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.util.UUID

class QrGeneratorFragment : Fragment() {

    private var _binding: FragmentQrGeneratorBinding? = null
    private val binding get() = _binding!!

    private var currentDeepLink: String? = null
    private var currentRelativeName: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQrGeneratorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnGenerateQr.setOnClickListener {
            generateAndRenderQr()
        }

        binding.btnShareQr.setOnClickListener {
            shareInviteLink()
        }
    }

    private fun generateAndRenderQr() {
        val name = binding.etRelativeName.text?.toString()?.trim().orEmpty()
        val rawPhone = binding.etRelativePhone.text?.toString()?.trim().orEmpty()
        val relationship = binding.etRelativeRelationship.text?.toString()?.trim()
            .orEmpty()
            .ifBlank { "Family" }

        var hasError = false
        if (name.isEmpty()) {
            binding.tilRelativeName.error = getString(R.string.enroll_name_hint)
            hasError = true
        } else {
            binding.tilRelativeName.error = null
        }

        if (rawPhone.isEmpty()) {
            binding.tilRelativePhone.error = getString(R.string.enroll_phone_hint)
            hasError = true
        } else {
            binding.tilRelativePhone.error = null
        }

        if (hasError) return

        val normalizedPhone = AudioUtils.normalizePhoneNumber(rawPhone)
        val token = UUID.randomUUID().toString()

        val deepLink = Uri.Builder()
            .scheme("dailer")
            .authority("enroll")
            .appendQueryParameter("token", token)
            .appendQueryParameter("name", name)
            .appendQueryParameter("phone", normalizedPhone)
            .appendQueryParameter("relationship", relationship)
            .build()
            .toString()

        runCatching {
            BarcodeEncoder().encodeBitmap(
                deepLink,
                BarcodeFormat.QR_CODE,
                512,
                512
            )
        }.onSuccess { bitmap: Bitmap ->
            currentDeepLink = deepLink
            currentRelativeName = name
            binding.ivQrCode.setImageBitmap(bitmap)
            binding.tvInviteToken.text = deepLink
            binding.cardQrPreview.visibility = View.VISIBLE
            binding.btnShareQr.visibility = View.VISIBLE
            binding.btnGenerateQr.setText(R.string.btn_regenerate_qr)
        }.onFailure { error ->
            Toast.makeText(
                requireContext(),
                error.message ?: "Failed to generate QR code",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun shareInviteLink() {
        val deepLink = currentDeepLink ?: return
        val name = currentRelativeName.ifBlank { "Family Member" }
        val message = getString(R.string.qr_share_message, name, deepLink)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.qr_share_subject))
            putExtra(Intent.EXTRA_TEXT, message)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.btn_share_invite)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
