package com.github.j5ik2o.pekko.persistence.effector.example.kotlinimpl.standard

import com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.*
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.scaladsl.Effect
import org.apache.pekko.persistence.typed.scaladsl.EventSourcedBehavior
import org.apache.pekko.persistence.typed.scaladsl.ReplyEffect
import java.time.Instant

/**
 * BankAccountAggregateクラス - Kotlin実装
 */
object BankAccountAggregate {
    /**
     * BankAccountAggregateのBehaviorを作成する
     */
    fun apply(id: BankAccountId): Behavior<BankAccountCommand> {
        return EventSourcedBehavior
            .withEnforcedReplies(
                persistenceId = PersistenceId.ofUniqueId(id.asString()),
                emptyState = BankAccountAggregateState.NotCreated(id),
                commandHandler = ::commandHandler,
                eventHandler = ::eventHandler
            )
            .withTagger { setOf(id.aggregateTypeName()) }
    }

    /**
     * イベントハンドラ
     */
    private fun eventHandler(
        state: BankAccountAggregateState,
        event: BankAccountEvent
    ): BankAccountAggregateState {
        return state.applyEvent(event)
    }

    /**
     * コマンドハンドラ
     */
    private fun commandHandler(
        state: BankAccountAggregateState,
        cmd: BankAccountCommand
    ): ReplyEffect<BankAccountEvent, BankAccountAggregateState> {
        return when {
            state is BankAccountAggregateState.NotCreated && cmd is BankAccountCommand.Create -> {
                Effect
                    .persist(
                        BankAccountEvent.Created(cmd.aggregateId(), cmd.limit(), cmd.balance(), Instant.now())
                    )
                    .thenReply(cmd.replyTo()) { _ ->
                        CreateReply.Succeeded(cmd.aggregateId())
                    }
            }
            state is BankAccountAggregateState.Created && cmd is BankAccountCommand.DepositCash -> {
                if (state.bankAccount.canAdd(cmd.amount())) {
                    Effect
                        .persist(
                            BankAccountEvent.CashDeposited(cmd.aggregateId(), cmd.amount(), Instant.now())
                        )
                        .thenReply(cmd.replyTo()) { _ ->
                            DepositCashReply.Succeeded(cmd.aggregateId(), cmd.amount())
                        }
                } else {
                    Effect.none<BankAccountEvent, BankAccountAggregateState>()
                        .thenReply(cmd.replyTo()) { _ ->
                            DepositCashReply.Failed(cmd.aggregateId(), BankAccountError.LimitOverError())
                        }
                }
            }
            state is BankAccountAggregateState.Created && cmd is BankAccountCommand.WithdrawCash -> {
                if (state.bankAccount.canSubtract(cmd.amount())) {
                    Effect
                        .persist(
                            BankAccountEvent.CashWithdrew(cmd.aggregateId(), cmd.amount(), Instant.now())
                        )
                        .thenReply(cmd.replyTo()) { _ ->
                            WithdrawCashReply.Succeeded(cmd.aggregateId(), cmd.amount())
                        }
                } else {
                    Effect.none<BankAccountEvent, BankAccountAggregateState>()
                        .thenReply(cmd.replyTo()) { _ ->
                            WithdrawCashReply.Failed(cmd.aggregateId(), BankAccountError.InsufficientFundsError())
                        }
                }
            }
            state is BankAccountAggregateState.Created && cmd is BankAccountCommand.GetBalance -> {
                Effect.none<BankAccountEvent, BankAccountAggregateState>()
                    .thenReply(cmd.replyTo()) { _ ->
                        GetBalanceReply.Succeeded(cmd.aggregateId(), state.bankAccount.balance)
                    }
            }
            state is BankAccountAggregateState.Created && cmd is BankAccountCommand.Stop -> {
                Effect.none<BankAccountEvent, BankAccountAggregateState>()
                    .thenReply(cmd.replyTo()) { _ ->
                        StopReply.Succeeded(cmd.aggregateId())
                    }
            }
            else -> Effect.unhandled<BankAccountEvent, BankAccountAggregateState>().thenNoReply()
        }
    }
}
