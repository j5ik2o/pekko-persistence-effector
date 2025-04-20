package com.github.j5ik2o.pekko.persistence.effector.example.javaimpl.standard;

import com.github.j5ik2o.pekko.persistence.effector.example.javaimpl.*;
import java.io.Serializable;

/** Class representing a bank account */
public class BankAccount implements Serializable {
  private final BankAccountId bankAccountId;
  private final Money limit;
  private final Money balance;

  /**
   * Constructor
   *
   * @param bankAccountId Bank account ID
   * @param limit Limit amount
   * @param balance Balance
   */
  public BankAccount(BankAccountId bankAccountId, Money limit, Money balance) {
    this.bankAccountId = bankAccountId;
    this.limit = limit;
    this.balance = balance;
  }

  /**
   * Create a bank account
   *
   * @param bankAccountId Bank account ID
   * @param limit Limit amount
   * @param balance Balance
   * @return Bank account
   */
  public static BankAccount create(BankAccountId bankAccountId, Money limit, Money balance) {
    return new BankAccount(bankAccountId, limit, balance);
  }

  /**
   * Create a bank account with the specified bank account ID
   *
   * @param bankAccountId Bank account ID
   * @return Bank account
   */
  public static BankAccount create(BankAccountId bankAccountId) {
    return create(bankAccountId, Money.yens(100000), Money.zero(Money.JPY));
  }

  /**
   * Get the bank account ID
   *
   * @return Bank account ID
   */
  public BankAccountId getBankAccountId() {
    return bankAccountId;
  }

  /**
   * Get the limit amount
   *
   * @return Limit amount
   */
  public Money getLimit() {
    return limit;
  }

  /**
   * Get the balance
   *
   * @return Balance
   */
  public Money getBalance() {
    return balance;
  }

  /**
   * Check if the specified amount can be added
   *
   * @param amount Amount
   * @return true if the amount can be added, false otherwise
   */
  public boolean canAdd(Money amount) {
    return !limit.isLessThan(balance.plus(amount));
  }

  /**
   * Deposit cash
   *
   * @param amount Amount
   * @return Either containing the error or the new bank account
   */
  public Either<BankAccountError, BankAccount> add(Money amount) {
    if (!canAdd(amount)) {
      return Either.left(BankAccountError.LIMIT_OVER_ERROR);
    } else {
      return Either.right(new BankAccount(bankAccountId, limit, balance.plus(amount)));
    }
  }

  /**
   * Check if the specified amount can be subtracted
   *
   * @param amount Amount
   * @return true if the amount can be subtracted, false otherwise
   */
  public boolean canSubtract(Money amount) {
    return !Money.zero(Money.JPY).isGreaterThan(balance.minus(amount));
  }

  /**
   * Withdraw cash
   *
   * @param amount Amount
   * @return Either containing the error or the new bank account
   */
  public Either<BankAccountError, BankAccount> subtract(Money amount) {
    if (!canSubtract(amount)) {
      return Either.left(BankAccountError.INSUFFICIENT_FUNDS_ERROR);
    } else {
      return Either.right(new BankAccount(bankAccountId, limit, balance.minus(amount)));
    }
  }
}
