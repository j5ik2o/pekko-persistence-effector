package com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl

sealed trait BankAccountError

object BankAccountError {
  case object LimitOverError extends BankAccountError

  case object InsufficientFundsError extends BankAccountError
}
