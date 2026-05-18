package com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl

import com.github.j5ik2o.pekko.persistence.effector.scaladsl.{
  PersistenceEffector,
  PersistenceEffectorConfig,
  PersistenceId,
  PersistenceMode,
}
import com.github.j5ik2o.pekko.persistence.effector.{TestEvent, TestMessage, TestState}
import org.apache.pekko.actor.ActorPath
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import java.io.File
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.*

/**
 * Test for PersistenceEffector using Persisted mode
 */
class PersistedEffectorSpec extends PersistenceEffectorTestBase {
  override def persistenceMode: PersistenceMode = PersistenceMode.Persisted

  // Run snapshot tests
  override def runSnapshotTests: Boolean = true

  "PersistenceEffector persisted actor names" should {
    "encode persistence ids into valid non-colliding actor names" in {
      val actorNames = Seq(
        "Staff|01KM",
        "Staff/01KM with space",
        "Staff|\u65e5\u672c\u8a9e",
      ).map(PersistenceEffector.persistenceStoreActorName)

      actorNames.foreach { actorName =>
        actorName should startWith("effector-")
        ActorPath.isValidPathElement(actorName) shouldBe true
      }

      PersistenceEffector.persistenceStoreActorName("A|B") should not be PersistenceEffector.persistenceStoreActorName(
        "A_B")
    }
  }

  "PersistenceEffector with a typed persistence id" should {
    "persist and recover using the original separator-based persistence id" in {
      val entityId = s"staff-${java.util.UUID.randomUUID()}"
      val persistenceId = PersistenceId.of("Staff", entityId)
      val initialState = TestState()
      val event = TestEvent.TestEventA("hired")

      persistenceId.asString shouldBe s"Staff|$entityId"

      val config =
        PersistenceEffectorConfig.create[TestState, TestEvent, TestMessage](
          persistenceId = persistenceId,
          initialState = initialState,
          applyEvent = (state, event) => state.applyEvent(event),
          stashSize = Int.MaxValue,
          persistenceMode = persistenceMode,
          snapshotCriteria = None,
          retentionCriteria = None,
          backoffConfig = None,
          messageConverter = messageConverter,
        )

      val persistedEvents = ArrayBuffer.empty[TestMessage]

      spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.fromConfig[TestState, TestEvent, TestMessage](config) { case (_, effector) =>
          effector.persistEvent(event) { _ =>
            persistedEvents += TestMessage.EventPersisted(Seq(event))
            Behaviors.stopped
          }
        }(using context)
      })

      eventually {
        persistedEvents.size shouldBe 1
      }

      val recoveredProbe = createTestProbe[TestState]()

      spawn(Behaviors.setup[TestMessage] { context =>
        PersistenceEffector.fromConfig[TestState, TestEvent, TestMessage](config) { case (state, _) =>
          recoveredProbe.ref ! state
          Behaviors.stopped
        }(using context)
      })

      val recoveredState = recoveredProbe.receiveMessage(10.seconds)
      recoveredState.values should contain("hired")
    }
  }

  // Ensure LevelDB storage directory is created before testing
  override def beforeAll(): Unit = {
    val journalDir = new File("target/journal")
    val snapshotDir = new File("target/snapshot")

    if (!journalDir.exists()) {
      journalDir.mkdirs()
    }

    if (!snapshotDir.exists()) {
      snapshotDir.mkdirs()
    }

    super.beforeAll()
  }

  // Clean up directory after testing
  override def afterAll(): Unit =
    super.afterAll()
}
