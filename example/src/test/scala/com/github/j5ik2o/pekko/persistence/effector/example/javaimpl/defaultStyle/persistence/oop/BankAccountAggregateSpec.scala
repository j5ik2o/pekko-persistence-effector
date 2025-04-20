package com.github.j5ik2o.pekko.persistence.effector.example.javaimpl.defaultStyle.persistence.oop

import com.github.j5ik2o.pekko.persistence.effector.example.javaimpl.{
  BankAccountAggregateTestBase,
  BankAccountCommand,
  BankAccountId,
}
import com.github.j5ik2o.pekko.persistence.effector.javadsl.PersistenceMode
import org.apache.pekko.actor.typed.Behavior

import java.io.File

/**
 * Test for BankAccountAggregate using Persisted mode
 */
class BankAccountAggregateSpec extends BankAccountAggregateTestBase {
  override def persistenceMode: PersistenceMode = PersistenceMode.PERSISTENCE

  override def createBankAccountAggregate(accountId: BankAccountId): Behavior[BankAccountCommand] =
    BankAccountAggregate.create(accountId)

  // Ensure LevelDB storage directories are created before testing
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

  // Clean up directories after testing
  override def afterAll(): Unit =
    super.afterAll()

}
