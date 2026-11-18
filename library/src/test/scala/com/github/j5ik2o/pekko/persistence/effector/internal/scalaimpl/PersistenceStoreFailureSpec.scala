package com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl

import com.github.j5ik2o.pekko.persistence.effector.scaladsl.*
import com.github.j5ik2o.pekko.persistence.effector.{TestConfig, TestEvent, TestState}
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior, ChildFailed, SupervisorStrategy}
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.*

object PersistenceStoreFailureSpec {
  enum Command {
    case Persist(value: String, replyTo: ActorRef[String])
  }

  val PoisonValue: String = "poison"
}

/**
 * The actor that uses a persisted PersistenceEffector must fail, not wait forever, when the journal rejects or fails
 * a write, and when recovery fails.
 */
class PersistenceStoreFailureSpec
  extends ScalaTestWithActorTestKit(
    ConfigFactory.parseString(FaultyJournal.configString).withFallback(TestConfig.config))
  with AnyWordSpecLike {
  import PersistenceStoreFailureSpec.*
  import Command.*

  private def newPersistenceId(): PersistenceId = PersistenceId.ofUniqueId(s"store-failure-${java.util.UUID.randomUUID()}")

  private def entity(
    persistenceId: PersistenceId,
    applyEvent: (TestState, TestEvent) => TestState = (state, event) => state.applyEvent(event),
    backoffConfig: Option[BackoffConfig] = None,
  ): Behavior[Command] =
    Behaviors.setup { context =>
      val config = PersistenceEffectorConfig.create[TestState, TestEvent, Command](
        persistenceId = persistenceId,
        initialState = TestState(),
        applyEvent = applyEvent,
        persistenceMode = PersistenceMode.Persisted,
        backoffConfig = backoffConfig,
      )
      PersistenceEffector.fromConfig(config) { case (_, effector) => active(effector) }(using context)
    }

  private def active(effector: PersistenceEffector[TestState, TestEvent, Command]): Behavior[Command] =
    Behaviors.receiveMessage { case Persist(value, replyTo) =>
      effector.persistEvent(TestEvent.TestEventA(value)) { _ =>
        replyTo ! value
        active(effector)
      }
    }

  /**
   * Spawns `child` under a parent that reports the child's failure cause, like a user's supervising actor would see it.
   */
  private def spawnSupervised(child: Behavior[Command]): (ActorRef[Command], TestProbe[Throwable]) = {
    val refProbe = createTestProbe[ActorRef[Command]]()
    val failureProbe = createTestProbe[Throwable]()
    spawn(Behaviors.setup[Nothing] { context =>
      val ref = context.spawnAnonymous(child)
      context.watch(ref)
      refProbe.ref ! ref
      Behaviors.receiveSignal { case (_, ChildFailed(_, cause)) =>
        failureProbe.ref ! cause
        Behaviors.stopped
      }
    })
    (refProbe.receiveMessage(), failureProbe)
  }

  "An actor using a persisted PersistenceEffector" should {
    "fail when the journal rejects an event, and drop the commands stashed while waiting" in {
      val persistenceId = newPersistenceId()
      val (ref, failureProbe) = spawnSupervised(entity(persistenceId))
      val replyProbe = createTestProbe[String]()

      ref ! Persist(FaultyJournal.RejectValue, replyProbe.ref)
      ref ! Persist("stashed-behind-rejection", replyProbe.ref)

      failureProbe.receiveMessage()
      replyProbe.expectNoMessage(100.millis)
      FaultyJournal.storedEvents(persistenceId.asString) shouldBe empty
    }

    "fail when the journal fails to write an event" in {
      val persistenceId = newPersistenceId()
      val (ref, failureProbe) = spawnSupervised(entity(persistenceId))
      val replyProbe = createTestProbe[String]()

      ref ! Persist(FaultyJournal.FailValue, replyProbe.ref)

      failureProbe.receiveMessage()
      FaultyJournal.storedEvents(persistenceId.asString) shouldBe empty
    }

    "fail when the journal rejects an event and a backoff supervisor wraps the store" in {
      val persistenceId = newPersistenceId()
      val backoff = BackoffConfig(minBackoff = 100.millis, maxBackoff = 1.second, randomFactor = 0.0)
      val (ref, failureProbe) = spawnSupervised(entity(persistenceId, backoffConfig = Some(backoff)))

      ref ! Persist(FaultyJournal.RejectValue, createTestProbe[String]().ref)

      failureProbe.receiveMessage()
    }

    "fail when recovery fails instead of waiting for the recovery result" in {
      val persistenceId = newPersistenceId()
      val replyProbe = createTestProbe[String]()
      val writer = spawn(entity(persistenceId))
      writer ! Persist(PoisonValue, replyProbe.ref)
      replyProbe.expectMessage(PoisonValue)
      testKit.stop(writer)

      val throwingApply: (TestState, TestEvent) => TestState = {
        case (_, TestEvent.TestEventA(PoisonValue)) => throw new IllegalStateException("cannot apply poison")
        case (state, event) => state.applyEvent(event)
      }
      val (_, failureProbe) = spawnSupervised(entity(persistenceId, applyEvent = throwingApply))

      failureProbe.receiveMessage()
    }

    "reuse the rejected sequence number after a restart, leaving no gap" in {
      val persistenceId = newPersistenceId()
      val replyProbe = createTestProbe[String]()
      val ref = spawn(
        Behaviors.supervise(entity(persistenceId)).onFailure[Exception](SupervisorStrategy.restart),
      )

      ref ! Persist(FaultyJournal.RejectValue, replyProbe.ref)
      ref ! Persist("after-restart", replyProbe.ref)
      replyProbe.expectMessage("after-restart")
      FaultyJournal.storedEvents(persistenceId.asString) shouldBe Vector(1L -> TestEvent.TestEventA("after-restart"))
    }
  }
}
