package com.performance.tracker.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.performance.tracker.model.Performance
import com.performance.tracker.model.User
import com.performance.tracker.repository.PerformanceRepository
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val repo = PerformanceRepository()
    
    // LiveData ভেরিয়েবল যা UI তে অবজার্ভ করা হবে
    val userState = MutableLiveData<User?>()
    val submissionStatus = MutableLiveData<Boolean>()
    val reportList = MutableLiveData<List<Performance>>()
    val thresholdList = MutableLiveData<List<com.performance.tracker.model.EmployeeThresholdStatus>>()
    val flaggedEmployees = MutableLiveData<List<com.performance.tracker.model.EmployeeThresholdStatus>>()
    val employeeThreshold = MutableLiveData<com.performance.tracker.model.EmployeeThresholdStatus?>()
    val errorMsg = MutableLiveData<String>()

    // ১. ইউজার চেক করা (লগইন)
    fun checkUser(employeeId: String) {
        viewModelScope.launch {
            try {
                val user = repo.getUser(employeeId)
                userState.postValue(user)
            } catch (e: Exception) {
                // ইউজার না পাওয়া গেলে বা নেটওয়ার্ক এরর হলে
                userState.postValue(null)
            }
        }
    }

    // ২. নতুন ইউজার রেজিস্ট্রেশন
    fun registerUser(user: User) {
        viewModelScope.launch {
            try {
                repo.registerUser(user)
                userState.postValue(user)
            } catch (e: Exception) {
                errorMsg.postValue("Registration Failed: ${e.message}")
            }
        }
    }

    // ৩. পারফরম্যান্স ডাটা সাবমিট করা
    fun submitPerformance(perf: Performance) {
        viewModelScope.launch {
            try {
                repo.submitPerformance(perf)
                submissionStatus.postValue(true)
            } catch (e: Exception) {
                errorMsg.postValue("Submission Failed: ${e.message}")
                submissionStatus.postValue(false)
            }
        }
    }

    // ==========================================
    // নতুন কোড নিচে যুক্ত করা হলো
    // ==========================================

    // ৪. শুধুমাত্র নিজের রিপোর্ট দেখা (User Dashboard)
    fun getMyReports(employeeId: String) {
        viewModelScope.launch {
            try {
                val list = repo.getMyReports(employeeId)
                reportList.postValue(list)
            } catch (e: Exception) {
                errorMsg.postValue("Failed to load reports: ${e.message}")
            }
        }
    }

    // ৫. সকল রিপোর্ট দেখা (Admin Dashboard)
    fun getAllReports() {
        viewModelScope.launch {
            try {
                val list = repo.getAllReports()
                reportList.postValue(list)
            } catch (e: Exception) {
                errorMsg.postValue("Failed to load admin data: ${e.message}")
            }
        }
    }

    // ৬. থ্রেশহোল্ড মনিটরিং (৫০% এর কম টার্গেট প্রাপ্ত এমপ্লয়ীদের চিহ্নিত করা)
    fun monitorThresholds(month: String? = null) {
        viewModelScope.launch {
            try {
                val list = if (month != null) repo.monitorThresholds(month) else repo.monitorThresholds()
                thresholdList.postValue(list)
                flaggedEmployees.postValue(list.filter { it.isBelowThreshold })
            } catch (e: Exception) {
                errorMsg.postValue("Threshold monitoring error: ${e.message}")
            }
        }
    }

    // ৭. নির্দিষ্ট এমপ্লয়ীর থ্রেশহোল্ড স্ট্যাটাস লোড করা
    fun checkEmployeeThreshold(employeeId: String, month: String? = null) {
        viewModelScope.launch {
            try {
                val status = if (month != null) {
                    repo.getEmployeeThresholdStatus(employeeId, month)
                } else {
                    repo.getEmployeeThresholdStatus(employeeId)
                }
                employeeThreshold.postValue(status)
            } catch (e: Exception) {
                errorMsg.postValue("Failed to check employee threshold: ${e.message}")
            }
        }
    }
}
