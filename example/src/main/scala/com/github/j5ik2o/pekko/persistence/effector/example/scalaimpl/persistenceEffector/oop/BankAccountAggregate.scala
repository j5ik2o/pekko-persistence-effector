package com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.persistenceEffector.oop

import com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.persistenceEffector.{
  BankAccount,
  BankAccountAggregateState,
}
import com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.{
  BankAccountCommand,
  BankAccountEvent,
  BankAccountId,
  CreateReply,
  DepositCashReply,
  GetBalanceReply,
  Result,
  StopReply,
  WithdrawCashReply,
}
import com.github.j5ik2o.pekko.persistence.effector.scaladsl.{
  PersistenceEffector,
  PersistenceEffectorConfig,
  PersistenceMode,
  RetentionCriteria,
  SnapshotCriteria,
}
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.{AbstractBehavior, ActorContext}
import org.apache.pekko.actor.typed.scaladsl.Behaviors

object BankAccountAggregate {
  def actorName(aggregateId: BankAccountId): String =
    s"${aggregateId.aggregateTypeName}-${aggregateId.asString}"

  def apply(
    id: BankAccountId,
    persistenceMode: PersistenceMode = PersistenceMode.Persisted): Behavior[BankAccountCommand] = {
    val config = PersistenceEffectorConfig
      .create[BankAccountAggregateState, BankAccountEvent, BankAccountCommand](
        persistenceId = actorName(id),
        initialState = BankAccountAggregateState.NotCreated(id),
        applyEvent = (state, event) => state.applyEvent(event),
      )
      .withPersistenceMode(persistenceMode)
      .withSnapshotCriteria(SnapshotCriteria.every(2))
      .withRetentionCriteria(RetentionCriteria.snapshotEvery(2, 2))
    Behaviors.setup { implicit ctx =>
      PersistenceEffector
        .fromConfig[BankAccountAggregateState, BankAccountEvent, BankAccountCommand](
          config,
        ) { case (initialState: BankAccountAggregateState, effector) =>
          new BankAccountAggregate(id, initialState)(ctx, effector)
        }

    }
  }
}

final class BankAccountAggregate(id: BankAccountId, initialState: BankAccountAggregateState)(
  ctx: ActorContext[BankAccountCommand],
  effector: PersistenceEffector[BankAccountAggregateState, BankAccountEvent, BankAccountCommand])
  extends AbstractBehavior[BankAccountCommand](ctx) {

  private var bf = initialState match {
    case state: BankAccountAggregateState.NotCreated =>
      notCreated(state)
    case state: BankAccountAggregateState.Created =>
      created(state)
  }

  override def onMessage(msg: BankAccountCommand): Behavior[BankAccountCommand] =
    bf(msg)

  private def notCreated(state: BankAccountAggregateState.NotCreated)
    : PartialFunction[BankAccountCommand, Behavior[BankAccountCommand]] = {
    case BankAccountCommand.Create(aggregateId, limit, balance, replyTo) =>
      val Result(bankAccount, event) = BankAccount.create(aggregateId, limit, balance)
      effector.persistEvent(event) { _ =>
        replyTo ! CreateReply.Succeeded(aggregateId)
        val newState: BankAccountAggregateState.Created =
          BankAccountAggregateState.Created(state.aggregateId, bankAccount)
        bf = created(newState)
        this
      }
  }

  private def created(state: BankAccountAggregateState.Created)
    : PartialFunction[BankAccountCommand, Behavior[BankAccountCommand]] = { msg =>
    effector.persistSnapshot(state) { _ =>
      msg match {
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
                  bf = created(state.copy(bankAccount = newBankAccount))
                  this
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
                  bf = created(state.copy(bankAccount = newBankAccount))
                  this
                }
              },
            )
        case _ =>
          Behaviors.unhandled
      }
    }

  }

}
