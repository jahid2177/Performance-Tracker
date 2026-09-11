package com.performance.tracker.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.performance.tracker.databinding.ItemUserTargetMonthBinding
import com.performance.tracker.util.TargetUtils

data class MonthTargetItem(
    val month: String,
    val target: Int,
    val achieved: Int,
    val rate: Float
)

class UserTargetMonthAdapter(
    private var items: List<MonthTargetItem>
) : RecyclerView.Adapter<UserTargetMonthAdapter.ViewHolder>() {

    fun updateList(newItems: List<MonthTargetItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUserTargetMonthBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemUserTargetMonthBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MonthTargetItem) {
            binding.tvMonthItemName.text = item.month
            binding.tvMonthItemStats.text = "Target: ${item.target} | Achieved: ${item.achieved} cards"
            binding.tvMonthItemAchievement.text = TargetUtils.formatAchievementRate(item.achieved, item.target)

            val color = TargetUtils.getAchievementColor(item.achieved, item.target)
            binding.tvMonthItemAchievement.setTextColor(color)

            val progressInt = item.rate.toInt().coerceIn(0, 100)
            binding.progressMonthItem.progress = progressInt
            binding.progressMonthItem.setIndicatorColor(color)
        }
    }
}
