package com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl

import com.github.j5ik2o.pekko.persistence.effector.TestEvent
import org.apache.pekko.persistence.journal.AsyncWriteJournal
import org.apache.pekko.persistence.{AtomicWrite, PersistentRepr}

import java.util.concurrent.ConcurrentHashMap
import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

/**
 * In-memory journal that rejects writes containing `TestEventA("reject")` and fails writes containing
 * `TestEventA("fail")`. Stored events are kept per persistence id so that tests can read them back.
 */
object FaultyJournal {
  val RejectValue: String = "reject"
  val FailValue: String = "fail"

  private val store = new ConcurrentHashMap[String, Vector[PersistentRepr]]()
  private val rejectionGates = new ConcurrentHashMap[String, Promise[Unit]]()

  def holdRejection(persistenceId: String): () => Unit = {
    val gate = Promise[Unit]()
    rejectionGates.put(persistenceId, gate)
    () => {
      gate.success(())
      rejectionGates.remove(persistenceId, gate)
    }
  }

  def storedEvents(persistenceId: String): Vector[(Long, Any)] =
    Option(store.get(persistenceId)).getOrElse(Vector.empty).map(repr => (repr.sequenceNr, repr.payload))

  def configString: String =
    s"""pekko.persistence.journal.plugin = "faulty-journal"
       |faulty-journal.class = "${classOf[FaultyJournal].getName}"
       |""".stripMargin
}

final class FaultyJournal extends AsyncWriteJournal {
  import FaultyJournal.*

  private def hasPayload(write: AtomicWrite, value: String): Boolean =
    write.payload.exists(_.payload == TestEvent.TestEventA(value))

  override def asyncWriteMessages(messages: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] =
    if (messages.exists(hasPayload(_, FailValue))) {
      Future.failed(new RuntimeException("simulated journal failure"))
    } else {
      val rejectionGate = messages
        .find(hasPayload(_, RejectValue))
        .flatMap(write => Option(rejectionGates.remove(write.persistenceId)))
        .fold(Future.unit)(_.future)
      rejectionGate.map { _ =>
        messages.map { write =>
          if (hasPayload(write, RejectValue)) {
            Failure(new IllegalArgumentException("simulated journal rejection"))
          } else {
            store.merge(write.persistenceId, write.payload.toVector, _ ++ _)
            Success(())
          }
        }
      }(using ExecutionContext.parasitic)
    }

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] =
    Future.successful(())

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(
    recoveryCallback: PersistentRepr => Unit): Future[Unit] = {
    Option(store.get(persistenceId))
      .getOrElse(Vector.empty)
      .filter(repr => repr.sequenceNr >= fromSequenceNr && repr.sequenceNr <= toSequenceNr)
      .take(math.min(max, Int.MaxValue.toLong).toInt)
      .foreach(recoveryCallback)
    Future.successful(())
  }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] =
    Future.successful(Option(store.get(persistenceId)).flatMap(_.lastOption).map(_.sequenceNr).getOrElse(0L))
}
