package com.github.j5ik2o.pekko.persistence.effector.example.javaimpl.standard;

import com.github.j5ik2o.pekko.persistence.effector.example.javaimpl.BankAccountId;

/**
 * Sealed interface representing the state of a bank account
 */
public sealed interface BankAccountAggregateState {
  /**
   * Get the aggregate ID
   *
   * @return Aggregate ID
   */
  BankAccountId getAggregateId();

  /**
   * Not created state
   *
   * @param aggregateId Aggregate ID
   */
  record NotCreated(BankAccountId aggregateId) implements BankAccountAggregateState {
    @Override
    public BankAccountId getAggregateId() {
      return aggregateId;
    }
  }

  /**
   * Created state
   *
   * @param aggregateId Aggregate ID
   * @param bankAccount Bank account
   */
  record Created(BankAccountId aggregateId, BankAccount bankAccount) implements BankAccountAggregateState {
    @Override
    public BankAccountId getAggregateId() {
      return aggregateId;
    }
  }
}
