package com.dailer.app.ui.relatives

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dailer.app.data.local.entity.RelativeEntity
import com.dailer.app.databinding.ItemRelativeBinding
import java.util.Locale

class RelativeAdapter(
    private val onDeleteClick: (RelativeEntity) -> Unit
) : ListAdapter<RelativeEntity, RelativeAdapter.RelativeViewHolder>(DiffCallback) {

    fun getRelativeAt(position: Int): RelativeEntity = getItem(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RelativeViewHolder {
        val binding = ItemRelativeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RelativeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RelativeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RelativeViewHolder(
        private val binding: ItemRelativeBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(relative: RelativeEntity) {
            val initial = relative.name.trim()
                .firstOrNull()
                ?.uppercase(Locale.getDefault())
                ?: "?"
            binding.tvAvatarInitial.text = initial
            binding.tvRelativeName.text = relative.name
            binding.tvRelativePhone.text = relative.phoneNumber
            binding.tvRelativeRelationship.text = relative.relationship

            val badgeVisibility = if (relative.isVerified) View.VISIBLE else View.GONE
            binding.ivVerifiedBadge.visibility = badgeVisibility
            binding.tvVerifiedBadge.visibility = badgeVisibility

            binding.btnDeleteRelative.setOnClickListener {
                onDeleteClick(relative)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<RelativeEntity>() {
        override fun areItemsTheSame(oldItem: RelativeEntity, newItem: RelativeEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: RelativeEntity, newItem: RelativeEntity): Boolean {
            return oldItem == newItem
        }
    }
}
