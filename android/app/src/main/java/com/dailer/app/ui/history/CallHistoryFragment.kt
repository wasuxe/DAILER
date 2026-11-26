package com.dailer.app.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.dailer.app.databinding.FragmentCallHistoryBinding

class CallHistoryFragment : Fragment() {

    private var _binding: FragmentCallHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CallHistoryViewModel by viewModels()
    private val adapter = CallHistoryAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCallHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvCallHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCallHistory.adapter = adapter

        viewModel.relatives.observe(viewLifecycleOwner) { relatives ->
            adapter.updateRelatives(relatives.orEmpty())
        }

        viewModel.callRecords.observe(viewLifecycleOwner) { records ->
            adapter.submitList(records)
            val empty = records.isNullOrEmpty()
            binding.tvEmptyHistory.visibility = if (empty) View.VISIBLE else View.GONE
            binding.rvCallHistory.visibility = if (empty) View.GONE else View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
