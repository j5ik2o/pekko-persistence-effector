package com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.persistenceEffector

import com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.{
  BankAccountAggregateTestBase,
  BankAccountCommand,
  BankAccountId,
}
import com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl.InMemoryEventStore
import com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistenceMode
import org.apache.pekko.actor.typed.Behavior

/**
 * Test for BankAccountAggregate using InMemory mode
 */
class InMemoryBankAccountAggregateSpec extends BankAccountAggregateTestBase {
  override def persistenceMode: PersistenceMode = PersistenceMode.Ephemeral

  override def createBankAccountAggregate(accountId: BankAccountId): Behavior[BankAccountCommand] =
    BankAccountAggregate(accountId, persistenceMode)

  // Clear InMemoryStore at the end of the test
  override def afterAll(): Unit = {
    InMemoryEventStore.clear()
    super.afterAll()
  }
}
