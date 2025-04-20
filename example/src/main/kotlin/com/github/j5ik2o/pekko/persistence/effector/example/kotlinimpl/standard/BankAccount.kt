package com.github.j5ik2o.pekko.persistence.effector.example.kotlinimpl.standard

import com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.BankAccountError
import com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.BankAccountId
import com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.Money

/**
 * BankAccountクラス - Kotlin実装
 */
data class BankAccount(
    val bankAccountId: BankAccountId,
    val limit: Money = Money(100000, Money.JPY()),
    val balance: Money = Money(0, Money.JPY())
) {
    /**
     * 入金可能かどうかを判断する
     */
    fun canAdd(amount: Money): Boolean {
        return limit.compareTo(balance.plus(amount)) >= 0
    }

    /**
     * 入金処理
     */
    fun add(amount: Money): Either<BankAccountError, BankAccount> {
        return if (!canAdd(amount)) {
            Either.Left(BankAccountError.LimitOverError())
        } else {
            Either.Right(copy(balance = balance.plus(amount)))
        }
    }

    /**
     * 出金可能かどうかを判断する
     */
    fun canSubtract(amount: Money): Boolean {
        return Money(0, Money.JPY()).compareTo(balance.minus(amount)) <= 0
    }

    /**
     * 出金処理
     */
    fun subtract(amount: Money): Either<BankAccountError, BankAccount> {
        return if (!canSubtract(amount)) {
            Either.Left(BankAccountError.InsufficientFundsError())
        } else {
            Either.Right(copy(balance = balance.minus(amount)))
        }
    }

    companion object {
        /**
         * BankAccountを作成する
         */
        fun create(
            bankAccountId: BankAccountId,
            limit: Money = Money(100000, Money.JPY()),
            balance: Money = Money(0, Money.JPY())
        ): BankAccount {
            return BankAccount(bankAccountId, limit, balance)
        }
    }
}

/**
 * Kotlinで使用するEitherクラス
 */
sealed class Either<out L, out R> {
    data class Left<out L>(val value: L) : Either<L, Nothing>()
    data class Right<out R>(val value: R) : Either<Nothing, R>()

    fun <T> fold(leftFn: (L) -> T, rightFn: (R) -> T): T {
        return when (this) {
            is Left -> leftFn(value)
            is Right -> rightFn(value)
        }
    }
}
