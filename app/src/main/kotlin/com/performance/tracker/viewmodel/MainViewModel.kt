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
}
