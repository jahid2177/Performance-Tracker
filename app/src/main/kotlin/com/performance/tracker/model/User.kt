package com.performance.tracker.model

data class User(
    val employeeId: String = "",
    val name: String = "",
    val email: String = "",
    val branch: String = "",
    val salesManager: String = "",
    val mobile: String = "",
    val officialNumber: String = "",
    val zone: String = "",
    val password: String = "",
    val role: String = "",
    val status: String = "Pending", // Default new users to Pending until Admin approves
    val createdAt: Long = 0L,
    val profileImage: String = "",
    val department: String = "",
    val monthlyTarget: Int = 0,
    val yearlyTarget: Int = 0
) {
    val displayDepartment: String
        get() = when {
            department.isNotBlank() -> department
            branch.isNotBlank() && !branch.equals("N/A", ignoreCase = true) -> branch
            zone.isNotBlank() && !zone.equals("N/A", ignoreCase = true) -> zone
            else -> "General"
        }

    val isManagementOrAdmin: Boolean
        get() {
            val cleanRole = role.trim()
            return cleanRole.equals("Sales Manager", ignoreCase = true) ||
                   cleanRole.equals("AGM", ignoreCase = true) ||
                   cleanRole.equals("DGM", ignoreCase = true) ||
                   cleanRole.equals("ADMIN", ignoreCase = true) ||
                   cleanRole.contains("Manager", ignoreCase = true) ||
                   cleanRole.contains("AGM", ignoreCase = true) ||
                   cleanRole.contains("DGM", ignoreCase = true) ||
                   cleanRole.contains("Admin", ignoreCase = true)
        }

    val isTargetEligible: Boolean
        get() = !isManagementOrAdmin
}
