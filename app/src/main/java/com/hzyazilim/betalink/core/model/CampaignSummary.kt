package com.hzyazilim.betalink.core.model

enum class CampaignStatus {
    DRAFT, OPEN, PAUSED, COMPLETED, CANCELLED
}

enum class CampaignMemberStatus {
    APPLIED, ACCEPTED, WAITLISTED, OPTED_IN, ACTIVE, COMPLETED, WITHDRAWN, REJECTED, DROPPED
}

data class CampaignSummary(
    val id: String,
    val appName: String,
    val description: String,
    val category: String,
    val targetTesters: Int,
    val minimumAndroidVersion: Int?,
    val status: CampaignStatus,
    val developerId: String
)
