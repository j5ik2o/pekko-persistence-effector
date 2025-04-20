package com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.pe

import com.github.j5ik2o.pekko.persistence.effector.example.*
import com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.*

import java.time.Instant

final case class BankAccount(
  bankAccountId: BankAccountId,
  limit: Money,
  balance: Money,
) {

  def add(amount: Money): Either[BankAccountError, Result[BankAccount, BankAccountEvent]] =
    if (limit < (balance + amount))
      Left(BankAccountError.LimitOverError)
    else
      Right(
        Result(
          copy(balance = balance + amount),
          BankAccountEvent.CashDeposited(bankAccountId, amount, Instant.now())))

  def subtract(amount: Money): Either[BankAccountError, Result[BankAccount, BankAccountEvent]] =
    if (Money(0, Money.JPY) > (balance - amount))
      Left(BankAccountError.InsufficientFundsError)
    else
      Right(
        Result(
          copy(balance = balance - amount),
          BankAccountEvent.CashWithdrew(bankAccountId, amount, Instant.now())))

}

object BankAccount {

  def create(
    bankAccountId: BankAccountId,
    limit: Money,
    balance: Money,
  ): Result[BankAccount, BankAccountEvent] =
    Result(
      new BankAccount(bankAccountId, limit, balance),
      BankAccountEvent.Created(bankAccountId, limit, balance, Instant.now()))
}
