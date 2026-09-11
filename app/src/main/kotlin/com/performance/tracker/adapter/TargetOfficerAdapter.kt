package com.performance.tracker.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.performance.tracker.databinding.ItemTargetOfficerBinding
import com.performance.tracker.model.User
import com.performance.tracker.util.ImageUtils
import com.performance.tracker.util.TargetUtils

data class TargetOfficerItem(
    val user: User,
    val target: Int,
    val achieved: Int,
    val achievementRate: Float,
    val yearlyTarget: Int = 0,
    val yearlyAchieved: Int = 0
)

class TargetOfficerAdapter(
    private var items: List<TargetOfficerItem>,
    private val onSetTargetClick: (User) -> Unit,
    private val onItemClick: ((User) -> Unit)? = null
) : RecyclerView.Adapter<TargetOfficerAdapter.ViewHolder>() {

    fun updateList(newItems: List<TargetOfficerItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTargetOfficerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemTargetOfficerBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TargetOfficerItem) {
            val user = item.user
            binding.tvTargetOfficerName.text = user.name
            binding.tvTargetOfficerIdBranch.text = "ID: ${user.employeeId} | ${user.branch.ifBlank { user.displayDepartment }}"

            // Profile image
            ImageUtils.loadProfileImage(user.profileImage, binding.ivTargetOfficerAvatar)

            if (item.yearlyTarget > 0) {
                binding.tvTargetNumbers.text = "Monthly: ${item.target} (Ach: ${item.achieved}) | Year: ${item.yearlyTarget} (Ach: ${item.yearlyAchieved})"
            } else {
                binding.tvTargetNumbers.text = "Target: ${item.target} | Achieved: ${item.achieved} cards"
            }
            binding.tvTargetPercentBadge.text = TargetUtils.formatAchievementRate(item.achieved, item.target)

            val isBelow = TargetUtils.isBelowThreshold(item.achieved, item.target)
            if (isBelow) {
                binding.tvTargetThresholdAlert.visibility = android.view.View.VISIBLE
                binding.tvTargetThresholdAlert.text = TargetUtils.getThresholdWarningBadgeText(item.achieved, item.target)
            } else {
                binding.tvTargetThresholdAlert.visibility = android.view.View.GONE
            }

            val color = TargetUtils.getAchievementColor(item.achieved, item.target)
            binding.tvTargetPercentBadge.setTextColor(color)

            val progressInt = item.achievementRate.toInt().coerceIn(0, 100)
            binding.progressTarget.progress = progressInt
            binding.progressTarget.setIndicatorColor(color)

            binding.btnItemSetTarget.setOnClickListener {
                onSetTargetClick(user)
            }

            binding.root.setOnClickListener {
                onItemClick?.invoke(user)
            }
        }
    }
}
