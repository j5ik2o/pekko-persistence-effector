package com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.persistenceEffector.fp

import com.github.j5ik2o.pekko.persistence.effector.example.*
import com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.*
import com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.persistenceEffector.{
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
  ): Behavior[BankAccountCommand] = {
    val config = PersistenceEffectorConfig
      .create[BankAccountAggregateState, BankAccountEvent, BankAccountCommand](
        persistenceId = PersistenceId.ofUniqueId(actorName(aggregateId)),
        initialState = BankAccountAggregateState.NotCreated(aggregateId),
        applyEvent = (state, event) => state.applyEvent(event),
      )
      .withPersistenceMode(persistenceMode)
      .withSnapshotCriteria(SnapshotCriteria.every(2))
      .withRetentionCriteria(RetentionCriteria.snapshotEvery(2))
    Behaviors.setup[BankAccountCommand] { implicit ctx =>
      PersistenceEffector
        .fromConfig[BankAccountAggregateState, BankAccountEvent, BankAccountCommand](
          config,
        ) {
          case (initialState: BankAccountAggregateState.NotCreated, effector) =>
            handleNotCreated(initialState, effector)
          case (initialState: BankAccountAggregateState.Created, effector) =>
            handleCreated(initialState, effector)
        }
    }
  }

  private def handleNotCreated(
    state: BankAccountAggregateState.NotCreated,
    effector: PersistenceEffector[BankAccountAggregateState, BankAccountEvent, BankAccountCommand])
    : Behavior[BankAccountCommand] =
    Behaviors.receiveMessagePartial { case BankAccountCommand.Create(aggregateId, limit, balance, replyTo) =>
      val Result(bankAccount, event) = BankAccount.create(aggregateId, limit, balance)
      effector.persistEvent(event) { _ =>
        replyTo ! CreateReply.Succeeded(aggregateId)
        val newState: BankAccountAggregateState.Created =
          BankAccountAggregateState.Created(state.aggregateId, bankAccount)
        handleCreated(newState, effector)
      }
    }

  private def handleCreated(
    state: BankAccountAggregateState.Created,
    effector: PersistenceEffector[BankAccountAggregateState, BankAccountEvent, BankAccountCommand])
    : Behavior[BankAccountCommand] =
    effector.persistSnapshot(state) { _ =>
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
                effector.persistEvent(event) { _ =>
                  replyTo ! DepositCashReply.Succeeded(aggregateId, amount)
                  handleCreated(state.copy(bankAccount = newBankAccount), effector)
                }
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
                effector.persistEvent(event) { _ =>
                  replyTo ! WithdrawCashReply.Succeeded(aggregateId, amount)
                  handleCreated(state.copy(bankAccount = newBankAccount), effector)
                }
              },
            )
      }
    }
}
