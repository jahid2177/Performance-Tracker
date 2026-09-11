package com.performance.tracker.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView // 🔥 এই ইম্পোর্টটি মিসিং ছিল
import com.google.firebase.firestore.FirebaseFirestore
import com.performance.tracker.R
import com.performance.tracker.adapter.MonthTargetItem
import com.performance.tracker.adapter.UserTargetMonthAdapter
import com.performance.tracker.databinding.ActivityUserDashboardBinding
import com.performance.tracker.databinding.DialogUserTargetDetailsBinding
import com.performance.tracker.model.Performance
import com.performance.tracker.model.User
import com.performance.tracker.util.TargetUtils
import com.performance.tracker.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserDashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityUserDashboardBinding
    private val viewModel: MainViewModel by viewModels()
    
    private var empId = ""
    private var empName = ""
    private var branch = ""
    private var zone = ""
    private var manager = ""
    
    private var currentUser: User? = null
    private var userReports: List<Performance> = emptyList()
    
    private var pendingSubmissions = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        empId = intent.getStringExtra("USER_ID") ?: ""
        
        setupMainDashboard() 
        setupSubmissionUI()  
        setupObservers()
        setupExitNavigation() // ব্যাক বাটন লজিক (Exit Confirmation)
        
        if(empId.isNotEmpty()) {
            viewModel.checkUser(empId)
        }
    }

    // 🔥 ১. ড্যাশবোর্ডের প্রধান অপশন সেটআপ
    private fun setupMainDashboard() {
        binding.cardSubmission.setOnClickListener {
            binding.mainOptionsLayout.visibility = View.GONE
            binding.submissionFormLayout.visibility = View.VISIBLE
            binding.tvPageTitle.text = "New Submission"
            if(binding.rowsContainer.childCount == 0) addNewRow() 
        }

        binding.cardOfficerList.setOnClickListener {
            startActivity(Intent(this, OfficerListActivity::class.java))
        }

        binding.cardMyReport.setOnClickListener {
            val intent = Intent(this, MyReportActivity::class.java).apply {
                putExtra("USER_ID", empId)
            }
            startActivity(intent)
        }

        binding.cardRanking?.setOnClickListener {
            showUserRankingDialog()
        }

        binding.cardUserTargetAchievement.setOnClickListener {
            showUserTargetDetailsDialog()
        }

        val openSettings = {
            val intent = Intent(this, SettingsActivity::class.java).apply {
                putExtra("USER_ID", empId)
                putExtra("USER_ROLE", "USER")
                putExtra("USER_NAME", empName)
            }
            startActivity(intent)
        }

        binding.btnUserSettings?.setOnClickListener {
            openSettings()
        }

        binding.cardUserSettings?.setOnClickListener {
            openSettings()
        }

        binding.btnBackToHome.setOnClickListener {
            binding.submissionFormLayout.visibility = View.GONE
            binding.mainOptionsLayout.visibility = View.VISIBLE
            binding.tvPageTitle.text = "Dashboard"
        }
    }

    // 🔥 ২. ব্যাক বাটন এবং এক্সিট ডায়ালগ লজিক
    private fun setupExitNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.submissionFormLayout.visibility == View.VISIBLE) {
                    binding.submissionFormLayout.visibility = View.GONE
                    binding.mainOptionsLayout.visibility = View.VISIBLE
                    binding.tvPageTitle.text = "Dashboard"
                } else {
                    showExitDialog()
                }
            }
        })
    }

    private fun showExitDialog() {
        AlertDialog.Builder(this)
            .setTitle("Exit App")
            .setMessage("Are you sure you want to exit?")
            .setPositiveButton("Yes") { _, _ -> finish() }
            .setNegativeButton("No", null)
            .show()
    }

    // ==========================================
    // Ranking Logic (Modern Leaderboard)
    // ==========================================
    private fun showUserRankingDialog() {
        Toast.makeText(this, "Loading Live Ranking...", Toast.LENGTH_SHORT).show()
        val db = FirebaseFirestore.getInstance()
        
        db.collection("performance").get().addOnSuccessListener { snapshot ->
            val allPerformances = snapshot.toObjects(Performance::class.java)
            val rankedList = allPerformances.groupBy { it.employeeName }
                .map { Pair(it.key, it.value.size) }
                .sortedByDescending { it.second }

            val myRankIndex = rankedList.indexOfFirst { it.first == empName }
            val myRankText = if (myRankIndex != -1) "Your Rank: ${myRankIndex + 1}" else "0 card submitted"

            val dialogView = layoutInflater.inflate(R.layout.dialog_employee_search, null)
            val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
            val etSearch = dialogView.findViewById<EditText>(R.id.etSearchEmployee)
            val recycler = dialogView.findViewById<RecyclerView>(R.id.recyclerEmployeeList)

            tvTitle.text = "Live Ranking\n$myRankText"
            tvTitle.setTextColor(Color.parseColor("#E91E63"))
            etSearch.visibility = View.GONE

            val dialog = AlertDialog.Builder(this).setView(dialogView).create()
            recycler.layoutManager = LinearLayoutManager(this)
            recycler.adapter = RankingAdapter(rankedList) 
            dialog.show()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to load ranking", Toast.LENGTH_SHORT).show()
        }
    }

    // 🔥 Modern Ranking Adapter
    inner class RankingAdapter(private val rankList: List<Pair<String, Int>>) : RecyclerView.Adapter<RankingAdapter.VH>() {
        
        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val cardBadge: MaterialCardView = view.findViewById(R.id.cardRankBadge)
            val tvNumber: TextView = view.findViewById(R.id.tvRankNumber)
            val tvName: TextView = view.findViewById(R.id.tvRankName)
            val tvScore: TextView = view.findViewById(R.id.tvRankScore)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ranking, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val rankData = rankList[position]
            val rankPosition = position + 1 
            
            holder.tvNumber.text = rankPosition.toString()
            holder.tvName.text = rankData.first
            
            // Top 3 এর জন্য গোল্ড, সিলভার এবং ব্রোঞ্জ ব্যাজ লজিক
            when (rankPosition) {
                1 -> { // Gold
                    holder.cardBadge.setCardBackgroundColor(Color.parseColor("#FFD700"))
                    holder.tvNumber.setTextColor(Color.WHITE)
                    holder.tvName.setTextColor(Color.parseColor("#D4AF37"))
                }
                2 -> { // Silver
                    holder.cardBadge.setCardBackgroundColor(Color.parseColor("#C0C0C0"))
                    holder.tvNumber.setTextColor(Color.WHITE)
                    holder.tvName.setTextColor(Color.parseColor("#757575"))
                }
                3 -> { // Bronze
                    holder.cardBadge.setCardBackgroundColor(Color.parseColor("#CD7F32"))
                    holder.tvNumber.setTextColor(Color.WHITE)
                    holder.tvName.setTextColor(Color.parseColor("#8D6E63"))
                }
                else -> { // Default
                    holder.cardBadge.setCardBackgroundColor(Color.parseColor("#F5F5F5"))
                    holder.tvNumber.setTextColor(Color.parseColor("#333333"))
                    holder.tvName.setTextColor(Color.parseColor("#212121"))
                }
            }

            // সাবমিশন কাউন্ট এবং "0 card" লজিক
            val count = rankData.second
            if (count == 0) {
                holder.tvScore.text = "0 card"
                holder.tvScore.setTextColor(Color.RED)
            } else {
                val cardText = if(count == 1) "1 card" else "$count cards"
                holder.tvScore.text = cardText
                holder.tvScore.setTextColor(Color.parseColor("#E91E63")) 
            }
        }

        override fun getItemCount() = rankList.size
    }

    // ==========================================
    // ৩. সাবমিশন ফর্ম লজিক (Zero Approval সহ)
    // ==========================================
    private fun setupSubmissionUI() {
        val months = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        val monthAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, months)
        binding.spinnerMonth.adapter = monthAdapter
        
        val currentMonth = SimpleDateFormat("MMMM", Locale.getDefault()).format(Date())
        val monthIndex = months.indexOf(currentMonth)
        if (monthIndex >= 0) binding.spinnerMonth.setSelection(monthIndex)

        // Zero Approval চেকবক্স লজিক
        binding.cbZeroApproval.setOnCheckedChangeListener { _, isChecked ->
            binding.btnAddMore.isEnabled = !isChecked
            if (isChecked) {
                binding.rowsContainer.alpha = 0.5f 
            } else {
                binding.rowsContainer.alpha = 1.0f
            }
        }

        binding.btnAddMore.setOnClickListener { addNewRow() }

        binding.btnSubmit.setOnClickListener {
            if (binding.cbZeroApproval.isChecked) {
                showConfirmDialog()
            } else if (validateInputs()) {
                showConfirmDialog()
            }
        }
    }

    private fun showConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("Confirm Submission")
            .setMessage("Are you sure you want to submit this data?")
            .setPositiveButton("Yes") { _, _ -> submitData() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun addNewRow() {
        val rowView = LayoutInflater.from(this).inflate(R.layout.item_input_row, binding.rowsContainer, false)
        binding.rowsContainer.addView(rowView)
    }

    private fun validateInputs(): Boolean {
        if (binding.cbZeroApproval.isChecked) return true

        val totalRows = binding.rowsContainer.childCount
        if (totalRows == 0) {
            Toast.makeText(this, "Please add a row or select Zero Approval", Toast.LENGTH_SHORT).show()
            return false
        }
        for (i in 0 until totalRows) {
            val row = binding.rowsContainer.getChildAt(i)
            val applicant = row.findViewById<EditText>(R.id.etRowName).text.toString().trim()
            val acNo = row.findViewById<EditText>(R.id.etRowAc).text.toString().trim()
            val limit = row.findViewById<EditText>(R.id.etRowLimit).text.toString().trim()
            if (applicant.isEmpty() || acNo.isEmpty() || limit.isEmpty()) {
                Toast.makeText(this, "Row ${i + 1} is incomplete", Toast.LENGTH_SHORT).show()
                return false
            }
        }
        return true
    }

    private fun submitData() {
        binding.btnSubmit.isEnabled = false
        binding.btnSubmit.text = "Submitting..."
        
        if (binding.cbZeroApproval.isChecked) {
            val nilPerformance = Performance(
                employeeId = empId,
                employeeName = empName,
                branch = branch,
                zone = zone,
                salesManager = manager,
                month = binding.spinnerMonth.selectedItem.toString(),
                applicantName = "N/A",
                accountNo = "N/A",
                limit = "NIL",
                timestamp = System.currentTimeMillis()
            )
            pendingSubmissions = 1
            viewModel.submitPerformance(nilPerformance)
        } else {
            val totalRows = binding.rowsContainer.childCount
            pendingSubmissions = totalRows
            for (i in 0 until totalRows) {
                val row = binding.rowsContainer.getChildAt(i)
                val applicantName = row.findViewById<EditText>(R.id.etRowName).text.toString().trim()
                val accountNo = row.findViewById<EditText>(R.id.etRowAc).text.toString().trim()
                val limit = row.findViewById<EditText>(R.id.etRowLimit).text.toString().trim()

                val performance = Performance(
                    employeeId = empId,
                    employeeName = empName,
                    branch = branch,
                    zone = zone,
                    salesManager = manager,
                    month = binding.spinnerMonth.selectedItem.toString(),
                    applicantName = applicantName,
                    accountNo = accountNo,
                    limit = limit,
                    timestamp = System.currentTimeMillis() + i
                )
                viewModel.submitPerformance(performance)
            }
        }
    }

    private fun setupObservers() {
        viewModel.userState.observe(this) { user ->
            if (user != null) {
                currentUser = user
                empName = user.name
                branch = user.branch
                zone = user.zone
                manager = user.salesManager
                binding.tvEmpName.text = "Welcome, $empName"
                loadUserTargetAndAchievement(user)
            }
        }

        viewModel.submissionStatus.observe(this) { success ->
            if(success && pendingSubmissions > 0) {
                pendingSubmissions--
                if (pendingSubmissions == 0) {
                    Toast.makeText(this, "Submitted Successfully!", Toast.LENGTH_LONG).show()
                    binding.rowsContainer.removeAllViews()
                    binding.cbZeroApproval.isChecked = false
                    addNewRow()
                    binding.btnSubmit.isEnabled = true
                    binding.btnSubmit.text = "SUBMIT"
                    currentUser?.let { loadUserTargetAndAchievement(it) }
                }
            }
        }
    }

    private fun loadUserTargetAndAchievement(user: User) {
        val currentMonth = SimpleDateFormat("MMMM", Locale.US).format(Date())
        binding.tvUserTargetMonthTitle.text = "$currentMonth Target"

        FirebaseFirestore.getInstance().collection("performance")
            .whereEqualTo("employeeId", user.employeeId)
            .get()
            .addOnSuccessListener { snapshot ->
                userReports = snapshot.toObjects(Performance::class.java)
                val target = user.monthlyTarget
                val achieved = TargetUtils.countCards(userReports, user.employeeId, currentMonth)
                val rate = TargetUtils.calculateAchievementRate(achieved, target)

                binding.tvUserTargetSummary.text = "Target: $target | Achieved: $achieved cards"
                binding.tvUserAchievementPercentBadge.text = TargetUtils.formatAchievementRate(achieved, target)

                val color = TargetUtils.getAchievementColor(achieved, target)
                binding.tvUserAchievementPercentBadge.setTextColor(color)

                val progress = rate.toInt().coerceIn(0, 100)
                binding.progressUserTarget.progress = progress
                binding.progressUserTarget.setIndicatorColor(color)

                binding.tvUserTargetMotivation.text = when {
                    target <= 0 -> "No target assigned by admin yet"
                    rate >= 100f -> "🎉 Target Achieved! Great job!"
                    rate >= 75f -> "Almost there! ${target - achieved} cards to reach 100%"
                    else -> "${target - achieved} more cards needed to reach goal"
                }
            }
    }

    private fun showUserTargetDetailsDialog() {
        val user = currentUser ?: return
        val detailsBinding = DialogUserTargetDetailsBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(detailsBinding.root)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        detailsBinding.tvUserTargetDetailsTitle.text = "My Target & Progress"
        detailsBinding.tvUserTargetDetailsSubtitle.text = "ID: ${user.employeeId} | ${user.branch.ifBlank { user.displayDepartment }}"
        detailsBinding.btnCloseUserTargetDetails.setOnClickListener { dialog.dismiss() }

        val currentMonth = SimpleDateFormat("MMMM", Locale.US).format(Date())
        detailsBinding.tvDetailCurrentMonthName.text = "$currentMonth Target"

        val target = user.monthlyTarget
        val currentAchieved = TargetUtils.countCards(userReports, user.employeeId, currentMonth)
        val currentRate = TargetUtils.calculateAchievementRate(currentAchieved, target)

        detailsBinding.tvDetailCurrentTargetCount.text = "Target: $target Cards"
        detailsBinding.tvDetailCurrentAchievedCount.text = "Achieved: $currentAchieved Cards"
        detailsBinding.tvDetailCurrentAchievementPercent.text = TargetUtils.formatAchievementRate(currentAchieved, target)

        val currentColor = TargetUtils.getAchievementColor(currentAchieved, target)
        detailsBinding.tvDetailCurrentAchievementPercent.setTextColor(currentColor)
        detailsBinding.progressDetailCurrent.progress = currentRate.toInt().coerceIn(0, 100)
        detailsBinding.progressDetailCurrent.setIndicatorColor(currentColor)

        val months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        val monthlyItems = months.map { m ->
            val ach = TargetUtils.countCards(userReports, user.employeeId, m)
            val r = TargetUtils.calculateAchievementRate(ach, target)
            MonthTargetItem(m, target, ach, r)
        }

        val adapter = UserTargetMonthAdapter(monthlyItems)
        detailsBinding.recyclerUserTargetMonths.layoutManager = LinearLayoutManager(this)
        detailsBinding.recyclerUserTargetMonths.adapter = adapter

        dialog.show()
    }
}
