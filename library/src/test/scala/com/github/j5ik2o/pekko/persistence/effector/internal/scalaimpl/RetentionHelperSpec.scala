package com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl

import com.github.j5ik2o.pekko.persistence.effector.scaladsl.RetentionCriteria
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Unit tests for RetentionHelper.
 *
 * These tests verify the snapshot retention calculation logic without requiring actor system or persistence components.
 */
class RetentionHelperSpec extends AnyWordSpec with Matchers {

  "RetentionHelper" should {

    "calculateMaxSequenceNumberToDelete" should {

      "return 0 when retention criteria is default (no settings)" in {
        val retention = RetentionCriteria.Default
        val result = RetentionHelper.calculateMaxSequenceNumberToDelete(100L, retention)
        result shouldBe 0L
      }

      "return 0 when snapshotEvery is missing" in {
        val retention = RetentionCriteria.Default
        val result = RetentionHelper.calculateMaxSequenceNumberToDelete(100L, retention)
        result shouldBe 0L
      }

      "return 0 when current sequence number is less than snapshotEvery" in {
        val retention = RetentionCriteria.snapshotEvery(numberOfEvents = 10, keepNSnapshots = 2)
        val result = RetentionHelper.calculateMaxSequenceNumberToDelete(5L, retention)
        result shouldBe 0L
      }

      "return 0 when only one snapshot interval has been reached" in {
        val retention = RetentionCriteria.snapshotEvery(numberOfEvents = 10, keepNSnapshots = 2)
        val result = RetentionHelper.calculateMaxSequenceNumberToDelete(10L, retention)
        result shouldBe 0L
      }

      "return 0 when only snapshots to keep exist" in {
        val retention = RetentionCriteria.snapshotEvery(numberOfEvents = 10, keepNSnapshots = 2)
        val result = RetentionHelper.calculateMaxSequenceNumberToDelete(20L, retention)
        result shouldBe 0L
      }

      "calculate correct deletion sequence number for basic case" in {
        // Sequence: 30, snapshots at 10, 20, 30
        // Keep 2 snapshots (20, 30), delete up to 10
        val retention = RetentionCriteria.snapshotEvery(numberOfEvents = 10, keepNSnapshots = 2)
        val result = RetentionHelper.calculateMaxSequenceNumberToDelete(30L, retention)
        result shouldBe 10L
      }

      "calculate correct deletion sequence number for larger intervals" in {
        // Sequence: 100, snapshots at 20, 40, 60, 80, 100
        // Keep 2 snapshots (80, 100), delete up to 60
        val retention = RetentionCriteria.snapshotEvery(numberOfEvents = 20, keepNSnapshots = 2)
        val result = RetentionHelper.calculateMaxSequenceNumberToDelete(100L, retention)
        result shouldBe 60L
      }

      "calculate correct deletion sequence number with partial interval" in {
        // Sequence: 105, latest snapshot at 100
        // Snapshots at 20, 40, 60, 80, 100
        // Keep 2 snapshots (80, 100), delete up to 60
        val retention = RetentionCriteria.snapshotEvery(numberOfEvents = 20, keepNSnapshots = 2)
        val result = RetentionHelper.calculateMaxSequenceNumberToDelete(105L, retention)
        result shouldBe 60L
      }

      "keep more snapshots when configured" in {
        // Sequence: 50, snapshots at 10, 20, 30, 40, 50
        // Keep 3 snapshots (30, 40, 50), delete up to 20
        val retention = RetentionCriteria.snapshotEvery(numberOfEvents = 10, keepNSnapshots = 3)
        val result = RetentionHelper.calculateMaxSequenceNumberToDelete(50L, retention)
        result shouldBe 20L
      }

      "return 0 when keeping all available snapshots" in {
        // Sequence: 40, snapshots at 10, 20, 30, 40
        // Keep 5 snapshots but only 4 exist
        val retention = RetentionCriteria.snapshotEvery(numberOfEvents = 10, keepNSnapshots = 5)
        val result = RetentionHelper.calculateMaxSequenceNumberToDelete(40L, retention)
        result shouldBe 0L
      }

      "handle edge case with keepNSnapshots = 1" in {
        // Sequence: 30, snapshots at 10, 20, 30
        // Keep 1 snapshot (30), delete up to 20
        val retention = RetentionCriteria.snapshotEvery(numberOfEvents = 10, keepNSnapshots = 1)
        val result = RetentionHelper.calculateMaxSequenceNumberToDelete(30L, retention)
        result shouldBe 20L
      }

      "handle large sequence numbers correctly" in {
        // Sequence: 1000000, snapshots every 100000
        // Keep 2 snapshots (900000, 1000000), delete up to 800000
        val retention = RetentionCriteria.snapshotEvery(numberOfEvents = 100000, keepNSnapshots = 2)
        val result = RetentionHelper.calculateMaxSequenceNumberToDelete(1000000L, retention)
        result shouldBe 800000L
      }
    }
  }
}
