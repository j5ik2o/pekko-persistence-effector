package com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl

import com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl.PersistenceStoreProtocol.*
import com.github.j5ik2o.pekko.persistence.effector.scaladsl.{
  PersistenceEffector,
  PersistenceEffectorConfig,
  RetentionCriteria,
}
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

import scala.compiletime.asMatchable

private[effector] final class DefaultPersistenceEffector[S, E, M](
  ctx: ActorContext[M],
  stashBuffer: StashBuffer[M],
  config: PersistenceEffectorConfig[S, E, M],
  persistenceRef: ActorRef[PersistenceCommand[S, E]],
  adapter: ActorRef[PersistenceReply[S, E]],
  initialSequenceNr: Long,
) extends PersistenceEffector[S, E, M] {
  import config.*

  // Manage the current sequence number for each PersistenceId
  // Set initial value with initialSequenceNr
  private val sequenceNumbers =
    scala.collection.mutable.Map[String, Long](persistenceId -> initialSequenceNr)

  // Change the default value of getOrElse to initialSequenceNr (however, it should normally exist in the map)
  private def getCurrentSequenceNumber: Long =
    sequenceNumbers.getOrElse(persistenceId, initialSequenceNr)

  private def incrementSequenceNumber(inc: Long = 1): Long = {
    val current = getCurrentSequenceNumber
    val newValue = current + inc
    sequenceNumbers.update(persistenceId, newValue)
    newValue
  }

  /**
   * Calculate the maximum sequence number of snapshots to be deleted based on RetentionCriteria
   *
   * @param currentSequenceNumber
   *   Current sequence number
   * @param retention
   *   Retention policy
   * @return
   *   Maximum sequence number of snapshots to be deleted (0 if there are no snapshots to delete)
   */
  private def calculateMaxSequenceNumberToDelete(
    currentSequenceNumber: Long,
    retention: RetentionCriteria,
  ): Long = {
    val result = RetentionHelper.calculateMaxSequenceNumberToDelete(currentSequenceNumber, retention)
    ctx.log.debug(
      "Calculated maxSequenceNumberToDelete: currentSequenceNumber={}, retention={}, result={}",
      currentSequenceNumber,
      retention,
      result,
    )
    result
  }

  /**
   * Generic method to wait for a specified message type
   *
   * @param messageMatcher
   *   Function to detect messages
   * @param logMessage
   *   Log message on success
   * @param onSuccess
   *   Callback on success
   * @tparam T
   *   Type of result to be extracted
   * @return
   *   Message waiting behavior
   */
  private def waitForMessage[T](
    messageMatcher: M => Option[T],
    logMessage: String,
    onSuccess: T => Behavior[M],
  ): Behavior[M] =
    Behaviors.receiveMessagePartial { msg =>
      ctx.log.debug("Waiting for message: {}", msg)
      msg.asMatchable match {
        case msg if messageMatcher(msg).isDefined =>
          val result = messageMatcher(msg).get
          ctx.log.debug(s"$logMessage: {}", msg)
          stashBuffer.unstashAll(onSuccess(result))
        case other =>
          ctx.log.debug("Stashing message: {}", other)
          stashBuffer.stash(other)
          Behaviors.same
      }
    }

  /**
   * Delete old snapshots based on RetentionCriteria
   *
   * @param retention
   *   Retention policy
   * @param onDeleted
   *   Behavior after deletion is complete
   * @return
   *   Waiting behavior
   */
  private def deleteOldSnapshots(retention: RetentionCriteria, onDeleted: => Behavior[M]): Behavior[M] = {
    val currentSequenceNumber = getCurrentSequenceNumber
    val maxSequenceNumberToDelete =
      calculateMaxSequenceNumberToDelete(currentSequenceNumber, retention)

    if (maxSequenceNumberToDelete > 0) {
      ctx.log.debug(
        "Deleting snapshots with sequence numbers up to {} based on retention policy",
        maxSequenceNumberToDelete,
      )
      persistenceRef ! DeleteSnapshots(maxSequenceNumberToDelete, adapter)
      waitForMessage(
        unwrapDeleteSnapshots,
        "Delete snapshots succeeded",
        _ => onDeleted,
      )
    } else {
      ctx.log.debug("No snapshots to delete based on retention policy")
      onDeleted
    }
  }

  /**
   * Evaluate whether to take a snapshot
   *
   * @param event
   *   Event
   * @param state
   *   State
   * @param sequenceNumber
   *   Sequence number
   * @param force
   *   Force flag
   * @return
   *   Whether a snapshot should be taken
   */
  private def shouldTakeSnapshot(event: E, state: S, sequenceNumber: Long, force: Boolean): Boolean = {
    val result = SnapshotHelper.shouldTakeSnapshot(Some(event), state, sequenceNumber, force, config.snapshotCriteria)
    ctx.log.debug("Snapshot criteria evaluation result: {}", result)
    result
  }

  /**
   * Handle snapshot saving
   *
   * @param state
   *   State to save
   * @param onCompleted
   *   Callback after snapshot processing is complete
   * @return
   *   Waiting behavior
   */
  private def handleSnapshotSave[T](state: S, onCompleted: => Behavior[M]): Behavior[M] = {
    ctx.log.debug("Taking snapshot for state: {}", state)
    persistenceRef ! PersistSnapshot(state, adapter)

    waitForMessage(
      unwrapPersistedSnapshot,
      "Persisted snapshot",
      snapshot =>
        // Delete old snapshots if RetentionCriteria is set
        config.retentionCriteria match {
          case Some(retention) => deleteOldSnapshots(retention, onCompleted)
          case None => onCompleted
        },
    )
  }

  override def persistEvent(event: E)(onPersisted: E => Behavior[M]): Behavior[M] = {
    ctx.log.debug("Persisting event: {}", event)
    persistenceRef ! PersistSingleEvent(event, adapter)
    incrementSequenceNumber()

    waitForMessage(
      unwrapPersistedEvents,
      "Persisted event",
      events => onPersisted(events.head),
    )
  }

  override def persistEvents(events: Seq[E])(onPersisted: Seq[E] => Behavior[M]): Behavior[M] = {
    ctx.log.debug("Persisting events: {}", events)
    persistenceRef ! PersistMultipleEvents(events, adapter)
    incrementSequenceNumber(events.size)

    waitForMessage(
      unwrapPersistedEvents,
      "Persisted events",
      persistedEvents => onPersisted(persistedEvents),
    )
  }

  override def persistSnapshot(snapshot: S, force: Boolean)(onPersisted: S => Behavior[M]): Behavior[M] = {
    ctx.log.debug("Persisting snapshot: {}", snapshot)

    // Determine whether to save based on force parameter or snapshot strategy
    val shouldSaveSnapshot = force || config.snapshotCriteria.exists { criteria =>
      val sequenceNumber = getCurrentSequenceNumber
      val result = SnapshotHelper.shouldTakeSnapshot(None, snapshot, sequenceNumber, force, Some(criteria))
      ctx.log.debug("Snapshot criteria evaluation result: {}", result)
      result
    }

    if (shouldSaveSnapshot) {
      handleSnapshotSave(snapshot, stashBuffer.unstashAll(onPersisted(snapshot)))
    } else {
      ctx.log.debug("Skipping snapshot persistence based on criteria evaluation")
      onPersisted(snapshot)
    }
  }

  override def persistEventWithSnapshot(event: E, snapshot: S, forceSnapshot: Boolean)(
    onPersisted: E => Behavior[M]): Behavior[M] = {
    ctx.log.debug("Persisting event with state: {}", event)
    persistenceRef ! PersistSingleEvent(event, adapter)
    val sequenceNumber = incrementSequenceNumber()

    waitForMessage(
      unwrapPersistedEvents,
      "Persisted event",
      events => {
        val shouldSaveSnapshot = shouldTakeSnapshot(event, snapshot, sequenceNumber, forceSnapshot)

        if (shouldSaveSnapshot) {
          ctx.log.debug("Taking snapshot at sequence number {}", sequenceNumber)
          handleSnapshotSave(snapshot, stashBuffer.unstashAll(onPersisted(events.head)))
        } else {
          stashBuffer.unstashAll(onPersisted(events.head))
        }
      },
    )
  }

  override def persistEventsWithSnapshot(events: Seq[E], snapshot: S, forceSnapshot: Boolean)(
    onPersisted: Seq[E] => Behavior[M]): Behavior[M] = {
    ctx.log.debug("Persisting events with state: {}", events)
    persistenceRef ! PersistMultipleEvents(events, adapter)
    val finalSequenceNumber = incrementSequenceNumber(events.size)

    waitForMessage(
      unwrapPersistedEvents,
      "Persisted events",
      persistedEvents => {
        // Automatic snapshot acquisition when evaluating snapshot strategy or force=true
        // Evaluates with only the last event and sequence number
        val shouldSaveSnapshot =
          forceSnapshot || (events.nonEmpty && {
            val lastEvent = events.last
            val result = SnapshotHelper.shouldTakeSnapshot(
              Some(lastEvent),
              snapshot,
              finalSequenceNumber,
              forceSnapshot,
              config.snapshotCriteria)
            ctx.log.debug("Snapshot criteria evaluation result: {}", result)
            result
          })

        if (shouldSaveSnapshot) {
          ctx.log.debug("Taking snapshot at sequence number {}", finalSequenceNumber)
          handleSnapshotSave(snapshot, stashBuffer.unstashAll(onPersisted(persistedEvents)))
        } else {
          stashBuffer.unstashAll(onPersisted(persistedEvents))
        }
      },
    )
  }
}
