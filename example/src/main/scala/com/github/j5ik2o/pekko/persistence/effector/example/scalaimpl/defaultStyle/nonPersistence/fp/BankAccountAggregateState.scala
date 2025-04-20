package com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.defaultStyle.nonPersistence.fp

import com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.{
  BankAccountEvent,
  BankAccountId,
}

enum BankAccountAggregateState {
  def aggregateId: BankAccountId

  case NotCreated(aggregateId: BankAccountId)
  case Created(aggregateId: BankAccountId, bankAccount: BankAccount)

  def applyEvent(event: BankAccountEvent): BankAccountAggregateState = (this, event) match {
    case (
          BankAccountAggregateState.NotCreated(aggregateId),
          BankAccountEvent.Created(id, limit, balance, _)) =>
      Created(id, BankAccount.create(id, limit, balance).bankAccount)
    case (
          BankAccountAggregateState.Created(id, bankAccount),
          BankAccountEvent.CashDeposited(_, amount, _)) =>
      bankAccount
        .add(amount)
        .fold(
          error => throw new IllegalStateException(s"Failed to apply event: $error"),
          result => BankAccountAggregateState.Created(id, result._1),
        )
    case (
          BankAccountAggregateState.Created(id, bankAccount),
          BankAccountEvent.CashWithdrew(_, amount, _)) =>
      bankAccount
        .subtract(amount)
        .fold(
          error => throw new IllegalStateException(s"Failed to apply event: $error"),
          result => BankAccountAggregateState.Created(id, result._1),
        )
    case _ =>
      throw new IllegalStateException(
        s"Invalid state transition: $this -> $event",
      )
  }
}
