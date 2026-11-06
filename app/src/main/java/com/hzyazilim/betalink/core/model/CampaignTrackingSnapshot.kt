package com.hzyazilim.betalink.core.model

/**
 * Read model for one campaign's independent closed-test tracking window.
 *
 * minimumTesterThresholdMaintained is a trusted backend result, not a client
 * calculation. Daily in-app check-ins are declarations and cannot prove
 * Google Play opt-in state by themselves.
 */
data class CampaignTrackingSnapshot(
    val campaignId: String,
    val activeTesterCount: Int,
    val minimumTesterThreshold: Int = 12,
    val requiredDays: Int = 14,
    val startedAtEpochMillis: Long? = null,
    val minimumTesterThresholdMaintained: Boolean = false
) {
    fun progressAt(nowEpochMillis: Long): CampaignProgress {
        val startedAt = startedAtEpochMillis
        val elapsedDays = if (startedAt == null || nowEpochMillis < startedAt) {
            0
        } else {
            ((nowEpochMillis - startedAt) / MILLIS_PER_DAY).toInt()
        }
        val daysRemaining = (requiredDays - elapsedDays).coerceAtLeast(0)
        val hasEnoughTestersNow = activeTesterCount >= minimumTesterThreshold
        val canBeMarkedComplete =
            startedAt != null &&
            elapsedDays >= requiredDays &&
            hasEnoughTestersNow &&
            minimumTesterThresholdMaintained

        return CampaignProgress(
            campaignId = campaignId,
            activeTesterCount = activeTesterCount,
            minimumTesterThreshold = minimumTesterThreshold,
            elapsedDays = elapsedDays,
            daysRemaining = daysRemaining,
            hasEnoughTestersNow = hasEnoughTestersNow,
            isTrackingStarted = startedAt != null,
            canBeMarkedComplete = canBeMarkedComplete
        )
    }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}

data class CampaignProgress(
    val campaignId: String,
    val activeTesterCount: Int,
    val minimumTesterThreshold: Int,
    val elapsedDays: Int,
    val daysRemaining: Int,
    val hasEnoughTestersNow: Boolean,
    val isTrackingStarted: Boolean,
    val canBeMarkedComplete: Boolean
)
