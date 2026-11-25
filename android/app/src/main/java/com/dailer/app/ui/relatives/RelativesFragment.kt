package com.dailer.app.ui.relatives

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dailer.app.R
import com.dailer.app.databinding.FragmentRelativesBinding
import com.google.android.material.snackbar.Snackbar

class RelativesFragment : Fragment() {

    private var _binding: FragmentRelativesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RelativesViewModel by viewModels()

    private val adapter = RelativeAdapter { relative ->
        deleteRelativeWithFeedback(relative)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRelativesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvRelatives.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRelatives.adapter = adapter

        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                @Suppress("DEPRECATION")
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val relative = adapter.getRelativeAt(position)
                    deleteRelativeWithFeedback(relative)
                }
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvRelatives)

        binding.fabAddRelative.setOnClickListener {
            findNavController().navigate(R.id.action_relatives_to_enroll)
        }

        binding.btnEmptyShareQr.setOnClickListener {
            findNavController().navigate(R.id.action_relatives_to_qr)
        }

        viewModel.relatives.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            val isEmpty = list.isNullOrEmpty()
            binding.layoutEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.rvRelatives.visibility = if (isEmpty) View.GONE else View.VISIBLE
        }
    }

    private fun deleteRelativeWithFeedback(relative: com.dailer.app.data.local.entity.RelativeEntity) {
        viewModel.removeRelative(relative)
        Snackbar.make(
            binding.root,
            getString(R.string.relative_removed, relative.name),
            Snackbar.LENGTH_SHORT
        ).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
