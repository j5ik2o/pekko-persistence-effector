package com.github.j5ik2o.pekko.persistence.effector.example.javaimpl.persistenceEffector.oop;

import com.github.j5ik2o.pekko.persistence.effector.example.javaimpl.*;
import java.io.Serializable;

/** Sealed interface representing the state of a bank account aggregate */
public sealed interface BankAccountAggregateState extends Serializable {
  /**
   * Get the aggregate ID
   *
   * @return Aggregate ID
   */
  BankAccountId getAggregateId();

  /**
   * Apply an event to the state
   *
   * @param event Event to apply
   * @return New state
   */
  BankAccountAggregateState applyEvent(BankAccountEvent event);

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

    @Override
    public BankAccountAggregateState applyEvent(BankAccountEvent event) {
      return switch (event) {
        case BankAccountEvent.Created created -> {
          var result = BankAccount.create(aggregateId, created.limit(), created.balance());
          yield new Created(aggregateId, result.getState());
        }
        default -> throw new IllegalStateException("Unexpected event: " + event);
      };
    }
  }

  /**
   * Created state
   *
   * @param aggregateId Aggregate ID
   * @param bankAccount Bank account
   */
  record Created(BankAccountId aggregateId, BankAccount bankAccount)
      implements BankAccountAggregateState {
    @Override
    public BankAccountId getAggregateId() {
      return aggregateId;
    }

    @Override
    public BankAccountAggregateState applyEvent(BankAccountEvent event) {
      return switch (event) {
        case BankAccountEvent.CashDeposited deposited -> {
          var result = bankAccount.add(deposited.amount());
          if (result.isLeft()) {
            throw new IllegalStateException("Failed to apply event: " + result.getLeft());
          } else {
            yield new Created(aggregateId, result.getRight().getState());
          }
        }
        case BankAccountEvent.CashWithdrew withdrew -> {
          var result = bankAccount.subtract(withdrew.amount());
          if (result.isLeft()) {
            throw new IllegalStateException("Failed to apply event: " + result.getLeft());
          } else {
            yield new Created(aggregateId, result.getRight().getState());
          }
        }
        default -> throw new IllegalStateException("Unexpected event: " + event);
      };
    }
  }
}
