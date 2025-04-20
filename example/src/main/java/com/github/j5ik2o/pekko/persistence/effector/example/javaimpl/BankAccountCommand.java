package com.github.j5ik2o.pekko.persistence.effector.example.javaimpl;

import java.io.Serializable;
import org.apache.pekko.actor.typed.ActorRef;

/** Sealed interface representing bank account commands */
public sealed interface BankAccountCommand extends Serializable {
  /**
   * Get the aggregate ID
   *
   * @return Aggregate ID
   */
  BankAccountId getAggregateId();

  /**
   * Get balance command
   *
   * @param aggregateId Aggregate ID
   * @param replyTo Reply destination
   */
  record GetBalance(BankAccountId aggregateId, ActorRef<CommandReply.GetBalanceReply> replyTo)
      implements BankAccountCommand {
    @Override
    public BankAccountId getAggregateId() {
      return aggregateId;
    }
  }

  /**
   * Stop command
   *
   * @param aggregateId Aggregate ID
   * @param replyTo Reply destination
   */
  record Stop(BankAccountId aggregateId, ActorRef<CommandReply.StopReply> replyTo)
      implements BankAccountCommand {
    @Override
    public BankAccountId getAggregateId() {
      return aggregateId;
    }
  }

  /**
   * Create command
   *
   * @param aggregateId Aggregate ID
   * @param replyTo Reply destination
   */
  record Create(BankAccountId aggregateId, ActorRef<CommandReply.CreateReply> replyTo)
      implements BankAccountCommand {
    @Override
    public BankAccountId getAggregateId() {
      return aggregateId;
    }
  }

  /**
   * Deposit cash command
   *
   * @param aggregateId Aggregate ID
   * @param amount Amount
   * @param replyTo Reply destination
   */
  record DepositCash(
      BankAccountId aggregateId, Money amount, ActorRef<CommandReply.DepositCashReply> replyTo)
      implements BankAccountCommand {
    @Override
    public BankAccountId getAggregateId() {
      return aggregateId;
    }
  }

  /**
   * Withdraw cash command
   *
   * @param aggregateId Aggregate ID
   * @param amount Amount
   * @param replyTo Reply destination
   */
  record WithdrawCash(
      BankAccountId aggregateId, Money amount, ActorRef<CommandReply.WithdrawCashReply> replyTo)
      implements BankAccountCommand {
    @Override
    public BankAccountId getAggregateId() {
      return aggregateId;
    }
  }
}
