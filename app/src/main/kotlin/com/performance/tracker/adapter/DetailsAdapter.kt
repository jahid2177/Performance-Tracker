package com.performance.tracker.adapter

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.performance.tracker.R
import com.performance.tracker.model.Performance

class DetailsAdapter(private val list: List<Performance>) : RecyclerView.Adapter<DetailsAdapter.DetailsVH>() {

    inner class DetailsVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvSl: TextView = view.findViewById(R.id.tvSlNo)
        val tvName: TextView = view.findViewById(R.id.tvApplicantName)
        val tvAc: TextView = view.findViewById(R.id.tvAccountNo)
        val tvLimit: TextView = view.findViewById(R.id.tvLimit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetailsVH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_table_row, parent, false)
        return DetailsVH(view)
    }

    override fun onBindViewHolder(holder: DetailsVH, position: Int) {
        val item = list[position]
        
        holder.tvSl.text = (position + 1).toString()
        holder.tvName.text = item.applicantName
        holder.tvAc.text = item.accountNo
        
        // 🔥 NIL হ্যান্ডেলিং এবং কালার লজিক
        if (item.limit.equals("NIL", ignoreCase = true)) {
            holder.tvLimit.text = "NIL"
            holder.tvLimit.setTextColor(Color.RED) // NIL হলে লাল দেখাবে
            holder.tvLimit.setTypeface(null, Typeface.BOLD) // বোল্ড করা হলো
        } else {
            // সাধারণ লিমিট নাম্বার ফরম্যাটিং (যেমন: 50,000)
            val limitNum = item.limit.replace(",", "").toDoubleOrNull()
            if (limitNum != null) {
                holder.tvLimit.text = String.format("%,.0f", limitNum)
            } else {
                holder.tvLimit.text = item.limit
            }
            holder.tvLimit.setTextColor(Color.parseColor("#2E7D32")) // সাধারণ ক্ষেত্রে গাঢ় সবুজ
            holder.tvLimit.setTypeface(null, Typeface.BOLD)
        }

        // জোড়-বিজোড় সারিতে হালকা আলাদা রং (Zebra Stripe Layout)
        
        if (position % 2 == 0) {
            holder.itemView.setBackgroundColor(Color.parseColor("#FFFFFF"))
        } else {
            holder.itemView.setBackgroundColor(Color.parseColor("#F5F5F5"))
        }
    }

    override fun getItemCount() = list.size
}
