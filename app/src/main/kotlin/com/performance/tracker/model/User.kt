package com.performance.tracker.model

data class User(
    val employeeId: String = "",
    val name: String = "",
    val branch: String = "",
    val salesManager: String = "",
    val mobile: String = "",
    val officialNumber: String = "",
    val zone: String = "",
    val password: String = "",
    val role: String = "",
    val status: String = "Approved", // 🔥 এখানে Pending এর বদলে Approved করে দিন
    val createdAt: Long = 0L,
    val profileImage: String = "",
    val department: String = "",
    val monthlyTarget: Int = 0
) {
    val displayDepartment: String
        get() = when {
            department.isNotBlank() -> department
            branch.isNotBlank() && !branch.equals("N/A", ignoreCase = true) -> branch
            zone.isNotBlank() && !zone.equals("N/A", ignoreCase = true) -> zone
            else -> "General"
        }
}
