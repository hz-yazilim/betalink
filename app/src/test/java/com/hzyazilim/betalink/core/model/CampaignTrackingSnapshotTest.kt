package com.hzyazilim.betalink.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignTrackingSnapshotTest {
    private val day = 24L * 60L * 60L * 1000L

    @Test
    fun campaignClockIsIndependentAndStartsAtItsOwnTimestamp() {
        val snapshot = CampaignTrackingSnapshot(
            campaignId = "campaign-a",
            activeTesterCount = 12,
            startedAtEpochMillis = 1_000L
        )

        val progress = snapshot.progressAt(1_000L + 5L * day)

        assertEquals("campaign-a", progress.campaignId)
        assertEquals(5, progress.elapsedDays)
        assertEquals(9, progress.daysRemaining)
        assertTrue(progress.isTrackingStarted)
    }

    @Test
    fun fourteenDaysAloneDoesNotCompleteCampaign() {
        val snapshot = CampaignTrackingSnapshot(
            campaignId = "campaign-b",
            activeTesterCount = 12,
            startedAtEpochMillis = 0L,
            minimumTesterThresholdMaintained = false
        )

        val progress = snapshot.progressAt(14L * day)

        assertEquals(0, progress.daysRemaining)
        assertFalse(progress.canBeMarkedComplete)
    }

    @Test
    fun campaignNeedsFourteenDaysAndMaintainedTesterThreshold() {
        val snapshot = CampaignTrackingSnapshot(
            campaignId = "campaign-c",
            activeTesterCount = 12,
            startedAtEpochMillis = 0L,
            minimumTesterThresholdMaintained = true
        )

        assertTrue(snapshot.progressAt(14L * day).canBeMarkedComplete)
        assertFalse(snapshot.progressAt(14L * day - 1L).canBeMarkedComplete)
    }

    @Test
    fun campaignCannotCompleteWhenActiveTesterCountDropsBelowThreshold() {
        val snapshot = CampaignTrackingSnapshot(
            campaignId = "campaign-d",
            activeTesterCount = 11,
            startedAtEpochMillis = 0L,
            minimumTesterThresholdMaintained = true
        )

        assertFalse(snapshot.progressAt(14L * day).canBeMarkedComplete)
    }
}
