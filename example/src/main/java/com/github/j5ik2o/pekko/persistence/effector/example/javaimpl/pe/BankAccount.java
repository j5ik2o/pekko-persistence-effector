package com.github.j5ik2o.pekko.persistence.effector.example.javaimpl.pe;

import com.github.j5ik2o.pekko.persistence.effector.example.javaimpl.*;

import java.io.Serializable;
import java.time.Instant;

/**
 * Class representing a bank account
 */
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
     * Create a bank account with the specified bank account ID
     *
     * @param bankAccountId Bank account ID
     * @return Bank account
     */
    public static BankAccount apply(BankAccountId bankAccountId) {
        return new BankAccount(bankAccountId, Money.yens(100000), Money.zero(Money.JPY));
    }

    /**
     * Create a bank account
     *
     * @param bankAccountId Bank account ID
     * @param limit Limit amount
     * @param balance Balance
     * @return Result
     */
    public static Result<BankAccount, BankAccountEvent> create(
            BankAccountId bankAccountId,
            Money limit,
            Money balance) {
        return new Result<>(
                new BankAccount(bankAccountId, limit, balance),
                new BankAccountEvent.Created(bankAccountId, limit, balance, Instant.now())
        );
    }

    /**
     * Create a bank account with the specified bank account ID
     *
     * @param bankAccountId Bank account ID
     * @return Result
     */
    public static Result<BankAccount, BankAccountEvent> create(BankAccountId bankAccountId) {
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
     * Deposit cash
     *
     * @param amount Amount
     * @return Result
     */
    public Either<BankAccountError, Result<BankAccount, BankAccountEvent>> add(Money amount) {
        if (limit.isLessThan(balance.plus(amount))) {
            return Either.left(BankAccountError.LIMIT_OVER_ERROR);
        } else {
            return Either.right(
                    new Result<>(
                            new BankAccount(bankAccountId, limit, balance.plus(amount)),
                            new BankAccountEvent.CashDeposited(bankAccountId, amount, Instant.now())
                    )
            );
        }
    }

    /**
     * Withdraw cash
     *
     * @param amount Amount
     * @return Result
     */
    public Either<BankAccountError, Result<BankAccount, BankAccountEvent>> subtract(Money amount) {
        if (Money.zero(Money.JPY).isGreaterThan(balance.minus(amount))) {
            return Either.left(BankAccountError.INSUFFICIENT_FUNDS_ERROR);
        } else {
            return Either.right(
                    new Result<>(
                            new BankAccount(bankAccountId, limit, balance.minus(amount)),
                            new BankAccountEvent.CashWithdrew(bankAccountId, amount, Instant.now())
                    )
            );
        }
    }
}
