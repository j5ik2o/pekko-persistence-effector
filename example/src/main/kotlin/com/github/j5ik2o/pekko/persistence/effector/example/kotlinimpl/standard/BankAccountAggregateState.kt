package com.github.j5ik2o.pekko.persistence.effector.example.kotlinimpl.standard

import com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.BankAccountEvent
import com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.BankAccountId

/**
 * BankAccountAggregateStateクラス - Kotlin実装
 */
sealed class BankAccountAggregateState {
    abstract val aggregateId: BankAccountId

    /**
     * 未作成状態
     */
    data class NotCreated(override val aggregateId: BankAccountId) : BankAccountAggregateState()

    /**
     * 作成済み状態
     */
    data class Created(
        override val aggregateId: BankAccountId,
        val bankAccount: BankAccount
    ) : BankAccountAggregateState()

    /**
     * イベントを適用して状態を更新する
     */
    fun applyEvent(event: BankAccountEvent): BankAccountAggregateState {
        return when (val state = this) {
            is NotCreated -> {
                when (event) {
                    is BankAccountEvent.Created -> {
                        Created(
                            event.aggregateId(),
                            BankAccount.create(event.aggregateId(), event.limit(), event.balance())
                        )
                    }
                    else -> throw IllegalStateException("Invalid state transition: $state -> $event")
                }
            }
            is Created -> {
                when (event) {
                    is BankAccountEvent.CashDeposited -> {
                        state.bankAccount.add(event.amount()).fold(
                            { error -> throw IllegalStateException("Failed to apply event: $error") },
                            { result -> Created(state.aggregateId, result) }
                        )
                    }
                    is BankAccountEvent.CashWithdrew -> {
                        state.bankAccount.subtract(event.amount()).fold(
                            { error -> throw IllegalStateException("Failed to apply event: $error") },
                            { result -> Created(state.aggregateId, result) }
                        )
                    }
                    else -> throw IllegalStateException("Invalid state transition: $state -> $event")
                }
            }
        }
    }
}
