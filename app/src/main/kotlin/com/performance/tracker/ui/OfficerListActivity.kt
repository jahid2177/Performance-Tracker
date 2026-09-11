package com.performance.tracker.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.FirebaseFirestore
import com.performance.tracker.R
import com.performance.tracker.databinding.ActivityOfficerListBinding 
import com.performance.tracker.model.Performance
import com.performance.tracker.model.User
import com.performance.tracker.util.TargetUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OfficerListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOfficerListBinding
    private val db = FirebaseFirestore.getInstance()
    private lateinit var adapter: OfficerListAdapter
    private val allOfficers = mutableListOf<User>()
    private val displayedOfficers = mutableListOf<User>()
    private var allPerformances = listOf<Performance>()
    private var selectedDepartment: String = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOfficerListBinding.inflate(layoutInflater) 
        setContentView(binding.root)

        binding.btnBack?.setOnClickListener { finish() }
        
        setupRecyclerView()
        setupSearchBar()
        setupResetButtons()
        loadApprovedOfficers()
    }

    private fun setupRecyclerView() {
        adapter = OfficerListAdapter(displayedOfficers) { user ->
            confirmDeleteOfficer(user)
        }
        binding.recyclerOfficers.layoutManager = LinearLayoutManager(this)
        binding.recyclerOfficers.adapter = adapter
    }

    private fun setupSearchBar() {
        binding.etSearchOfficers.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrEmpty()
                binding.btnClearSearch.visibility = if (hasText) View.VISIBLE else View.GONE
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearchOfficers.setText("")
        }
    }

    private fun setupResetButtons() {
        binding.btnResetFilters.setOnClickListener { resetFilters() }
        binding.btnEmptyReset.setOnClickListener { resetFilters() }
    }

    private fun resetFilters() {
        binding.etSearchOfficers.setText("")
        selectedDepartment = "All"
        setupDepartmentChips()
        applyFilters()
    }

    private fun loadApprovedOfficers() {
        binding.progressBar.visibility = View.VISIBLE 

        // Query all employees and filter officers safely (case-insensitive & doc id fallback)
        db.collection("employees") 
            .get()
            .addOnSuccessListener { snapshot ->
                binding.progressBar.visibility = View.GONE 
                allOfficers.clear()
                
                for (doc in snapshot.documents) {
                    val user = doc.toObject(User::class.java)
                    if (user != null) {
                        val effectiveUser = if (user.employeeId.isBlank()) user.copy(employeeId = doc.id) else user
                        val isOfficer = effectiveUser.role.isBlank() || effectiveUser.role.equals("USER", ignoreCase = true)
                        val isApproved = effectiveUser.status.isBlank() ||
                                         effectiveUser.status.equals("APPROVED", ignoreCase = true) ||
                                         effectiveUser.status.equals("Approved", ignoreCase = true)
                        if (isOfficer && isApproved) {
                            allOfficers.add(effectiveUser)
                        }
                    }
                }

                allOfficers.sortBy { it.name.lowercase() }
                binding.tvTotalBadge.text = "${allOfficers.size} Total"

                db.collection("performance").get()
                    .addOnSuccessListener { perfSnapshot ->
                        allPerformances = perfSnapshot.documents.mapNotNull { doc ->
                            doc.toObject(Performance::class.java)?.apply { id = doc.id }
                        }
                        setupDepartmentChips()
                        applyFilters()
                    }
                    .addOnFailureListener {
                        setupDepartmentChips()
                        applyFilters()
                    }
            }
            .addOnFailureListener { exception ->
                binding.progressBar.visibility = View.GONE 
                Toast.makeText(this, "Error: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun setupDepartmentChips() {
        binding.chipGroupDepartments.removeAllViews()

        val currentMonth = SimpleDateFormat("MMMM", Locale.US).format(Date())
        val belowThresholdCount = allOfficers.count { user ->
            val target = user.monthlyTarget
            val achieved = TargetUtils.countCards(allPerformances, user.employeeId, currentMonth)
            user.isTargetEligible && TargetUtils.isBelowThreshold(achieved, target)
        }

        val departments = allOfficers.map { it.displayDepartment.trim() }
            .filter { it.isNotEmpty() && !it.equals("N/A", ignoreCase = true) }
            .distinct()
            .sorted()

        // 1. "All" Chip
        val allChip = createFilterChip(
            displayText = "All (${allOfficers.size})",
            isChecked = selectedDepartment.equals("All", ignoreCase = true),
            deptTag = "All"
        )
        binding.chipGroupDepartments.addView(allChip)

        // 2. "<50% Target Alert" Chip (if any exist)
        if (belowThresholdCount > 0) {
            val alertChip = createFilterChip(
                displayText = "⚠️ <50% Target ($belowThresholdCount)",
                isChecked = selectedDepartment.equals("BELOW_50", ignoreCase = true),
                deptTag = "BELOW_50"
            )
            binding.chipGroupDepartments.addView(alertChip)
        }

        // 3. Department-specific Chips
        departments.forEach { dept ->
            val count = allOfficers.count { it.displayDepartment.equals(dept, ignoreCase = true) }
            val deptChip = createFilterChip(
                displayText = "$dept ($count)",
                isChecked = selectedDepartment.equals(dept, ignoreCase = true),
                deptTag = dept
            )
            binding.chipGroupDepartments.addView(deptChip)
        }

        binding.chipGroupDepartments.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) {
                selectedDepartment = "All"
                allChip.isChecked = true
            } else {
                val selectedChip = group.findViewById<Chip>(checkedIds.first())
                val tag = selectedChip?.tag as? String
                selectedDepartment = tag ?: "All"
            }
            applyFilters()
        }
    }

    private fun createFilterChip(displayText: String, isChecked: Boolean, deptTag: String): Chip {
        val density = resources.displayMetrics.density
        return Chip(this).apply {
            text = displayText
            tag = deptTag
            isCheckable = true
            isClickable = true
            this.isChecked = isChecked
            isCheckedIconVisible = false
            id = View.generateViewId()

            setChipBackgroundColorResource(R.color.chip_bg_selector)
            setTextColor(ContextCompat.getColorStateList(this@OfficerListActivity, R.color.chip_text_selector))
            setChipStrokeColorResource(R.color.chip_stroke_selector)
            chipStrokeWidth = 1f * density
            chipCornerRadius = 16f * density
            textSize = 13f
        }
    }

    private fun applyFilters() {
        val query = binding.etSearchOfficers.text?.toString()?.trim()?.lowercase().orEmpty()
        val isAllDept = selectedDepartment.equals("All", ignoreCase = true)
        val isBelow50Filter = selectedDepartment.equals("BELOW_50", ignoreCase = true)
        val currentMonth = SimpleDateFormat("MMMM", Locale.US).format(Date())

        val filtered = allOfficers.filter { user ->
            val matchesQuery = query.isEmpty() ||
                user.name.lowercase().contains(query) ||
                user.employeeId.lowercase().contains(query) ||
                user.branch.lowercase().contains(query) ||
                user.zone.lowercase().contains(query) ||
                user.department.lowercase().contains(query) ||
                user.salesManager.lowercase().contains(query)

            val matchesDepartment = when {
                isBelow50Filter -> {
                    val target = user.monthlyTarget
                    val achieved = TargetUtils.countCards(allPerformances, user.employeeId, currentMonth)
                    user.isTargetEligible && TargetUtils.isBelowThreshold(achieved, target)
                }
                isAllDept -> true
                else -> user.displayDepartment.equals(selectedDepartment, ignoreCase = true) ||
                        user.branch.equals(selectedDepartment, ignoreCase = true) ||
                        user.zone.equals(selectedDepartment, ignoreCase = true) ||
                        user.department.equals(selectedDepartment, ignoreCase = true)
            }

            matchesQuery && matchesDepartment
        }

        displayedOfficers.clear()
        displayedOfficers.addAll(filtered)
        adapter.notifyDataSetChanged()

        val hasActiveFilter = query.isNotEmpty() || !isAllDept
        binding.btnResetFilters.visibility = if (hasActiveFilter) View.VISIBLE else View.GONE

        binding.tvOfficerCount.text = if (hasActiveFilter) {
            "Showing ${filtered.size} of ${allOfficers.size} employees"
        } else {
            "Officers (${allOfficers.size})"
        }

        if (filtered.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.recyclerOfficers.visibility = View.GONE

            if (hasActiveFilter) {
                binding.tvEmptyTitle.text = "No Matching Employees"
                val deptNote = when {
                    isBelow50Filter -> " below 50% target threshold"
                    !isAllDept -> " in '$selectedDepartment'"
                    else -> ""
                }
                val searchNote = if (query.isNotEmpty()) " matching '$query'" else ""
                binding.tvEmpty.text = "No employees found$searchNote$deptNote. Try a different search keyword or department filter."
                binding.btnEmptyReset.visibility = View.VISIBLE
            } else {
                binding.tvEmptyTitle.text = "No Officers Found"
                binding.tvEmpty.text = "No approved officers are currently listed in the system."
                binding.btnEmptyReset.visibility = View.GONE
            }
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.recyclerOfficers.visibility = View.VISIBLE
        }
    }

    private fun confirmDeleteOfficer(user: User) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Warning: Delete Employee")
            .setMessage("Are you sure you want to delete ${user.name} (ID: ${user.employeeId}) from the system?\n\nThis will remove their profile and records permanently.")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Yes, Delete") { _, _ ->
                db.collection("employees").document(user.employeeId) 
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "${user.name} removed successfully", Toast.LENGTH_SHORT).show()
                        loadApprovedOfficers() 
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to remove: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ==========================================
    // 🔥 Adapter for Officer List
    // ==========================================
    inner class OfficerListAdapter(
        private val list: List<User>,
        private val onDeleteClick: (User) -> Unit
    ) : RecyclerView.Adapter<OfficerListAdapter.OfficerVH>() {

        inner class OfficerVH(view: View) : RecyclerView.ViewHolder(view) {
            val ivAvatar: ImageView = view.findViewById(R.id.ivOfficerAvatar)
            val tvName: TextView = view.findViewById(R.id.tvOfficerName)
            val tvId: TextView = view.findViewById(R.id.tvOfficerId)
            val tvBranchZone: TextView = view.findViewById(R.id.tvBranchZone)
            val tvOfficerDepartmentBadge: TextView? = view.findViewById(R.id.tvOfficerDepartmentBadge)
            val layoutTargetAchievement: View? = view.findViewById(R.id.layoutOfficerTargetAchievement)
            val tvTargetBadge: TextView? = view.findViewById(R.id.tvOfficerTargetBadge)
            val tvAchievementBadge: TextView? = view.findViewById(R.id.tvOfficerAchievementBadge)
            val tvThresholdAlert: TextView? = view.findViewById(R.id.tvOfficerThresholdAlert)
            val btnDelete: MaterialCardView = view.findViewById(R.id.btnDeleteOfficer)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OfficerVH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_officer, parent, false)
            return OfficerVH(view)
        }

        override fun onBindViewHolder(holder: OfficerVH, position: Int) {
            val user = list[position]
            com.performance.tracker.util.ImageUtils.loadProfileImage(user.profileImage, holder.ivAvatar)
            holder.tvName.text = user.name
            holder.tvId.text = "ID: ${user.employeeId}"
            
            // Display department badge
            holder.tvOfficerDepartmentBadge?.text = user.displayDepartment
            
            val branchText = if (user.branch.isNotBlank() && !user.branch.equals("N/A", ignoreCase = true)) user.branch else "Main Branch"
            val zoneText = if (user.zone.isNotBlank() && !user.zone.equals("N/A", ignoreCase = true)) " | Zone: ${user.zone}" else ""
            holder.tvBranchZone.text = "Branch: $branchText$zoneText"

            // Target & Achievement Info (Only for target-eligible employees)
            val currentMonth = SimpleDateFormat("MMMM", Locale.US).format(Date())
            val target = user.monthlyTarget
            val achieved = TargetUtils.countCards(allPerformances, user.employeeId, currentMonth)

            if (user.isTargetEligible && (target > 0 || achieved > 0)) {
                holder.layoutTargetAchievement?.visibility = View.VISIBLE
                holder.tvTargetBadge?.text = "Target: $target"
                holder.tvAchievementBadge?.text = TargetUtils.formatAchievementRate(achieved, target)
                val achColor = TargetUtils.getAchievementColor(achieved, target)
                holder.tvAchievementBadge?.setTextColor(achColor)

                val isBelow = TargetUtils.isBelowThreshold(achieved, target)
                if (isBelow) {
                    holder.tvThresholdAlert?.visibility = View.VISIBLE
                    holder.tvThresholdAlert?.text = TargetUtils.getThresholdWarningBadgeText(achieved, target)
                } else {
                    holder.tvThresholdAlert?.visibility = View.GONE
                }
            } else {
                holder.layoutTargetAchievement?.visibility = View.GONE
                holder.tvThresholdAlert?.visibility = View.GONE
            }

            val isAdmin = com.performance.tracker.util.SessionManager.getUserRole(this@OfficerListActivity).equals("ADMIN", ignoreCase = true)
            holder.btnDelete.visibility = if (isAdmin) View.VISIBLE else View.GONE

            // Open Profile Activity
            holder.itemView.setOnClickListener {
                val intent = Intent(this@OfficerListActivity, ProfileActivity::class.java)
                intent.putExtra("USER_ID", user.employeeId)
                startActivity(intent)
            }

            holder.btnDelete.setOnClickListener {
                if (isAdmin) {
                    onDeleteClick(user)
                }
            }
        }

        override fun getItemCount() = list.size
    }
}

