package com.github.j5ik2o.pekko.persistence.effector.example.javaimpl.defaultStyle.nonPersistence.oop;

import com.github.j5ik2o.pekko.persistence.effector.example.javaimpl.BankAccountCommand;
import com.github.j5ik2o.pekko.persistence.effector.example.javaimpl.BankAccountId;
import com.github.j5ik2o.pekko.persistence.effector.example.javaimpl.CommandReply;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

public class BankAccountAggregate extends AbstractBehavior<BankAccountCommand> {
  private final BankAccountAggregateState state;

  public static String actorName(BankAccountId aggregateId) {
    return aggregateId.getAggregateTypeName() + "-" + aggregateId.asString();
  }

  public static Behavior<BankAccountCommand> create(BankAccountId aggregateId) {
    return Behaviors.setup(
        ctx ->
            new BankAccountAggregate(ctx, new BankAccountAggregateState.NotCreated(aggregateId)));
  }

  private BankAccountAggregate(
      ActorContext<BankAccountCommand> context, BankAccountAggregateState state) {
    super(context);
    this.state = state;
  }

  @Override
  public Receive<BankAccountCommand> createReceive() {
    return newReceiveBuilder()
        .onMessage(BankAccountCommand.Create.class, this::onCreate)
        .onMessage(BankAccountCommand.DepositCash.class, this::onDepositCash)
        .onMessage(BankAccountCommand.WithdrawCash.class, this::onWithdrawCash)
        .build();
  }

  private Behavior<BankAccountCommand> onWithdrawCash(
      BankAccountCommand.WithdrawCash withdrawCash) {
    if (state
        instanceof
        BankAccountAggregateState.Created(BankAccountId aggregateId, BankAccount bankAccount)) {
      var result = bankAccount.subtract(withdrawCash.amount());
      if (result.isRight()) {
        withdrawCash
            .replyTo()
            .tell(CommandReply.WithdrawCashReply.succeeded(aggregateId, withdrawCash.amount()));
        BankAccountAggregateState newState =
            new BankAccountAggregateState.Created(aggregateId, bankAccount);
        return new BankAccountAggregate(getContext(), newState);
      } else {
        withdrawCash
            .replyTo()
            .tell(CommandReply.WithdrawCashReply.failed(aggregateId, result.getLeft()));
        return this;
      }
    }
    return Behaviors.unhandled();
  }

  private Behavior<BankAccountCommand> onDepositCash(BankAccountCommand.DepositCash depositCash) {
    if (state
        instanceof
        BankAccountAggregateState.Created(BankAccountId aggregateId, BankAccount bankAccount)) {
      var result = bankAccount.add(depositCash.amount());
      if (result.isRight()) {
        depositCash
            .replyTo()
            .tell(CommandReply.DepositCashReply.succeeded(aggregateId, depositCash.amount()));
        BankAccountAggregateState newState =
            new BankAccountAggregateState.Created(aggregateId, bankAccount);
        return new BankAccountAggregate(getContext(), newState);
      } else {
        depositCash
            .replyTo()
            .tell(CommandReply.DepositCashReply.failed(aggregateId, result.getLeft()));
        return this;
      }
    }
    return Behaviors.unhandled();
  }

  private Behavior<BankAccountCommand> onCreate(BankAccountCommand.Create createCommand) {
    if (state instanceof BankAccountAggregateState.NotCreated) {
      BankAccount bankAccount = BankAccount.create(createCommand.aggregateId());
      createCommand.replyTo().tell(CommandReply.CreateReply.succeeded(createCommand.aggregateId()));
      BankAccountAggregateState newState =
          new BankAccountAggregateState.Created(createCommand.aggregateId(), bankAccount);
      return new BankAccountAggregate(getContext(), newState);
    }
    return Behaviors.unhandled();
  }
}
