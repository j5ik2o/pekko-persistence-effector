package com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl

import com.github.j5ik2o.pekko.persistence.effector.example.*
import com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.*
import org.apache.pekko.actor.typed.ActorRef

enum BankAccountCommand {
  case GetBalance(override val aggregateId: BankAccountId, replyTo: ActorRef[GetBalanceReply])
  case Stop(override val aggregateId: BankAccountId, replyTo: ActorRef[StopReply])
  case Create(
    override val aggregateId: BankAccountId,
    limit: Money = Money(100000, Money.JPY),
    balance: Money = Money(0, Money.JPY),
    replyTo: ActorRef[CreateReply])
  case DepositCash(override val aggregateId: BankAccountId, amount: Money, replyTo: ActorRef[DepositCashReply])
  case WithdrawCash(override val aggregateId: BankAccountId, amount: Money, replyTo: ActorRef[WithdrawCashReply])

  def aggregateId: BankAccountId = this match {
    case GetBalance(aggregateId, _) => aggregateId
    case Stop(aggregateId, _) => aggregateId
    case Create(aggregateId, _, _, _) => aggregateId
    case DepositCash(aggregateId, _, _) => aggregateId
    case WithdrawCash(aggregateId, _, _) => aggregateId
  }
}

object BankAccountCommand {
  type Message = BankAccountCommand
}

enum StopReply {
  case Succeeded(aggregateId: BankAccountId)
}

enum GetBalanceReply {
  case Succeeded(aggregateId: BankAccountId, balance: Money)
}

enum CreateReply {
  case Succeeded(aggregateId: BankAccountId)
}

enum DepositCashReply {
  case Succeeded(aggregateId: BankAccountId, amount: Money)
  case Failed(aggregateId: BankAccountId, error: BankAccountError)
}

enum WithdrawCashReply {
  case Succeeded(aggregateId: BankAccountId, amount: Money)
  case Failed(aggregateId: BankAccountId, error: BankAccountError)
}
