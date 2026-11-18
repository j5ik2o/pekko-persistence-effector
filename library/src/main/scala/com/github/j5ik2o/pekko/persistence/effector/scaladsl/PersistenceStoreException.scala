package com.github.j5ik2o.pekko.persistence.effector.scaladsl

/**
 * Thrown in the actor that uses a persisted [[PersistenceEffector]] when its internal persistence store actor stops.
 * The store can no longer persist events, so the actor fails instead of waiting for a reply that never comes. Commands
 * that were stashed while waiting are discarded.
 *
 * Let the actor stop or restart it so that it recovers from the journal. Resuming leaves the actor without a store.
 */
sealed abstract class PersistenceStoreException(message: String, cause: Option[Throwable])
  extends RuntimeException(message, cause.orNull) {
  def persistenceId: PersistenceId
}

/**
 * The journal rejected the event, for example because it could not be serialized. The event was not stored, so the
 * sequence number is used again after recovery. If a defect in the event or serializer caused the rejection, fix the
 * defect before retrying.
 */
final class PersistEventRejectedException(
  val persistenceId: PersistenceId,
  val sequenceNr: Long,
  val eventType: String,
  cause: Throwable,
) extends PersistenceStoreException(
    s"Journal rejected event [$eventType] with sequence number [$sequenceNr] for persistenceId " +
      s"[${persistenceId.asString}]. The event was not stored.",
    Some(cause),
  )

/**
 * The journal failed to write the event. Whether the event was stored is unknown; recovery reads the journal again.
 */
final class PersistEventFailedException(
  val persistenceId: PersistenceId,
  val sequenceNr: Long,
  val eventType: String,
  cause: Throwable,
) extends PersistenceStoreException(
    s"Journal failed to persist event [$eventType] with sequence number [$sequenceNr] for persistenceId " +
      s"[${persistenceId.asString}].",
    Some(cause),
  )

/**
 * The persistence store actor stopped without reporting a failed write, for example because recovery failed. The store
 * actor logs the original error.
 */
final class PersistenceStoreStoppedException(val persistenceId: PersistenceId, cause: Option[Throwable])
  extends PersistenceStoreException(
    s"Persistence store actor for persistenceId [${persistenceId.asString}] stopped.",
    cause,
  )
