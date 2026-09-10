package com.performance.tracker.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.performance.tracker.databinding.ItemUserCardBinding
import com.performance.tracker.model.User

class UserAdapter(
    private var list: List<User>,
    private val isApprovalMode: Boolean,
    private val onCardClick: ((User) -> Unit)? = null, // 🔥 কার্ডে ক্লিকের জন্য নতুন লজিক
    private val onActionClick: (User, String) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    inner class UserViewHolder(val binding: ItemUserCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = list[position]
        holder.binding.apply {
            tvUserName.text = user.name
            tvUserId.text = "ID: ${user.employeeId}"
            tvUserLocation.text = "Branch: ${user.branch} | Zone: ${user.zone}"

            if (isApprovalMode) {
                // Approval Screen: Approve (Green) & Reject (Red)
                btnPositive.text = "APPROVE"
                btnNegative.text = "REJECT"
                btnPositive.visibility = View.VISIBLE
            } else {
                // Officer List Screen: No Approve, only Delete
                btnPositive.visibility = View.GONE
                btnNegative.text = "DELETE USER"
            }

            btnPositive.setOnClickListener { onActionClick(user, "APPROVE") }
            btnNegative.setOnClickListener { onActionClick(user, "DELETE") }

            // 🔥 কার্ডের যেকোনো জায়গায় ক্লিক করলে প্রোফাইল ডায়ালগ দেখাবে
            root.setOnClickListener { onCardClick?.invoke(user) }
        }
    }

    override fun getItemCount() = list.size

    fun updateList(newList: List<User>) {
        list = newList
        notifyDataSetChanged()
    }
}
