package com.github.j5ik2o.pekko.persistence.effector.example.javaimpl;

import java.io.Serializable;

/**
 * Sealed interface representing command replies
 */
public sealed interface CommandReply extends Serializable {
  /**
   * Get the aggregate ID
   *
   * @return Aggregate ID
   */
  BankAccountId getAggregateId();

  /**
   * Check if the reply is successful
   *
   * @return true if successful, false otherwise
   */
  boolean isSuccessful();


  /**
   * Get balance reply
   *
   * @param aggregateId Aggregate ID
   * @param balance     Balance
   * @param error       Error
   */
  record GetBalanceReply(BankAccountId aggregateId, Money balance, BankAccountError error) implements CommandReply {
    @Override
    public BankAccountId getAggregateId() {
      return aggregateId;
    }

    @Override
    public boolean isSuccessful() {
      return error == null;
    }

    /**
     * Create a successful reply
     *
     * @param aggregateId Aggregate ID
     * @param balance     Balance
     * @return Reply
     */
    public static GetBalanceReply succeeded(BankAccountId aggregateId, Money balance) {
      return new GetBalanceReply(aggregateId, balance, null);
    }

    /**
     * Create a failed reply
     *
     * @param aggregateId Aggregate ID
     * @param error       Error
     * @return Reply
     */
    public static GetBalanceReply failed(BankAccountId aggregateId, BankAccountError error) {
      return new GetBalanceReply(aggregateId, null, error);
    }
  }

  /**
   * Stop reply
   *
   * @param aggregateId Aggregate ID
   * @param error       Error
   */
  record StopReply(BankAccountId aggregateId, BankAccountError error) implements CommandReply {
    @Override
    public BankAccountId getAggregateId() {
      return aggregateId;
    }

    @Override
    public boolean isSuccessful() {
      return error == null;
    }

    /**
     * Create a successful reply
     *
     * @param aggregateId Aggregate ID
     * @return Reply
     */
    public static StopReply succeeded(BankAccountId aggregateId) {
      return new StopReply(aggregateId, null);
    }

    /**
     * Create a failed reply
     *
     * @param aggregateId Aggregate ID
     * @param error       Error
     * @return Reply
     */
    public static StopReply failed(BankAccountId aggregateId, BankAccountError error) {
      return new StopReply(aggregateId, error);
    }
  }

  /**
   * Create reply
   *
   * @param aggregateId Aggregate ID
   * @param error       Error
   */
  record CreateReply(BankAccountId aggregateId, BankAccountError error) implements CommandReply {
    @Override
    public BankAccountId getAggregateId() {
      return aggregateId;
    }

    @Override
    public boolean isSuccessful() {
      return error == null;
    }

    /**
     * Create a successful reply
     *
     * @param aggregateId Aggregate ID
     * @return Reply
     */
    public static CreateReply succeeded(BankAccountId aggregateId) {
      return new CreateReply(aggregateId, null);
    }

    /**
     * Create a failed reply
     *
     * @param aggregateId Aggregate ID
     * @param error       Error
     * @return Reply
     */
    public static CreateReply failed(BankAccountId aggregateId, BankAccountError error) {
      return new CreateReply(aggregateId, error);
    }
  }

  /**
   * Deposit cash reply
   *
   * @param aggregateId Aggregate ID
   * @param amount      Amount
   * @param error       Error
   */
  record DepositCashReply(BankAccountId aggregateId, Money amount, BankAccountError error) implements CommandReply {
    @Override
    public BankAccountId getAggregateId() {
      return aggregateId;
    }

    @Override
    public boolean isSuccessful() {
      return error == null;
    }

    /**
     * Create a successful reply
     *
     * @param aggregateId Aggregate ID
     * @param amount      Amount
     * @return Reply
     */
    public static DepositCashReply succeeded(BankAccountId aggregateId, Money amount) {
      return new DepositCashReply(aggregateId, amount, null);
    }

    /**
     * Create a failed reply
     *
     * @param aggregateId Aggregate ID
     * @param error       Error
     * @return Reply
     */
    public static DepositCashReply failed(BankAccountId aggregateId, BankAccountError error) {
      return new DepositCashReply(aggregateId, null, error);
    }
  }

  /**
   * Withdraw cash reply
   *
   * @param aggregateId Aggregate ID
   * @param amount      Amount
   * @param error       Error
   */
  record WithdrawCashReply(BankAccountId aggregateId, Money amount, BankAccountError error) implements CommandReply {
    @Override
    public BankAccountId getAggregateId() {
      return aggregateId;
    }

    @Override
    public boolean isSuccessful() {
      return error == null;
    }

    /**
     * Create a successful reply
     *
     * @param aggregateId Aggregate ID
     * @param amount      Amount
     * @return Reply
     */
    public static WithdrawCashReply succeeded(BankAccountId aggregateId, Money amount) {
      return new WithdrawCashReply(aggregateId, amount, null);
    }

    /**
     * Create a failed reply
     *
     * @param aggregateId Aggregate ID
     * @param error       Error
     * @return Reply
     */
    public static WithdrawCashReply failed(BankAccountId aggregateId, BankAccountError error) {
      return new WithdrawCashReply(aggregateId, null, error);
    }
  }
}
