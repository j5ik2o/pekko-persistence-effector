package com.github.j5ik2o.pekko.persistence.effector.example.javaimpl.persistenceEffector.oop;

import com.github.j5ik2o.pekko.persistence.effector.example.javaimpl.*;
import com.github.j5ik2o.pekko.persistence.effector.javadsl.*;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

/** Bank Account Aggregate */
public class BankAccountAggregate {
  /**
   * Get actor name
   *
   * @param aggregateId Aggregate ID
   * @return Actor name
   */
  public static String actorName(BankAccountId aggregateId) {
    return aggregateId.getAggregateTypeName() + "-" + aggregateId.asString();
  }

  /**
   * Create a bank account actor
   *
   * @param aggregateId Aggregate ID
   * @return Actor behavior
   */
  public static Behavior<BankAccountCommand> create(BankAccountId aggregateId) {
    return create(aggregateId, PersistenceMode.PERSISTENCE);
  }

  /**
   * Create a bank account actor
   *
   * @param aggregateId Aggregate ID
   * @param persistenceMode Persistence mode
   * @return Actor behavior
   */
  public static Behavior<BankAccountCommand> create(
      BankAccountId aggregateId, PersistenceMode persistenceMode) {
    var config =
        PersistenceEffectorConfig
            .<BankAccountAggregateState, BankAccountEvent, BankAccountCommand>create(
                PersistenceId.ofUniqueId(actorName(aggregateId)),
                new BankAccountAggregateState.NotCreated(aggregateId),
                BankAccountAggregateState::applyEvent)
            .withPersistenceMode(persistenceMode)
            .withSnapshotCriteria(SnapshotCriteria.every(2))
            .withRetentionCriteria(RetentionCriteria.ofSnapshotEvery(2));

    return Behaviors.setup(
        ctx ->
            PersistenceEffector.fromConfig(
                config,
                (initialState, effector) ->
                    switch (initialState) {
                      case BankAccountAggregateState.NotCreated notCreated ->
                          handleNotCreated(notCreated, effector);
                      case BankAccountAggregateState.Created created ->
                          handleCreated(created, effector);
                      default ->
                          throw new IllegalStateException("Unexpected state: " + initialState);
                    }));
  }

  private static Behavior<BankAccountCommand> handleNotCreated(
      BankAccountAggregateState.NotCreated state,
      PersistenceEffector<BankAccountAggregateState, BankAccountEvent, BankAccountCommand>
          effector) {
    return Behaviors.receiveMessage(
        command ->
            switch (command) {
              case BankAccountCommand.Create create -> {
                var result = BankAccount.create(create.getAggregateId());
                yield effector.persistEvent(
                    result.getEvent(),
                    newState -> {
                      create
                          .replyTo()
                          .tell(CommandReply.CreateReply.succeeded(create.getAggregateId()));
                      var created =
                          new BankAccountAggregateState.Created(
                              state.getAggregateId(), result.getState());
                      return handleCreated(created, effector);
                    });
              }
              default -> throw new IllegalStateException("Unexpected command: " + command);
            });
  }

  private static Behavior<BankAccountCommand> handleCreated(
      BankAccountAggregateState.Created state,
      PersistenceEffector<BankAccountAggregateState, BankAccountEvent, BankAccountCommand>
          effector) {
    return effector.persistSnapshot(
        state,
        newState ->
            Behaviors.receiveMessage(
                command ->
                    switch (command) {
                      case BankAccountCommand.Stop stop -> {
                        stop.replyTo()
                            .tell(CommandReply.StopReply.succeeded(stop.getAggregateId()));
                        yield Behaviors.<BankAccountCommand>stopped();
                      }
                      case BankAccountCommand.GetBalance getBalance -> {
                        getBalance
                            .replyTo()
                            .tell(
                                CommandReply.GetBalanceReply.succeeded(
                                    getBalance.getAggregateId(), state.bankAccount().getBalance()));
                        yield Behaviors.<BankAccountCommand>same();
                      }
                      case BankAccountCommand.DepositCash depositCash -> {
                        var result = state.bankAccount().add(depositCash.amount());
                        if (result.isLeft()) {
                          depositCash
                              .replyTo()
                              .tell(
                                  CommandReply.DepositCashReply.failed(
                                      depositCash.getAggregateId(), result.getLeft()));
                          yield Behaviors.<BankAccountCommand>same();
                        } else {
                          var newBankAccount = result.getRight();
                          yield effector.persistEvent(
                              newBankAccount.getEvent(),
                              newState2 -> {
                                depositCash
                                    .replyTo()
                                    .tell(
                                        CommandReply.DepositCashReply.succeeded(
                                            depositCash.getAggregateId(), depositCash.amount()));
                                var created =
                                    new BankAccountAggregateState.Created(
                                        state.getAggregateId(), newBankAccount.getState());
                                return handleCreated(created, effector);
                              });
                        }
                      }
                      case BankAccountCommand.WithdrawCash withdrawCash -> {
                        var result = state.bankAccount().subtract(withdrawCash.amount());
                        if (result.isLeft()) {
                          withdrawCash
                              .replyTo()
                              .tell(
                                  CommandReply.WithdrawCashReply.failed(
                                      withdrawCash.getAggregateId(), result.getLeft()));
                          yield Behaviors.<BankAccountCommand>same();
                        } else {
                          var newBankAccount = result.getRight();
                          yield effector.persistEvent(
                              newBankAccount.getEvent(),
                              newState2 -> {
                                withdrawCash
                                    .replyTo()
                                    .tell(
                                        CommandReply.WithdrawCashReply.succeeded(
                                            withdrawCash.getAggregateId(), withdrawCash.amount()));
                                var created =
                                    new BankAccountAggregateState.Created(
                                        state.getAggregateId(), newBankAccount.getState());
                                return handleCreated(created, effector);
                              });
                        }
                      }
                      default -> throw new IllegalStateException("Unexpected command: " + command);
                    }));
  }
}
