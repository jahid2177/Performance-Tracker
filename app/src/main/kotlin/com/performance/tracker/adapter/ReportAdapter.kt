package com.performance.tracker.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.performance.tracker.databinding.ItemReportBinding
import com.performance.tracker.model.ReportSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportAdapter(
    private var list: List<ReportSummary>,
    private val canEdit: Boolean = false,
    private val canDelete: Boolean = false,
    private val onCardClick: ((ReportSummary) -> Unit)? = null,
    private val onActionClick: ((ReportSummary, String) -> Unit)? = null
) : RecyclerView.Adapter<ReportAdapter.ReportViewHolder>() {

    inner class ReportViewHolder(val binding: ItemReportBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val binding = ItemReportBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReportViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        val item = list[position]
        holder.binding.apply {
            tvUserName.text = "Name: ${item.employeeName}"
            tvMonth.text = item.month
            
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val dateStr = sdf.format(Date(item.timestamp))
            val count = item.totalRecords

            // 🔥 0 card হলে লাল কালার, নাহলে সাধারণ কালার (কার্ড/কার্ডস লজিক)
            if (count == 0) {
                tvDate.text = "Submitted: $dateStr (0 card)"
                tvDate.setTextColor(Color.RED)
            } else {
                val cardText = if (count == 1) "1 card" else "$count cards"
                tvDate.text = "Submitted: $dateStr ($cardText)"
                tvDate.setTextColor(Color.parseColor("#666666")) // ডিফল্ট গ্রে কালার
            }

            // পুরো কার্ডে ক্লিক করলে বিস্তারিত পেজে যাবে
            root.setOnClickListener { onCardClick?.invoke(item) }

            if (canEdit) {
                btnEdit.visibility = View.VISIBLE
                btnEdit.setOnClickListener { onActionClick?.invoke(item, "EDIT") }
            } else {
                btnEdit.visibility = View.GONE
            }

            if (canDelete) {
                btnDelete.visibility = View.VISIBLE
                btnDelete.setOnClickListener { onActionClick?.invoke(item, "DELETE") }
            } else {
                btnDelete.visibility = View.GONE
            }
        }
    }

    override fun getItemCount() = list.size

    fun updateList(newList: List<ReportSummary>) {
        list = newList
        notifyDataSetChanged()
    }
}
