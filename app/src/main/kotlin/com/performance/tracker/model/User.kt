package com.performance.tracker.model

data class User(
    val employeeId: String = "",
    val name: String = "",
    val branch: String = "",
    val salesManager: String = "",
    val mobile: String = "",
    val zone: String = "",
    val password: String = "",
    val role: String = "",
    val status: String = "Approved", // 🔥 এখানে Pending এর বদলে Approved করে দিন
    val createdAt: Long = 0L
)
