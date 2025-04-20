package com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.standard.fp

import com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.*
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.scaladsl.{
  Effect,
  EventSourcedBehavior,
  ReplyEffect,
  RetentionCriteria,
}

import java.time.Instant

object BankAccountAggregate {
  def apply(id: BankAccountId): Behavior[BankAccountCommand] =
    EventSourcedBehavior
      .withEnforcedReplies(
        persistenceId = PersistenceId.ofUniqueId(id.asString),
        emptyState = BankAccountAggregateState.NotCreated(id),
        commandHandler = commandHandler,
        eventHandler = eventHandler,
      )
      .withRetention(RetentionCriteria.snapshotEvery(2, 2))
      .snapshotWhen((_, _, seqNr) => seqNr % 2 == 0)
      .withTagger(_ => Set(id.aggregateTypeName))

  private def eventHandler
    : (BankAccountAggregateState, BankAccountEvent) => BankAccountAggregateState = {
    case (state, event) => state.applyEvent(event)
  }

  private def commandHandler: (
    BankAccountAggregateState,
    BankAccountCommand) => ReplyEffect[BankAccountEvent, BankAccountAggregateState] = {
    (state, cmd) =>
      (state, cmd) match {
        case (
              BankAccountAggregateState.NotCreated(aggregateId),
              BankAccountCommand.Create(_, limit, balance, replyTo)) =>
          Effect
            .persist(
              BankAccountEvent.Created(aggregateId, limit, balance, Instant.now()),
            )
            .thenReply(replyTo) { _ =>
              CreateReply.Succeeded(aggregateId)
            }
        case (
              BankAccountAggregateState.Created(aggregateId, bankAccount),
              BankAccountCommand.DepositCash(_, amount, replyTo)) =>
          if (bankAccount.canAdd(amount)) {
            Effect
              .persist(
                BankAccountEvent.CashDeposited(aggregateId, amount, Instant.now()),
              )
              .thenReply(replyTo) { _ =>
                DepositCashReply.Succeeded(aggregateId, amount)
              }
          } else {
            Effect.none
              .thenReply(replyTo) { _ =>
                DepositCashReply.Failed(aggregateId, BankAccountError.LimitOverError)
              }
          }
        case (
              BankAccountAggregateState.Created(aggregateId, bankAccount),
              BankAccountCommand.WithdrawCash(_, amount, replyTo)) =>
          if (bankAccount.canSubtract(amount)) {
            Effect
              .persist(
                BankAccountEvent.CashWithdrew(aggregateId, amount, Instant.now()),
              )
              .thenReply(replyTo) { _ =>
                WithdrawCashReply.Succeeded(aggregateId, amount)
              }
          } else {
            Effect.none
              .thenReply(replyTo) { _ =>
                WithdrawCashReply.Failed(aggregateId, BankAccountError.InsufficientFundsError)
              }
          }
        case (
              BankAccountAggregateState.Created(aggregateId, bankAccount),
              BankAccountCommand.GetBalance(_, replyTo)) =>
          Effect.none
            .thenReply(replyTo) { _ =>
              GetBalanceReply.Succeeded(aggregateId, bankAccount.balance)
            }
        case (
              BankAccountAggregateState.Created(aggregateId, _),
              BankAccountCommand.Stop(_, replyTo)) =>
          Effect.none
            .thenReply(replyTo) { _ =>
              StopReply.Succeeded(aggregateId)
            }
        case (state, cmd) => Effect.unhandled.thenNoReply()
      }
  }
}
