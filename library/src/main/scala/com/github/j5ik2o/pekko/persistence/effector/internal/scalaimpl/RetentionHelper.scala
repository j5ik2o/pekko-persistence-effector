package com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl

import com.github.j5ik2o.pekko.persistence.effector.scaladsl.RetentionCriteria

/**
 * Utility object for handling snapshot retention calculations.
 *
 * This object consolidates the snapshot retention logic that was previously duplicated across multiple effector
 * implementations.
 */
private[scalaimpl] object RetentionHelper {

  /**
   * Calculate the maximum sequence number for snapshots that can be safely deleted.
   *
   * This method implements the retention policy based on the current sequence number and the configured retention
   * criteria. It ensures that the specified number of snapshots are kept while safely deleting older ones.
   *
   * @param currentSequenceNumber
   *   The current sequence number of the aggregate
   * @param retention
   *   The retention criteria specifying how many snapshots to keep
   * @return
   *   Maximum sequence number of snapshots to be deleted (0 if there are no snapshots to delete)
   */
  def calculateMaxSequenceNumberToDelete(
    currentSequenceNumber: Long,
    retention: RetentionCriteria,
  ): Long =
    // Calculate only if both snapshotEvery and keepNSnapshots are set
    (retention.snapshotEvery, retention.keepNSnapshots) match {
      case (Some(snapshotEvery), Some(keepNSnapshots)) =>
        // Calculate the sequence number of the latest snapshot
        val latestSnapshotSeqNr = currentSequenceNumber - (currentSequenceNumber % snapshotEvery)

        if (latestSnapshotSeqNr < snapshotEvery) {
          // If even the first snapshot has not been created
          0L
        } else {
          // The oldest sequence number of snapshots to keep
          val oldestKeptSnapshot =
            latestSnapshotSeqNr - (snapshotEvery.toLong * (keepNSnapshots - 1))

          if (oldestKeptSnapshot <= 0) {
            // If all snapshots to be kept do not exist
            0L
          } else {
            // Maximum sequence number to be deleted (snapshot just before oldestKeptSnapshot)
            val maxSequenceNumberToDelete = oldestKeptSnapshot - snapshotEvery

            if (maxSequenceNumberToDelete <= 0) 0L else maxSequenceNumberToDelete
          }
        }
      case _ =>
        // Do not delete if either setting is missing
        0L
    }
}
