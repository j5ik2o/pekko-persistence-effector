package com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.defaultStyle.nonPersistence.oop

import com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.*
import com.github.j5ik2o.pekko.persistence.effector.example.scalaimpl.defaultStyle.nonPersistence.{
  BankAccount,
  BankAccountAggregateState,
}
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}

object BankAccountAggregate {
  def actorName(aggregateId: BankAccountId): String =
    s"${aggregateId.aggregateTypeName}-${aggregateId.asString}"

  def apply(
    id: BankAccountId,
  ): Behavior[BankAccountCommand] =
    Behaviors.setup { implicit ctx =>
      new BankAccountAggregate(id, BankAccountAggregateState.NotCreated(id))(ctx)
    }
}

final class BankAccountAggregate(id: BankAccountId, initialState: BankAccountAggregateState)(
  ctx: ActorContext[BankAccountCommand],
) extends AbstractBehavior[BankAccountCommand](ctx) {

  private var bf = initialState match {
    case state: BankAccountAggregateState.NotCreated =>
      notCreated(state)
    case state: BankAccountAggregateState.Created =>
      created(state)
  }

  override def onMessage(msg: BankAccountCommand): Behavior[BankAccountCommand] =
    bf(msg)

  private def notCreated(state: BankAccountAggregateState.NotCreated)
    : PartialFunction[BankAccountCommand, Behavior[BankAccountCommand]] = {
    case BankAccountCommand.Create(aggregateId, limit, balance, replyTo) =>
      val Result(bankAccount, event) = BankAccount.create(aggregateId, limit, balance)
      replyTo ! CreateReply.Succeeded(aggregateId)
      val newState: BankAccountAggregateState.Created =
        BankAccountAggregateState.Created(state.aggregateId, bankAccount)
      bf = created(newState)
      this
  }

  private def created(state: BankAccountAggregateState.Created)
    : PartialFunction[BankAccountCommand, Behavior[BankAccountCommand]] = {
    case BankAccountCommand.Stop(aggregateId, replyTo) =>
      replyTo ! StopReply.Succeeded(aggregateId)
      Behaviors.stopped
    case BankAccountCommand.GetBalance(aggregateId, replyTo) =>
      replyTo ! GetBalanceReply.Succeeded(aggregateId, state.bankAccount.balance)
      Behaviors.same
    case BankAccountCommand.DepositCash(aggregateId, amount, replyTo) =>
      state.bankAccount
        .add(amount)
        .fold(
          error => {
            replyTo ! DepositCashReply.Failed(aggregateId, error)
            Behaviors.same
          },
          { case Result(newBankAccount, event) =>
            replyTo ! DepositCashReply.Succeeded(aggregateId, amount)
            bf = created(state.copy(bankAccount = newBankAccount))
            this
          },
        )
    case BankAccountCommand.WithdrawCash(aggregateId, amount, replyTo) =>
      state.bankAccount
        .subtract(amount)
        .fold(
          error => {
            replyTo ! WithdrawCashReply.Failed(aggregateId, error)
            Behaviors.same
          },
          { case Result(newBankAccount, event) =>
            replyTo ! WithdrawCashReply.Succeeded(aggregateId, amount)
            bf = created(state.copy(bankAccount = newBankAccount))
            this
          },
        )
    case _ =>
      Behaviors.unhandled
  }

}
