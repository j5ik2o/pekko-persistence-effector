package com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.standard

import com.github.j5ik2o.pekko.persistence.effector.example.*
import com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.*

final case class BankAccount(
  bankAccountId: BankAccountId,
  limit: Money = Money(100000, Money.JPY),
  balance: Money = Money(0, Money.JPY),
) {

  def add(amount: Money): Either[BankAccountError, BankAccount] =
    if (!canAdd(amount))
      Left(BankAccountError.LimitOverError)
    else
      Right(copy(balance = balance + amount))

  def canAdd(amount: Money): Boolean =
    limit >= (balance + amount)

  def subtract(amount: Money): Either[BankAccountError, BankAccount] =
    if (!canSubtract(amount))
      Left(BankAccountError.InsufficientFundsError)
    else
      Right(copy(balance = balance - amount))

  def canSubtract(amount: Money): Boolean =
    Money(0, Money.JPY) <= (balance - amount)

}

object BankAccount {
  def create(
    bankAccountId: BankAccountId,
    limit: Money = Money(100000, Money.JPY),
    balance: Money = Money(0, Money.JPY),
  ): BankAccount =
    new BankAccount(bankAccountId, limit, balance)
}
