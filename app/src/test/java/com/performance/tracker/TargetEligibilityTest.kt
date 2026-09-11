package com.performance.tracker

import com.performance.tracker.model.User
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetEligibilityTest {

    @Test
    fun testTargetEligibility_onlyEmployeesAreEligible() {
        val userEmployee = User(employeeId = "EMP001", name = "John Officer", role = "USER")
        val userBlankRole = User(employeeId = "EMP002", name = "Jane Officer", role = "")
        val salesManager = User(employeeId = "MGR001", name = "Bob Manager", role = "Sales Manager")
        val agm = User(employeeId = "AGM001", name = "Alice AGM", role = "AGM")
        val dgm = User(employeeId = "DGM001", name = "David DGM", role = "DGM")
        val admin = User(employeeId = "ADM001", name = "Admin User", role = "ADMIN")

        assertTrue("Regular USER role should be target eligible", userEmployee.isTargetEligible)
        assertTrue("Blank role should default to target eligible", userBlankRole.isTargetEligible)

        assertFalse("Sales Manager should NOT be target eligible", salesManager.isTargetEligible)
        assertFalse("AGM should NOT be target eligible", agm.isTargetEligible)
        assertFalse("DGM should NOT be target eligible", dgm.isTargetEligible)
        assertFalse("ADMIN should NOT be target eligible", admin.isTargetEligible)
    }
}
