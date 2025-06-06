package com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl

import com.github.j5ik2o.pekko.persistence.effector.example.*
import com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistenceMode
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.actor.typed.Behavior
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import scala.concurrent.duration.*

/**
 * Base test class for BankAccountAggregate. Specific mode (Persisted/InMemory) is specified in subclasses
 */
abstract class BankAccountAggregateTestBase
  extends ScalaTestWithActorTestKit(TestConfig.config)
  with AnyWordSpecLike
  with Matchers
  with Eventually
  with BeforeAndAfterAll {

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 10.seconds, interval = 100.millis)

  // Method to be implemented in subclasses - PersistenceMode to be tested
  def persistenceMode: PersistenceMode

  // Helper method to create BankAccountAggregate
  def createBankAccountAggregate(accountId: BankAccountId): Behavior[BankAccountCommand]

  s"BankAccountAggregate with $persistenceMode mode" should {
    "create a new bank account successfully" in {
      val accountId = BankAccountId(UUID.randomUUID())
      val limit = Money(100000, Money.JPY)
      val balance = Money(0, Money.JPY)
      val bankAccountActor = spawn(createBankAccountAggregate(accountId))

      val probe = createTestProbe[CreateReply]()
      bankAccountActor ! BankAccountCommand.Create(accountId, limit, balance, probe.ref)

      val response = probe.expectMessageType[scalaimpl.CreateReply.Succeeded]
      response.aggregateId shouldBe accountId
    }

    "deposit cash successfully" in {
      val accountId = BankAccountId(UUID.randomUUID())
      val limit = Money(100000, Money.JPY)
      val balance = Money(0, Money.JPY)
      val bankAccountActor = spawn(createBankAccountAggregate(accountId))

      // Create an account
      val createProbe = createTestProbe[CreateReply]()
      bankAccountActor ! BankAccountCommand.Create(accountId, limit, balance, createProbe.ref)
      createProbe.expectMessageType[scalaimpl.CreateReply.Succeeded]

      val depositProbe = createTestProbe[DepositCashReply]()

      for { _ <- 1 to 10 } {
        // Deposit
        val depositAmount = Money(10000, Money.JPY)
        bankAccountActor ! BankAccountCommand.DepositCash(accountId, depositAmount, depositProbe.ref)

        val depositResponse = depositProbe.expectMessageType[scalaimpl.DepositCashReply.Succeeded]
        depositResponse.aggregateId shouldBe accountId
        depositResponse.amount shouldBe depositAmount
      }

      // Check balance
      val balanceProbe = createTestProbe[GetBalanceReply]()
      bankAccountActor ! BankAccountCommand.GetBalance(accountId, balanceProbe.ref)

      val balanceResponse = balanceProbe.expectMessageType[scalaimpl.GetBalanceReply.Succeeded]
      // balanceResponse.balance shouldBe depositAmount
    }

    "withdraw cash successfully" in {
      val accountId = BankAccountId(UUID.randomUUID())
      val limit = Money(100000, Money.JPY)
      val balance = Money(0, Money.JPY)
      val bankAccountActor = spawn(createBankAccountAggregate(accountId))

      // Create an account
      val createProbe = createTestProbe[CreateReply]()
      bankAccountActor ! BankAccountCommand.Create(accountId, limit, balance, createProbe.ref)
      createProbe.expectMessageType[scalaimpl.CreateReply.Succeeded]

      // Deposit
      val depositAmount = Money(10000, Money.JPY)
      val depositProbe = createTestProbe[DepositCashReply]()
      bankAccountActor ! BankAccountCommand.DepositCash(accountId, depositAmount, depositProbe.ref)
      depositProbe.expectMessageType[scalaimpl.DepositCashReply.Succeeded]

      // Withdraw
      val withdrawAmount = Money(3000, Money.JPY)
      val withdrawProbe = createTestProbe[WithdrawCashReply]()
      bankAccountActor ! BankAccountCommand.WithdrawCash(accountId, withdrawAmount, withdrawProbe.ref)

      val withdrawResponse = withdrawProbe.expectMessageType[scalaimpl.WithdrawCashReply.Succeeded]
      withdrawResponse.aggregateId shouldBe accountId
      withdrawResponse.amount shouldBe withdrawAmount

      // Check balance
      val balanceProbe = createTestProbe[GetBalanceReply]()
      bankAccountActor ! BankAccountCommand.GetBalance(accountId, balanceProbe.ref)

      val balanceResponse = balanceProbe.expectMessageType[scalaimpl.GetBalanceReply.Succeeded]
      balanceResponse.balance shouldBe Money(7000, Money.JPY)
    }

    "get balance successfully" in {
      val accountId = BankAccountId(UUID.randomUUID())
      val limit = Money(100000, Money.JPY)
      val balance = Money(0, Money.JPY)
      val bankAccountActor = spawn(createBankAccountAggregate(accountId))

      // Create account
      val createProbe = createTestProbe[CreateReply]()
      bankAccountActor ! BankAccountCommand.Create(accountId, limit, balance, createProbe.ref)
      createProbe.expectMessageType[scalaimpl.CreateReply.Succeeded]

      // Check initial balance
      val initialBalanceProbe = createTestProbe[GetBalanceReply]()
      bankAccountActor ! BankAccountCommand.GetBalance(accountId, initialBalanceProbe.ref)

      val initialBalanceResponse =
        initialBalanceProbe.expectMessageType[scalaimpl.GetBalanceReply.Succeeded]
      initialBalanceResponse.balance shouldBe Money(0, Money.JPY)
    }

    "fail to withdraw when insufficient funds" in {
      val accountId = BankAccountId(UUID.randomUUID())
      val limit = Money(100000, Money.JPY)
      val balance = Money(0, Money.JPY)
      val bankAccountActor = spawn(createBankAccountAggregate(accountId))

      // Create an account
      val createProbe = createTestProbe[CreateReply]()
      bankAccountActor ! BankAccountCommand.Create(accountId, limit, balance, createProbe.ref)
      createProbe.expectMessageType[scalaimpl.CreateReply.Succeeded]

      // Attempt to withdraw more than the deposit amount
      val withdrawAmount = Money(1000, Money.JPY)
      val withdrawProbe = createTestProbe[WithdrawCashReply]()
      bankAccountActor ! BankAccountCommand.WithdrawCash(accountId, withdrawAmount, withdrawProbe.ref)

      val failedResponse = withdrawProbe.expectMessageType[scalaimpl.WithdrawCashReply.Failed]
      failedResponse.aggregateId shouldBe accountId
      failedResponse.error shouldBe BankAccountError.InsufficientFundsError
    }

    "fail to deposit when over limit" in {
      val accountId = BankAccountId(UUID.randomUUID())
      val limit = Money(100000, Money.JPY)
      val balance = Money(0, Money.JPY)
      val bankAccountActor = spawn(createBankAccountAggregate(accountId))

      // Create an account
      val createProbe = createTestProbe[CreateReply]()
      bankAccountActor ! BankAccountCommand.Create(accountId, limit, balance, createProbe.ref)
      createProbe.expectMessageType[scalaimpl.CreateReply.Succeeded]

      // Attempt to deposit exceeding limit
      val depositAmount = Money(150000, Money.JPY) // Limit is 100000 yen
      val depositProbe = createTestProbe[DepositCashReply]()
      bankAccountActor ! BankAccountCommand.DepositCash(accountId, depositAmount, depositProbe.ref)

      val failedResponse = depositProbe.expectMessageType[scalaimpl.DepositCashReply.Failed]
      failedResponse.aggregateId shouldBe accountId
      failedResponse.error shouldBe BankAccountError.LimitOverError
    }

    "maintain state after stop and restart with multiple actions" in {
      assume(
        persistenceMode != PersistenceMode.Deferred,
        "Test skipped for Deferred persistence mode",
      )
      val accountId = BankAccountId(UUID.randomUUID())
      val limit = Money(100000, Money.JPY)
      val balance = Money(0, Money.JPY)
      // Create first an actor and build state
      val bankAccountActor1 = spawn(createBankAccountAggregate(accountId))

      // Create an account
      val createProbe = createTestProbe[CreateReply]()
      bankAccountActor1 ! BankAccountCommand.Create(accountId, limit, balance, createProbe.ref)
      createProbe.expectMessageType[scalaimpl.CreateReply.Succeeded]

      // Deposit
      val depositAmount = Money(50000, Money.JPY)
      val depositProbe = createTestProbe[DepositCashReply]()
      bankAccountActor1 ! BankAccountCommand.DepositCash(accountId, depositAmount, depositProbe.ref)
      depositProbe.expectMessageType[scalaimpl.DepositCashReply.Succeeded]

      // Deposit again
      val depositAmount2 = Money(20000, Money.JPY)
      val depositProbe2 = createTestProbe[DepositCashReply]()
      bankAccountActor1 ! BankAccountCommand.DepositCash(accountId, depositAmount2, depositProbe2.ref)
      depositProbe2.expectMessageType[scalaimpl.DepositCashReply.Succeeded]

      // Explicitly stop to create a snapshot
      val stopProbe = createTestProbe[StopReply]()
      bankAccountActor1 ! BankAccountCommand.Stop(accountId, stopProbe.ref)
      stopProbe.expectMessageType[scalaimpl.StopReply.Succeeded]

      // Create a second actor - at this point receiveRecover of PersistenceStoreActor is called
      val bankAccountActor2 = spawn(createBankAccountAggregate(accountId))

      // Check balance - verify that the state of the previous actor has been restored
      val expectedBalance = Money(70000, Money.JPY) // 50000 + 20000
      val balanceProbe = createTestProbe[GetBalanceReply]()
      bankAccountActor2 ! BankAccountCommand.GetBalance(accountId, balanceProbe.ref)

      val balanceResponse = balanceProbe.expectMessageType[scalaimpl.GetBalanceReply.Succeeded]
      balanceResponse.balance shouldBe expectedBalance

      // Verify that operations can be performed normally after actor restart
      val withdrawAmount = Money(10000, Money.JPY)
      val withdrawProbe = createTestProbe[WithdrawCashReply]()
      bankAccountActor2 ! BankAccountCommand.WithdrawCash(accountId, withdrawAmount, withdrawProbe.ref)

      val withdrawResponse = withdrawProbe.expectMessageType[scalaimpl.WithdrawCashReply.Succeeded]
      withdrawResponse.amount shouldBe withdrawAmount

      // Final balance check
      val finalBalanceProbe = createTestProbe[GetBalanceReply]()
      bankAccountActor2 ! BankAccountCommand.GetBalance(accountId, finalBalanceProbe.ref)

      val finalResponse = finalBalanceProbe.expectMessageType[scalaimpl.GetBalanceReply.Succeeded]
      finalResponse.balance shouldBe Money(60000, Money.JPY) // 70000 - 10000
    }

    "maintain state after stop and restart" in {
      assume(
        persistenceMode != PersistenceMode.Deferred,
        "Test skipped for Deferred persistence mode",
      )
      val accountId = BankAccountId(UUID.randomUUID())
      val limit = Money(100000, Money.JPY)
      val balance = Money(0, Money.JPY)
      // Create a first actor and build state
      val bankAccountActor1 = spawn(createBankAccountAggregate(accountId))

      // Create an account
      val createProbe = createTestProbe[CreateReply]()
      bankAccountActor1 ! BankAccountCommand.Create(accountId, limit, balance, createProbe.ref)
      createProbe.expectMessageType[scalaimpl.CreateReply.Succeeded]

      // Deposit
      val depositAmount = Money(50000, Money.JPY)
      val depositProbe = createTestProbe[DepositCashReply]()
      bankAccountActor1 ! BankAccountCommand.DepositCash(accountId, depositAmount, depositProbe.ref)
      depositProbe.expectMessageType[scalaimpl.DepositCashReply.Succeeded]

      // Explicitly stop to create a snapshot
      val stopProbe = createTestProbe[StopReply]()
      bankAccountActor1 ! BankAccountCommand.Stop(accountId, stopProbe.ref)
      stopProbe.expectMessageType[scalaimpl.StopReply.Succeeded]

      // Restart actor (receiveRecover is called at this point)
      val bankAccountActor2 = spawn(createBankAccountAggregate(accountId))

      // Check balance - verify that the state of the previous actor has been restored
      val balanceProbe = createTestProbe[GetBalanceReply]()
      bankAccountActor2 ! BankAccountCommand.GetBalance(accountId, balanceProbe.ref)

      val balanceResponse = balanceProbe.expectMessageType[scalaimpl.GetBalanceReply.Succeeded]
      balanceResponse.balance shouldBe depositAmount
    }

    "restore initial state after stop and restart" in {
      assume(
        persistenceMode != PersistenceMode.Deferred,
        "Test skipped for Deferred persistence mode",
      )
      val accountId = BankAccountId(UUID.randomUUID())
      val limit = Money(100000, Money.JPY)
      val balance = Money(0, Money.JPY)
      // Create a first actor and build the initial state
      val bankAccountActor1 = spawn(createBankAccountAggregate(accountId))

      // Create an account
      val createProbe = createTestProbe[CreateReply]()
      bankAccountActor1 ! BankAccountCommand.Create(accountId, limit, balance, createProbe.ref)
      createProbe.expectMessageType[scalaimpl.CreateReply.Succeeded]

      // Stop the actor
      val stopProbe = createTestProbe[StopReply]()
      bankAccountActor1 ! BankAccountCommand.Stop(accountId, stopProbe.ref)
      stopProbe.expectMessageType[scalaimpl.StopReply.Succeeded]

      // Restart the actor (receiveRecover needs to be called at this point)
      val bankAccountActor2 = spawn(createBankAccountAggregate(accountId))

      // Check the balance - verify that the initial state has been correctly restored
      val balanceProbe = createTestProbe[GetBalanceReply]()
      bankAccountActor2 ! BankAccountCommand.GetBalance(accountId, balanceProbe.ref)

      val balanceResponse = balanceProbe.expectMessageType[scalaimpl.GetBalanceReply.Succeeded]
      // Since only account creation was done without deposit, balance should be 0 yen
      balanceResponse.balance shouldBe Money(0, Money.JPY)

      // Comment: If receiveRecover is called correctly, the state will be restored.
      // If it is not called, this test will fail.
    }
  }
  // Additional mode-specific test cases can be added in subclasses
}
