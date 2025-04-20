package com.github.j5ik2o.pekko.persistence.effector.example.javaimpl

import com.github.j5ik2o.pekko.persistence.effector.example.TestConfig
import com.github.j5ik2o.pekko.persistence.effector.javadsl.PersistenceMode
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.actor.typed.Behavior
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.math.BigDecimal as JavaBigDecimal
import java.util.UUID
import _root_.scala.concurrent.duration.*

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

  /**
   * Method to be implemented in subclasses to specify the PersistenceMode to be tested.
   *
   * @return
   *   PersistenceMode to use for tests
   */
  def persistenceMode: PersistenceMode

  /**
   * Helper method to create a BankAccountAggregate with the specified persistence mode.
   *
   * @param accountId
   *   Bank account ID
   * @return
   *   Behavior for the BankAccountAggregate actor
   */
  def createBankAccountAggregate(accountId: BankAccountId): Behavior[BankAccountCommand]

  s"BankAccountAggregate with $persistenceMode mode" should {
    "create a new bank account successfully" in {
      val accountId = BankAccountId(UUID.randomUUID())

      val bankAccountActor = spawn(createBankAccountAggregate(accountId))

      val probe = createTestProbe[CommandReply.CreateReply]()
      bankAccountActor ! BankAccountCommand.Create(accountId, probe.ref)

      val response = probe.expectMessageType[CommandReply.CreateReply]
      response shouldBe CommandReply.CreateReply.succeeded(accountId)
      response.getAggregateId shouldBe accountId
    }

    "deposit cash successfully" in {
      val accountId = BankAccountId(UUID.randomUUID())

      val bankAccountActor = spawn(createBankAccountAggregate(accountId))

      // Create account
      val createProbe = createTestProbe[CommandReply.CreateReply]()
      bankAccountActor ! BankAccountCommand.Create(accountId, createProbe.ref)
      createProbe.expectMessageType[CommandReply.CreateReply]

      val depositProbe = createTestProbe[CommandReply.DepositCashReply]()

      for { _ <- 1 to 10 } {
        // Deposit
        val depositAmount = Money.of(JavaBigDecimal.valueOf(10000), Money.JPY)
        bankAccountActor ! BankAccountCommand.DepositCash(accountId, depositAmount, depositProbe.ref)

        val depositResponse = depositProbe.expectMessageType[CommandReply.DepositCashReply]
        depositResponse shouldBe CommandReply.DepositCashReply.succeeded(accountId, depositAmount)
        depositResponse.getAggregateId shouldBe accountId
        depositResponse.getAmount shouldBe depositAmount
      }

      // Stop account
      val stopProbe = createTestProbe[CommandReply.StopReply]()
      bankAccountActor ! BankAccountCommand.Stop(accountId, stopProbe.ref)
      stopProbe.expectMessageType[CommandReply.StopReply]

      val bankAccountActor2 = spawn(createBankAccountAggregate(accountId))

      // Check balance
      val balanceProbe = createTestProbe[CommandReply.GetBalanceReply]()
      bankAccountActor2 ! BankAccountCommand.GetBalance(accountId, balanceProbe.ref)

      val balanceResponse = balanceProbe.expectMessageType[CommandReply.GetBalanceReply]
      balanceResponse.getBalance shouldBe Money.of(JavaBigDecimal.valueOf(100000), Money.JPY)
    }

    "withdraw cash successfully" in {
      val accountId = BankAccountId(UUID.randomUUID())

      val bankAccountActor = spawn(createBankAccountAggregate(accountId))

      // Create an account
      val createProbe = createTestProbe[CommandReply.CreateReply]()
      bankAccountActor ! BankAccountCommand.Create(accountId, createProbe.ref)
      createProbe.expectMessageType[CommandReply.CreateReply]

      // Deposit
      val depositAmount = Money.of(JavaBigDecimal.valueOf(10000), Money.JPY)
      val depositProbe = createTestProbe[CommandReply.DepositCashReply]()
      bankAccountActor ! BankAccountCommand.DepositCash(accountId, depositAmount, depositProbe.ref)
      depositProbe.expectMessageType[CommandReply.DepositCashReply]

      // Withdraw
      val withdrawAmount = Money.of(JavaBigDecimal.valueOf(3000), Money.JPY)
      val withdrawProbe = createTestProbe[CommandReply.WithdrawCashReply]()
      bankAccountActor ! BankAccountCommand.WithdrawCash(accountId, withdrawAmount, withdrawProbe.ref)

      val withdrawResponse = withdrawProbe.expectMessageType[CommandReply.WithdrawCashReply]
      withdrawResponse.getAggregateId shouldBe accountId
      withdrawResponse.getAmount shouldBe withdrawAmount

      // Check balance
      val balanceProbe = createTestProbe[CommandReply.GetBalanceReply]()
      bankAccountActor ! BankAccountCommand.GetBalance(accountId, balanceProbe.ref)

      val balanceResponse = balanceProbe.expectMessageType[CommandReply.GetBalanceReply]
      balanceResponse.getBalance shouldBe Money.of(JavaBigDecimal.valueOf(7000), Money.JPY)
    }

    "get balance successfully" in {
      val accountId = BankAccountId(UUID.randomUUID())

      val bankAccountActor = spawn(createBankAccountAggregate(accountId))

      // Create an account
      val createProbe = createTestProbe[CommandReply.CreateReply]()
      bankAccountActor ! BankAccountCommand.Create(accountId, createProbe.ref)
      createProbe.expectMessageType[CommandReply.CreateReply]

      // Check the initial balance
      val initialBalanceProbe = createTestProbe[CommandReply.GetBalanceReply]()
      bankAccountActor ! BankAccountCommand.GetBalance(accountId, initialBalanceProbe.ref)

      val initialBalanceResponse =
        initialBalanceProbe.expectMessageType[CommandReply.GetBalanceReply]
      initialBalanceResponse.getBalance shouldBe Money.of(JavaBigDecimal.valueOf(0), Money.JPY)
    }

    "fail to withdraw when insufficient funds" in {
      val accountId = BankAccountId(UUID.randomUUID())

      val bankAccountActor = spawn(createBankAccountAggregate(accountId))

      // Create an account
      val createProbe = createTestProbe[CommandReply.CreateReply]()
      bankAccountActor ! BankAccountCommand.Create(accountId, createProbe.ref)
      createProbe.expectMessageType[CommandReply.CreateReply]

      // Attempt to withdraw more than the deposit amount
      val withdrawAmount = Money.of(JavaBigDecimal.valueOf(1000), Money.JPY)
      val withdrawProbe = createTestProbe[CommandReply.WithdrawCashReply]()
      bankAccountActor ! BankAccountCommand.WithdrawCash(accountId, withdrawAmount, withdrawProbe.ref)

      val failedResponse = withdrawProbe.expectMessageType[CommandReply.WithdrawCashReply]
      failedResponse.getAggregateId shouldBe accountId
      failedResponse.getError shouldBe BankAccountError.INSUFFICIENT_FUNDS_ERROR
    }

    "fail to deposit when over limit" in {
      val accountId = BankAccountId(UUID.randomUUID())

      val bankAccountActor = spawn(createBankAccountAggregate(accountId))

      // Create an account
      val createProbe = createTestProbe[CommandReply.CreateReply]()
      bankAccountActor ! BankAccountCommand.Create(accountId, createProbe.ref)
      createProbe.expectMessageType[CommandReply.CreateReply]

      // Attempt to deposit more than the limit
      val depositAmount =
        Money.of(JavaBigDecimal.valueOf(150000), Money.JPY) // The limit is 100000 yen
      val depositProbe = createTestProbe[CommandReply.DepositCashReply]()
      bankAccountActor ! BankAccountCommand.DepositCash(accountId, depositAmount, depositProbe.ref)

      val failedResponse = depositProbe.expectMessageType[CommandReply.DepositCashReply]
      failedResponse.getAggregateId shouldBe accountId
      failedResponse.getError shouldBe BankAccountError.LIMIT_OVER_ERROR
    }

    "maintain state after stop and restart with multiple actions" in {
      val accountId = BankAccountId(UUID.randomUUID())

      // Create the first actor and build state
      val bankAccountActor1 = spawn(createBankAccountAggregate(accountId))

      // Create an account
      val createProbe = createTestProbe[CommandReply.CreateReply]()
      bankAccountActor1 ! BankAccountCommand.Create(accountId, createProbe.ref)
      createProbe.expectMessageType[CommandReply.CreateReply]

      // Deposit
      val depositAmount = Money.of(JavaBigDecimal.valueOf(50000), Money.JPY)
      val depositProbe = createTestProbe[CommandReply.DepositCashReply]()
      bankAccountActor1 ! BankAccountCommand.DepositCash(accountId, depositAmount, depositProbe.ref)
      depositProbe.expectMessageType[CommandReply.DepositCashReply]

      // Deposit again
      val depositAmount2 = Money.of(JavaBigDecimal.valueOf(20000), Money.JPY)
      val depositProbe2 = createTestProbe[CommandReply.DepositCashReply]()
      bankAccountActor1 ! BankAccountCommand.DepositCash(accountId, depositAmount2, depositProbe2.ref)
      depositProbe2.expectMessageType[CommandReply.DepositCashReply]

      // Explicitly stop to create a snapshot
      val stopProbe = createTestProbe[CommandReply.StopReply]()
      bankAccountActor1 ! BankAccountCommand.Stop(accountId, stopProbe.ref)
      stopProbe.expectMessageType[CommandReply.StopReply]

      // Create a second actor - at this point receiveRecover of PersistenceStoreActor is called
      val bankAccountActor2 = spawn(createBankAccountAggregate(accountId))

      // Check balance - verify that the state of the previous actor has been restored
      val expectedBalance = Money.of(JavaBigDecimal.valueOf(70000), Money.JPY) // 50000 + 20000
      val balanceProbe = createTestProbe[CommandReply.GetBalanceReply]()
      bankAccountActor2 ! BankAccountCommand.GetBalance(accountId, balanceProbe.ref)

      val balanceResponse = balanceProbe.expectMessageType[CommandReply.GetBalanceReply]
      balanceResponse.getBalance shouldBe expectedBalance

      // Verify that operations can be performed normally after actor restart
      val withdrawAmount = Money.of(JavaBigDecimal.valueOf(10000), Money.JPY)
      val withdrawProbe = createTestProbe[CommandReply.WithdrawCashReply]()
      bankAccountActor2 ! BankAccountCommand.WithdrawCash(accountId, withdrawAmount, withdrawProbe.ref)

      val withdrawResponse = withdrawProbe.expectMessageType[CommandReply.WithdrawCashReply]
      withdrawResponse.getAmount shouldBe withdrawAmount

      // Final balance check
      val finalBalanceProbe = createTestProbe[CommandReply.GetBalanceReply]()
      bankAccountActor2 ! BankAccountCommand.GetBalance(accountId, finalBalanceProbe.ref)

      val finalResponse = finalBalanceProbe.expectMessageType[CommandReply.GetBalanceReply]
      finalResponse.getBalance shouldBe Money.of(
        JavaBigDecimal.valueOf(60000),
        Money.JPY,
      ) // 70000 - 10000
    }

    "maintain state after stop and restart" in {
      val accountId = BankAccountId(UUID.randomUUID())

      // Create the first actor and build state
      val bankAccountActor1 = spawn(createBankAccountAggregate(accountId))

      // Create an account
      val createProbe = createTestProbe[CommandReply.CreateReply]()
      bankAccountActor1 ! BankAccountCommand.Create(accountId, createProbe.ref)
      createProbe.expectMessageType[CommandReply.CreateReply]

      // Deposit
      val depositAmount = Money.of(JavaBigDecimal.valueOf(50000), Money.JPY)
      val depositProbe = createTestProbe[CommandReply.DepositCashReply]()
      bankAccountActor1 ! BankAccountCommand.DepositCash(accountId, depositAmount, depositProbe.ref)
      depositProbe.expectMessageType[CommandReply.DepositCashReply]

      // Explicitly stop to create a snapshot
      val stopProbe = createTestProbe[CommandReply.StopReply]()
      bankAccountActor1 ! BankAccountCommand.Stop(accountId, stopProbe.ref)
      stopProbe.expectMessageType[CommandReply.StopReply]

      // Restart the actor (receiveRecover is called at this point)
      val bankAccountActor2 = spawn(createBankAccountAggregate(accountId))

      // Check balance - verify that the state of the previous actor has been restored
      val balanceProbe = createTestProbe[CommandReply.GetBalanceReply]()
      bankAccountActor2 ! BankAccountCommand.GetBalance(accountId, balanceProbe.ref)

      val balanceResponse = balanceProbe.expectMessageType[CommandReply.GetBalanceReply]
      balanceResponse.getBalance shouldBe depositAmount
    }

    "restore initial state after stop and restart" in {
      val accountId = BankAccountId(UUID.randomUUID())

      // Create the first actor and build the initial state
      val bankAccountActor1 = spawn(createBankAccountAggregate(accountId))

      // Create account
      val createProbe = createTestProbe[CommandReply.CreateReply]()
      bankAccountActor1 ! BankAccountCommand.Create(accountId, createProbe.ref)
      createProbe.expectMessageType[CommandReply.CreateReply]

      // Stop the actor
      val stopProbe = createTestProbe[CommandReply.StopReply]()
      bankAccountActor1 ! BankAccountCommand.Stop(accountId, stopProbe.ref)
      stopProbe.expectMessageType[CommandReply.StopReply]

      // Restart the actor (receiveRecover needs to be called at this point)
      val bankAccountActor2 = spawn(createBankAccountAggregate(accountId))

      // Check balance - verify that the initial state has been correctly restored
      val balanceProbe = createTestProbe[CommandReply.GetBalanceReply]()
      bankAccountActor2 ! BankAccountCommand.GetBalance(accountId, balanceProbe.ref)

      val balanceResponse = balanceProbe.expectMessageType[CommandReply.GetBalanceReply]
      // Since only account creation was done without any deposits, the balance should be 0 yen
      balanceResponse.getBalance shouldBe Money.of(JavaBigDecimal.valueOf(0), Money.JPY)

      // Comment: If receiveRecover is called correctly, the state will be restored.
      // If it is not called, this test will fail.
    }
  }
  // Additional mode-specific test cases can be added in subclasses
}
