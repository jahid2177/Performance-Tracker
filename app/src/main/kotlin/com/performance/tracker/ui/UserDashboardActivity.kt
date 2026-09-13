package com.performance.tracker.ui

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView // 🔥 এই ইম্পোর্টটি মিসিং ছিল
import com.google.firebase.firestore.FirebaseFirestore
import com.performance.tracker.R
import com.performance.tracker.adapter.MonthTargetItem
import com.performance.tracker.adapter.UserTargetMonthAdapter
import com.performance.tracker.databinding.ActivityUserDashboardBinding
import com.performance.tracker.databinding.DialogPdfLoadingBinding
import com.performance.tracker.databinding.DialogUserTargetDetailsBinding
import com.performance.tracker.model.Performance
import com.performance.tracker.model.User
import com.performance.tracker.util.ParsedKpiAccount
import com.performance.tracker.util.PdfKpiParser
import com.performance.tracker.util.TargetUtils
import com.performance.tracker.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    // PDF Import Data
    private var importedPdfAccounts: List<ParsedKpiAccount> = emptyList()
    private var importedPdfAccountsByMonth: Map<String, List<ParsedKpiAccount>> = emptyMap()

    private val pickPdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { processPdfUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        empId = intent.getStringExtra("USER_ID") ?: ""
        
        setupMainDashboard() 
        setupSubmissionUI()  
        setupObservers()
        setupExitNavigation() // ব্যাক বাটন লজিক (Exit Confirmation)
        setupSyncStatus()
        
        if(empId.isNotEmpty()) {
            viewModel.checkUser(empId)
        }
    }

    override fun onResume() {
        super.onResume()
        checkSettingsRedDot()
        com.performance.tracker.util.UpdateManager.checkForAppUpdate(this)
        com.performance.tracker.util.OfflineSyncManager.syncPendingReports(this, showIndicator = false)
    }

    private fun setupSyncStatus() {
        binding.btnUserSyncStatus.setOnClickListener {
            binding.ivUserSyncIcon.animate().rotationBy(360f).setDuration(600).start()
            com.performance.tracker.util.OfflineSyncManager.performFullSync(this, showIndicator = true)
        }

        com.performance.tracker.util.OfflineSyncManager.syncState.observe(this) { state ->
            when (state) {
                is com.performance.tracker.util.OfflineSyncManager.SyncState.Syncing -> {
                    binding.ivUserSyncIcon.setColorFilter(android.graphics.Color.parseColor("#3B82F6"))
                    binding.ivUserSyncIcon.animate().rotationBy(180f).setDuration(400).start()
                }
                is com.performance.tracker.util.OfflineSyncManager.SyncState.Synced -> {
                    binding.ivUserSyncIcon.setColorFilter(android.graphics.Color.parseColor("#15803D"))
                }
                is com.performance.tracker.util.OfflineSyncManager.SyncState.Error -> {
                    binding.ivUserSyncIcon.setColorFilter(android.graphics.Color.parseColor("#DC2626"))
                }
                is com.performance.tracker.util.OfflineSyncManager.SyncState.Idle -> {
                    binding.ivUserSyncIcon.setColorFilter(android.graphics.Color.parseColor("#15803D"))
                }
            }
        }
    }

    private fun checkSettingsRedDot() {
        val email = com.performance.tracker.util.SessionManager.getUserEmail(this)
        if (email.isNotBlank()) {
            binding.viewUserSettingsRedDot.visibility = View.GONE
        } else {
            val userId = if (empId.isNotBlank()) empId else com.performance.tracker.util.SessionManager.getUserId(this)
            if (userId.isNotEmpty()) {
                com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("employees").document(userId).get()
                    .addOnSuccessListener { doc ->
                        val remoteEmail = doc.getString("email") ?: ""
                        if (remoteEmail.isNotBlank()) {
                            com.performance.tracker.util.SessionManager.saveUser(this, userId, "USER", empName, remoteEmail)
                            binding.viewUserSettingsRedDot.visibility = View.GONE
                        } else {
                            binding.viewUserSettingsRedDot.visibility = View.VISIBLE
                        }
                    }
                    .addOnFailureListener {
                        binding.viewUserSettingsRedDot.visibility = View.VISIBLE
                    }
            } else {
                binding.viewUserSettingsRedDot.visibility = View.VISIBLE
            }
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
            .setIcon(R.drawable.ic_logout)
            .setPositiveButton("Yes") { _, _ -> finishAffinity() }
            .setNegativeButton("No", null)
            .show()
    }

    // ==========================================
    // Ranking Logic (Modern Fullscreen Leaderboard)
    // ==========================================
    private fun showUserRankingDialog() {
        Toast.makeText(this, "Loading Live Ranking...", Toast.LENGTH_SHORT).show()
        val db = FirebaseFirestore.getInstance()
        
        db.collection("employees").get().addOnSuccessListener { empSnapshot ->
            val allEmployees = empSnapshot.documents.mapNotNull { doc ->
                val u = doc.toObject(User::class.java)
                if (u != null) {
                    val effective = if (u.employeeId.isBlank()) u.copy(employeeId = doc.id) else u
                    val isApproved = effective.status.isBlank() ||
                                     effective.status.trim().equals("Approved", ignoreCase = true)
                    val isOfficer = effective.role.isBlank() || effective.role.trim().equals("USER", ignoreCase = true)
                    if (isApproved && isOfficer) effective else null
                } else null
            }

            db.collection("performance").get().addOnSuccessListener { snapshot ->
                val allPerformances = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Performance::class.java)?.apply { id = doc.id }
                }

                val currentMonth = SimpleDateFormat("MMMM", Locale.US).format(Date())

                val rawRanked = allEmployees.map { emp ->
                    val count = TargetUtils.countCards(allPerformances, emp.employeeId, currentMonth)
                    Pair(emp, count)
                }.sortedWith(compareByDescending<Pair<User, Int>> { it.second }.thenBy { it.first.name })

                val rankedList = rawRanked.mapIndexed { index, pair ->
                    AdminDashboardActivity.ModernRankingEntry(
                        rank = index + 1,
                        employeeId = pair.first.employeeId,
                        employeeName = pair.first.name,
                        branch = pair.first.branch,
                        zone = pair.first.zone,
                        score = pair.second
                    )
                }

                val dialog = Dialog(this, R.style.Theme_FullScreenDialog)
                val dialogView = layoutInflater.inflate(R.layout.dialog_fullscreen_ranking, null)
                dialog.setContentView(dialogView)

                dialog.window?.apply {
                    setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundDrawable(ColorDrawable(Color.parseColor("#F8FAFC")))
                }

                val btnClose = dialogView.findViewById<ImageButton>(R.id.btnRankingClose)
                val tvTotalRankedCount = dialogView.findViewById<TextView>(R.id.tvTotalRankedCount)
                val tvPodiumRank1Name = dialogView.findViewById<TextView>(R.id.tvPodiumRank1Name)
                val tvPodiumRank1Score = dialogView.findViewById<TextView>(R.id.tvPodiumRank1Score)
                val tvPodiumRank2Name = dialogView.findViewById<TextView>(R.id.tvPodiumRank2Name)
                val tvPodiumRank2Score = dialogView.findViewById<TextView>(R.id.tvPodiumRank2Score)
                val tvPodiumRank3Name = dialogView.findViewById<TextView>(R.id.tvPodiumRank3Name)
                val tvPodiumRank3Score = dialogView.findViewById<TextView>(R.id.tvPodiumRank3Score)
                
                val cardMyRank = dialogView.findViewById<MaterialCardView>(R.id.cardMyRankHighlight)
                val tvMyRankBadge = dialogView.findViewById<TextView>(R.id.tvMyRankBadge)
                val tvMyRankName = dialogView.findViewById<TextView>(R.id.tvMyRankName)
                val tvMyRankDetails = dialogView.findViewById<TextView>(R.id.tvMyRankDetails)
                val tvMyRankScore = dialogView.findViewById<TextView>(R.id.tvMyRankScore)

                val etSearch = dialogView.findViewById<EditText>(R.id.etRankingSearch)
                val btnClearSearch = dialogView.findViewById<ImageButton>(R.id.btnClearRankingSearch)
                val recycler = dialogView.findViewById<RecyclerView>(R.id.recyclerFullscreenRanking)
                val layoutEmpty = dialogView.findViewById<LinearLayout>(R.id.layoutRankingEmpty)

                btnClose.setOnClickListener { dialog.dismiss() }
                tvTotalRankedCount.text = "${rankedList.size} Officers"

                // Highlights for User
                val cleanEmpId = empId.trim()
                val cleanEmpName = empName.trim()
                val myRankIndex = rankedList.indexOfFirst { 
                    it.employeeId.trim().equals(cleanEmpId, ignoreCase = true) || 
                    it.employeeName.trim().equals(cleanEmpName, ignoreCase = true) 
                }
            if (myRankIndex != -1) {
                val myRank = rankedList[myRankIndex]
                cardMyRank.visibility = View.VISIBLE
                tvMyRankBadge.text = "#${myRank.rank}"
                tvMyRankName.text = "Your Position: #${myRank.rank} in Leaderboard"
                tvMyRankDetails.text = if (myRank.rank <= 3) "Outstanding performance! You are on the podium! 🏆" else "Keep submitting to reach the top 3 podium!"
                tvMyRankScore.text = "${myRank.score} Cards"
            } else {
                cardMyRank.visibility = View.GONE
            }

            // Populate Podium
            if (rankedList.isNotEmpty()) {
                val first = rankedList[0]
                tvPodiumRank1Name.text = first.employeeName
                tvPodiumRank1Score.text = "${first.score} cards"
            }
            if (rankedList.size > 1) {
                val second = rankedList[1]
                tvPodiumRank2Name.text = second.employeeName
                tvPodiumRank2Score.text = "${second.score} cards"
            }
            if (rankedList.size > 2) {
                val third = rankedList[2]
                tvPodiumRank3Name.text = third.employeeName
                tvPodiumRank3Score.text = "${third.score} cards"
            }

            val adapter = UserRankingAdapter(rankedList)
            recycler.layoutManager = LinearLayoutManager(this)
            recycler.adapter = adapter

            etSearch.addTextChangedListener(object : TextWatcher {
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val query = s?.toString()?.trim() ?: ""
                    btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                    val filtered = if (query.isEmpty()) {
                        rankedList
                    } else {
                        rankedList.filter {
                            it.employeeName.contains(query, ignoreCase = true) ||
                            it.employeeId.contains(query, ignoreCase = true) ||
                            it.branch.contains(query, ignoreCase = true) ||
                            it.zone.contains(query, ignoreCase = true)
                        }
                    }
                    adapter.updateList(filtered)
                    layoutEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
                    recycler.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
                }
                override fun afterTextChanged(s: Editable?) {}
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            })

            btnClearSearch.setOnClickListener { etSearch.setText("") }
            dialog.show()
            }.addOnFailureListener {
                Toast.makeText(this, "Failed to load performance data", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to load officers list", Toast.LENGTH_SHORT).show()
        }
    }

    // 🔥 Modern Ranking Adapter for User Dashboard
    inner class UserRankingAdapter(private var rankList: List<AdminDashboardActivity.ModernRankingEntry>) : RecyclerView.Adapter<UserRankingAdapter.VH>() {
        
        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView = view.findViewById(R.id.cardRankItem)
            val layoutBadge: FrameLayout = view.findViewById(R.id.layoutRankBadge)
            val tvNumber: TextView = view.findViewById(R.id.tvRankItemNumber)
            val tvName: TextView = view.findViewById(R.id.tvRankItemName)
            val tvBranch: TextView = view.findViewById(R.id.tvRankItemBranch)
            val tvZone: TextView = view.findViewById(R.id.tvRankItemZone)
            val tvScore: TextView = view.findViewById(R.id.tvRankItemScore)
            val tvStatus: TextView = view.findViewById(R.id.tvRankItemStatus)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ranking_modern, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = rankList[position]
            holder.tvNumber.text = item.rank.toString()
            holder.tvName.text = item.employeeName
            holder.tvBranch.text = if (item.branch.isNotBlank()) "Branch: ${item.branch}" else "ID: ${item.employeeId}"
            
            if (item.zone.isNotBlank()) {
                holder.tvZone.visibility = View.VISIBLE
                holder.tvZone.text = item.zone
            } else {
                holder.tvZone.visibility = View.GONE
            }

            val cardText = if (item.score == 1) "1 card" else "${item.score} cards"
            holder.tvScore.text = cardText

            when (item.rank) {
                1 -> {
                    holder.card.strokeColor = Color.parseColor("#FDE68A")
                    holder.card.strokeWidth = 3
                    holder.layoutBadge.setBackgroundResource(R.drawable.bg_badge_amber)
                    holder.tvNumber.setTextColor(Color.parseColor("#92400E"))
                    holder.tvStatus.visibility = View.VISIBLE
                    holder.tvStatus.text = "👑 Champion (#1)"
                    holder.tvStatus.setTextColor(Color.parseColor("#B45309"))
                }
                2 -> {
                    holder.card.strokeColor = Color.parseColor("#CBD5E1")
                    holder.card.strokeWidth = 2
                    holder.layoutBadge.setBackgroundResource(R.drawable.bg_badge_grey)
                    holder.tvNumber.setTextColor(Color.parseColor("#334155"))
                    holder.tvStatus.visibility = View.VISIBLE
                    holder.tvStatus.text = "🥈 Runner Up (#2)"
                    holder.tvStatus.setTextColor(Color.parseColor("#475569"))
                }
                3 -> {
                    holder.card.strokeColor = Color.parseColor("#FED7AA")
                    holder.card.strokeWidth = 2
                    holder.layoutBadge.setBackgroundResource(R.drawable.bg_badge_amber)
                    holder.tvNumber.setTextColor(Color.parseColor("#C2410C"))
                    holder.tvStatus.visibility = View.VISIBLE
                    holder.tvStatus.text = "🥉 3rd Place (#3)"
                    holder.tvStatus.setTextColor(Color.parseColor("#C2410C"))
                }
                else -> {
                    val isMe = item.employeeName.equals(empName, ignoreCase = true)
                    if (isMe) {
                        holder.card.strokeColor = Color.parseColor("#93C5FD")
                        holder.card.strokeWidth = 2
                        holder.layoutBadge.setBackgroundResource(R.drawable.bg_badge_blue)
                        holder.tvNumber.setTextColor(Color.parseColor("#1E40AF"))
                        holder.tvStatus.visibility = View.VISIBLE
                        holder.tvStatus.text = "⭐ You"
                        holder.tvStatus.setTextColor(Color.parseColor("#2563EB"))
                    } else {
                        holder.card.strokeColor = Color.parseColor("#E2E8F0")
                        holder.card.strokeWidth = 1
                        holder.layoutBadge.setBackgroundResource(R.drawable.bg_badge_blue)
                        holder.tvNumber.setTextColor(Color.parseColor("#1E40AF"))
                        holder.tvStatus.visibility = View.GONE
                    }
                }
            }
        }

        override fun getItemCount() = rankList.size

        fun updateList(newList: List<AdminDashboardActivity.ModernRankingEntry>) {
            rankList = newList
            notifyDataSetChanged()
        }
    }

    // ==========================================
    // ৩. সাবমিশন ফর্ম লজিক (Zero Approval ও PDF আপলোড সহ)
    // ==========================================
    private fun setupSubmissionUI() {
        val months = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        val monthAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, months)
        binding.spinnerMonth.adapter = monthAdapter
        
        val currentMonth = SimpleDateFormat("MMMM", Locale.getDefault()).format(Date())
        val monthIndex = months.indexOf(currentMonth)
        if (monthIndex >= 0) binding.spinnerMonth.setSelection(monthIndex)

        // Month change listener: if PDF was loaded, automatically populate rows for the selected month
        binding.spinnerMonth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedMonth = months[position]
                if (importedPdfAccountsByMonth.containsKey(selectedMonth)) {
                    populateRowsForMonth(selectedMonth, showToast = false)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Zero Approval চেকবক্স লজিক
        binding.cbZeroApproval.setOnCheckedChangeListener { _, isChecked ->
            binding.btnAddMore.isEnabled = !isChecked
            binding.btnUploadPdf.isEnabled = !isChecked
            if (isChecked) {
                binding.rowsContainer.alpha = 0.5f 
            } else {
                binding.rowsContainer.alpha = 1.0f
            }
        }

        // PDF আপলোড বাটন লজিক (Add More Row এর উপরে)
        binding.btnUploadPdf.setOnClickListener {
            pickPdfLauncher.launch("application/pdf")
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

    private fun processPdfUri(uri: Uri) {
        val dialogView = DialogPdfLoadingBinding.inflate(layoutInflater)
        val loadingDialog = AlertDialog.Builder(this)
            .setView(dialogView.root)
            .setCancelable(false)
            .create()
        loadingDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        loadingDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val fullText = PdfKpiParser.extractTextFromPdf(inputStream)
                    val accounts = PdfKpiParser.parseKpiText(fullText)
                    withContext(Dispatchers.Main) {
                        loadingDialog.dismiss()
                        if (accounts.isEmpty()) {
                            AlertDialog.Builder(this@UserDashboardActivity)
                                .setTitle("No Records Found")
                                .setMessage("No KPI account records could be found in this PDF. Please ensure you are uploading the official Pubali Bank KPI Register PDF.")
                                .setPositiveButton("OK", null)
                                .show()
                        } else {
                            handleParsedAccounts(accounts)
                        }
                    }
                } ?: run {
                    withContext(Dispatchers.Main) {
                        loadingDialog.dismiss()
                        Toast.makeText(this@UserDashboardActivity, "Unable to open the selected PDF file.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    AlertDialog.Builder(this@UserDashboardActivity)
                        .setTitle("PDF Processing Error")
                        .setMessage("Failed to process the PDF: ${e.localizedMessage ?: "Unknown error"}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun handleParsedAccounts(accounts: List<ParsedKpiAccount>) {
        importedPdfAccounts = accounts
        importedPdfAccountsByMonth = accounts.groupBy { it.month }
        binding.cbZeroApproval.isChecked = false

        val months = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        val currentSelectedMonth = binding.spinnerMonth.selectedItem?.toString() ?: ""
        
        // Find which month to select: if currently selected month has records, use it, else pick the first month from PDF
        val targetMonth = if (importedPdfAccountsByMonth.containsKey(currentSelectedMonth)) {
            currentSelectedMonth
        } else {
            importedPdfAccountsByMonth.keys.firstOrNull() ?: currentSelectedMonth
        }

        val targetIdx = months.indexOf(targetMonth)
        if (targetIdx >= 0) {
            binding.spinnerMonth.setSelection(targetIdx)
        }

        populateRowsForMonth(targetMonth, showToast = true)

        val summary = buildString {
            append("PDF Data Processed Successfully!\n\n")
            append("Filter Applied: Only 906 Accounts Imported\n")
            append("Total 906 Accounts Found: ${accounts.size}\n\n")
            append("Monthly Breakdown (Based on Period):\n")
            importedPdfAccountsByMonth.forEach { (m, list) ->
                append("• $m: ${list.size} accounts\n")
            }
            append("\n✓ Double entry protection active: Duplicate accounts per month filtered automatically.\n")
            append("\nAccounts for '$targetMonth' are filled into the input fields.")
            if (importedPdfAccountsByMonth.size > 1) {
                append(" You can change the 'Reporting Month' dropdown to view or edit accounts for other months.")
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Data Import Complete")
            .setMessage(summary)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun populateRowsForMonth(monthName: String, showToast: Boolean = false) {
        val monthAccounts = importedPdfAccountsByMonth[monthName] ?: return
        binding.rowsContainer.removeAllViews()
        for (acc in monthAccounts) {
            val rowView = LayoutInflater.from(this).inflate(R.layout.item_input_row, binding.rowsContainer, false)
            // Account Title -> Name field
            rowView.findViewById<EditText>(R.id.etRowName).setText(acc.accountTitle)
            // Account Number -> A/C No field
            rowView.findViewById<EditText>(R.id.etRowAc).setText(acc.accountNo)
            // Achievement Amount -> Limit field
            rowView.findViewById<EditText>(R.id.etRowLimit).setText(acc.achievementAmount)
            binding.rowsContainer.addView(rowView)
        }
        if (showToast) {
            Toast.makeText(this, "Loaded ${monthAccounts.size} accounts for $monthName", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showConfirmDialog() {
        if (importedPdfAccountsByMonth.size > 1 && !binding.cbZeroApproval.isChecked) {
            val currentMonth = binding.spinnerMonth.selectedItem.toString()
            val currentRowsCount = binding.rowsContainer.childCount
            val totalAccounts = importedPdfAccounts.size

            AlertDialog.Builder(this)
                .setTitle("Confirm Submission")
                .setMessage("Multiple months were imported from the PDF ($totalAccounts accounts across ${importedPdfAccountsByMonth.size} months).\n\nHow would you like to submit?")
                .setPositiveButton("Submit All ($totalAccounts accounts)") { _, _ ->
                    submitAllImportedMonths()
                }
                .setNeutralButton("Only $currentMonth ($currentRowsCount)") { _, _ ->
                    submitData()
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Confirm Submission")
                .setMessage("Are you sure you want to submit this data?")
                .setPositiveButton("Yes") { _, _ -> submitData() }
                .setNegativeButton("No", null)
                .show()
        }
    }

    private fun submitAllImportedMonths() {
        binding.btnSubmit.isEnabled = false
        binding.btnSubmit.text = "Submitting ${importedPdfAccounts.size} accounts..."
        pendingSubmissions = importedPdfAccounts.size

        for ((index, acc) in importedPdfAccounts.withIndex()) {
            val performance = Performance(
                employeeId = empId,
                employeeName = empName,
                branch = branch,
                zone = zone,
                salesManager = manager,
                month = acc.month,
                applicantName = acc.accountTitle,
                accountNo = acc.accountNo,
                limit = acc.achievementAmount,
                timestamp = System.currentTimeMillis() + index
            )
            viewModel.submitPerformance(performance)
        }
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
        if (!user.isTargetEligible) {
            binding.cardUserTargetAchievement.visibility = View.GONE
            return
        }
        binding.cardUserTargetAchievement.visibility = View.VISIBLE
        val currentMonth = SimpleDateFormat("MMMM", Locale.US).format(Date())
        binding.tvUserTargetMonthTitle.text = "$currentMonth Target"

        FirebaseFirestore.getInstance().collection("performance")
            .whereEqualTo("employeeId", user.employeeId)
            .get()
            .addOnSuccessListener { snapshot ->
                userReports = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Performance::class.java)?.apply { id = doc.id }
                }
                val target = user.monthlyTarget
                val achieved = TargetUtils.countCards(userReports, user.employeeId, currentMonth)
                val rate = TargetUtils.calculateAchievementRate(achieved, target)

                binding.tvUserTargetSummary.text = "Target: $target | Achieved: $achieved cards"
                binding.tvUserAchievementPercentBadge.text = TargetUtils.formatAchievementRate(achieved, target)

                val isBelow = TargetUtils.isBelowThreshold(achieved, target)
                val color = TargetUtils.getAchievementColor(achieved, target)
                binding.tvUserAchievementPercentBadge.setTextColor(color)

                val progress = rate.toInt().coerceIn(0, 100)
                binding.progressUserTarget.progress = progress
                binding.progressUserTarget.setIndicatorColor(color)

                binding.tvUserTargetMotivation.text = when {
                    target <= 0 -> "No target assigned by admin yet"
                    rate >= 100f -> "🎉 Target Achieved! Great job!"
                    rate >= 75f -> "Almost there! ${target - achieved} cards to reach 100%"
                    isBelow -> "⚠️ Alert: Below 50% threshold (${String.format(Locale.US, "%.0f%%", rate)}). ${target - achieved} more cards needed."
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
