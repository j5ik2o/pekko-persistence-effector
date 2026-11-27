package com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.persistenceEffector.oop

import com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.{
  BankAccountAggregateTestBase,
  BankAccountCommand,
  BankAccountId,
}
import com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistenceMode
import org.apache.pekko.actor.typed.Behavior

/**
 * Test for BankAccountAggregate using InMemory mode
 */
class InMemoryBankAccountAggregateSpec extends BankAccountAggregateTestBase {
  override def persistenceMode: PersistenceMode = PersistenceMode.Ephemeral

  override def createBankAccountAggregate(accountId: BankAccountId): Behavior[BankAccountCommand] =
    BankAccountAggregate(accountId, persistenceMode)
}
