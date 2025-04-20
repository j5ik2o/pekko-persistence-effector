package com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.defaultStyle.nonPersistence.fp

import com.github.j5ik2o.pekko.persistence.effector.example.*
import com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.*
import com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.defaultStyle.nonPersistence.{
  BankAccount,
  BankAccountAggregateState,
}
import com.github.j5ik2o.pekko.persistence.effector.scaladsl.*
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors

object BankAccountAggregate {
  def actorName(aggregateId: BankAccountId): String =
    s"${aggregateId.aggregateTypeName}-${aggregateId.asString}"

  def apply(
    aggregateId: BankAccountId,
    persistenceMode: PersistenceMode = PersistenceMode.Persisted,
  ): Behavior[BankAccountCommand] =
    Behaviors.setup[BankAccountCommand] { implicit ctx =>
      handleNotCreated(BankAccountAggregateState.NotCreated(aggregateId))
    }

  private def handleNotCreated(state: BankAccountAggregateState.NotCreated): Behavior[BankAccountCommand] =
    Behaviors.receiveMessagePartial { case BankAccountCommand.Create(aggregateId, limit, balance, replyTo) =>
      val Result(bankAccount, event) = BankAccount.create(aggregateId, limit, balance)
      replyTo ! CreateReply.Succeeded(aggregateId)
      val newState: BankAccountAggregateState.Created =
        BankAccountAggregateState.Created(state.aggregateId, bankAccount)
      handleCreated(newState)
    }

  private def handleCreated(state: BankAccountAggregateState.Created): Behavior[BankAccountCommand] =
    Behaviors.receiveMessagePartial {
      case BankAccountCommand.Stop(aggregateId, replyTo) =>
        replyTo ! StopReply.Succeeded(aggregateId)
        Behaviors.stopped
      case BankAccountCommand.GetBalance(aggregateId, replyTo) =>
        replyTo ! GetBalanceReply.Succeeded(aggregateId, state.bankAccount.balance)
        Behaviors.same
      case BankAccountCommand.DepositCash(aggregateId, amount, replyTo) =>
        state.bankAccount
          .add(amount)
          .fold(
            error => {
              replyTo ! DepositCashReply.Failed(aggregateId, error)
              Behaviors.same
            },
            { case Result(newBankAccount, event) =>
              replyTo ! DepositCashReply.Succeeded(aggregateId, amount)
              handleCreated(state.copy(bankAccount = newBankAccount))
            },
          )
      case BankAccountCommand.WithdrawCash(aggregateId, amount, replyTo) =>
        state.bankAccount
          .subtract(amount)
          .fold(
            error => {
              replyTo ! WithdrawCashReply.Failed(aggregateId, error)
              Behaviors.same
            },
            { case Result(newBankAccount, event) =>
              replyTo ! WithdrawCashReply.Succeeded(aggregateId, amount)
              handleCreated(state.copy(bankAccount = newBankAccount))
            },
          )
    }
}
