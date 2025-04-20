package com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl

import java.time.Instant

enum BankAccountEvent {
  def aggregateId: BankAccountId
  def occurredAt: Instant

  case Created(aggregateId: BankAccountId, limit: Money, balance: Money, occurredAt: Instant)
  case CashDeposited(aggregateId: BankAccountId, amount: Money, occurredAt: Instant)
  case CashWithdrew(aggregateId: BankAccountId, amount: Money, occurredAt: Instant)
}
