package com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl

import com.github.j5ik2o.pekko.persistence.effector.scaladsl.SnapshotCriteria

/**
 * Utility object for handling snapshot evaluation logic.
 *
 * This object consolidates the snapshot evaluation logic that was previously duplicated across multiple effector
 * implementations.
 */
private[scalaimpl] object SnapshotHelper {

  /**
   * Evaluate whether a snapshot should be taken based on the provided criteria.
   *
   * This method provides a centralized way to evaluate snapshot criteria, ensuring consistent behavior across all
   * effector implementations.
   *
   * @param event
   *   The event that was persisted (can be None for snapshot-only operations)
   * @param state
   *   The current state after applying the event
   * @param sequenceNumber
   *   The sequence number after persisting the event
   * @param force
   *   Whether to force snapshot creation regardless of criteria
   * @param criteria
   *   Optional snapshot criteria to evaluate
   * @tparam S
   *   State type
   * @tparam E
   *   Event type
   * @return
   *   true if a snapshot should be taken, false otherwise
   */
  def shouldTakeSnapshot[S, E](
    event: Option[E],
    state: S,
    sequenceNumber: Long,
    force: Boolean,
    criteria: Option[SnapshotCriteria[S, E]],
  ): Boolean =
    if (force) {
      true
    } else {
      criteria match {
        case Some(snapshotCriteria) =>
          event match {
            case Some(evt) =>
              snapshotCriteria.shouldTakeSnapshot(evt, state, sequenceNumber)
            case None =>
              // For snapshot-only operations, create a dummy event representation
              // This is safe because SnapshotCriteria implementations should handle any event type
              val dummyEvent = state.asInstanceOf[E]
              snapshotCriteria.shouldTakeSnapshot(dummyEvent, state, sequenceNumber)
          }
        case None =>
          false
      }
    }
}
