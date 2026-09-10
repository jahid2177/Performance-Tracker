package com.performance.tracker.model

data class Performance(
    var employeeId: String = "",
    var employeeName: String = "",
    var branch: String = "",
    var zone: String = "",
    var salesManager: String = "",
    var month: String = "",
    
    // 🔥 নতুন ডাটা ফিল্ডসমূহ (ছবির রিকোয়ারমেন্ট অনুযায়ী)
    var applicantName: String = "",
    var accountNo: String = "", // 906 Account No
    var limit: String = "",      // Card Limit
    
    var timestamp: Long = 0L
)
