package com.github.j5ik2o.pekko.persistence.effector.example.javaimpl.standard;

import com.github.j5ik2o.pekko.persistence.effector.example.javaimpl.*;
import java.time.Instant;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.apache.pekko.persistence.typed.javadsl.CommandHandlerWithReply;
import org.apache.pekko.persistence.typed.javadsl.EventHandler;
import org.apache.pekko.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies;
import org.apache.pekko.persistence.typed.javadsl.ReplyEffect;

/** Bank Account Aggregate */
public class BankAccountAggregate
    extends EventSourcedBehaviorWithEnforcedReplies<
        BankAccountCommand, BankAccountEvent, BankAccountAggregateState> {
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
    return Behaviors.setup(ctx -> new BankAccountAggregate(ctx, aggregateId));
  }

  private final ActorContext<BankAccountCommand> ctx;
  private final BankAccountId aggregateId;

  /**
   * Constructor
   *
   * @param ctx Actor context
   * @param aggregateId Aggregate ID
   */
  public BankAccountAggregate(ActorContext<BankAccountCommand> ctx, BankAccountId aggregateId) {
    super(PersistenceId.ofUniqueId(actorName(aggregateId)));
    this.ctx = ctx;
    this.aggregateId = aggregateId;
  }

  @Override
  public BankAccountAggregateState emptyState() {
    return new BankAccountAggregateState.NotCreated(aggregateId);
  }

  @Override
  public CommandHandlerWithReply<BankAccountCommand, BankAccountEvent, BankAccountAggregateState>
      commandHandler() {
    var builder = newCommandHandlerWithReplyBuilder();

    builder
        .forStateType(BankAccountAggregateState.NotCreated.class)
        .onCommand(BankAccountCommand.Create.class, this::handleCreate);

    builder
        .forStateType(BankAccountAggregateState.Created.class)
        .onCommand(BankAccountCommand.GetBalance.class, this::handleGetBalance)
        .onCommand(BankAccountCommand.DepositCash.class, this::handleDepositCash)
        .onCommand(BankAccountCommand.WithdrawCash.class, this::handleWithdrawCash)
        .onCommand(BankAccountCommand.Stop.class, this::handleStop);

    return builder.build();
  }

  private ReplyEffect<BankAccountEvent, BankAccountAggregateState> handleCreate(
      BankAccountAggregateState.NotCreated state, BankAccountCommand.Create cmd) {
    ctx.getLog().debug("Received Create command: {}", cmd);
    var event =
        new BankAccountEvent.Created(
            cmd.getAggregateId(), Money.yens(100000), Money.zero(Money.JPY), Instant.now());
    ctx.getLog().debug("Created BankAccount persisting event: {}", event);

    // Persist event
    return Effect()
        .persist(event)
        .thenReply(cmd.replyTo(), s -> CommandReply.CreateReply.succeeded(cmd.getAggregateId()));
  }

  private ReplyEffect<BankAccountEvent, BankAccountAggregateState> handleGetBalance(
      BankAccountAggregateState.Created state, BankAccountCommand.GetBalance cmd) {
    ctx.getLog().debug("Received GetBalance command: {}", cmd);
    var balance = state.bankAccount().getBalance();
    ctx.getLog().debug("Current balance for {}: {}", cmd.getAggregateId(), balance);
    return Effect()
        .none()
        .thenReply(
            cmd.replyTo(),
            s -> CommandReply.GetBalanceReply.succeeded(cmd.getAggregateId(), balance));
  }

  private ReplyEffect<BankAccountEvent, BankAccountAggregateState> handleDepositCash(
      BankAccountAggregateState.Created state, BankAccountCommand.DepositCash cmd) {
    ctx.getLog().debug("Received DepositCash command: {}, amount: {}", cmd, cmd.amount());
    var result = state.bankAccount().canAdd(cmd.amount());
    if (!result) {
      var error =
          CommandReply.DepositCashReply.failed(
              cmd.getAggregateId(), BankAccountError.LIMIT_OVER_ERROR);
      return Effect().none().thenReply(cmd.replyTo(), s -> error);
    } else {
      var event =
          new BankAccountEvent.CashDeposited(cmd.getAggregateId(), cmd.amount(), Instant.now());
      ctx.getLog().debug("Cash deposited, persisting event: {}", event);
      return Effect()
          .persist(event)
          .thenReply(
              cmd.replyTo(),
              s -> CommandReply.DepositCashReply.succeeded(cmd.getAggregateId(), cmd.amount()));
    }
  }

  private ReplyEffect<BankAccountEvent, BankAccountAggregateState> handleWithdrawCash(
      BankAccountAggregateState.Created state, BankAccountCommand.WithdrawCash cmd) {
    ctx.getLog().debug("Received WithdrawCash command: {}, amount: {}", cmd, cmd.amount());
    var result = state.bankAccount().canSubtract(cmd.amount());
    if (!result) {
      var error =
          CommandReply.WithdrawCashReply.failed(
              cmd.getAggregateId(), BankAccountError.INSUFFICIENT_FUNDS_ERROR);
      return Effect().none().thenReply(cmd.replyTo(), s -> error);
    } else {
      var event =
          new BankAccountEvent.CashWithdrew(cmd.getAggregateId(), cmd.amount(), Instant.now());
      ctx.getLog().debug("Cash withdrawn, persisting event: {}", event);
      return Effect()
          .persist(event)
          .thenReply(
              cmd.replyTo(),
              s -> CommandReply.WithdrawCashReply.succeeded(cmd.getAggregateId(), cmd.amount()));
    }
  }

  private ReplyEffect<BankAccountEvent, BankAccountAggregateState> handleStop(
      BankAccountAggregateState.Created state, BankAccountCommand.Stop cmd) {
    ctx.getLog().debug("Received Stop command: {}", cmd);
    return Effect()
        .none()
        .thenStop()
        .thenReply(cmd.replyTo(), s -> CommandReply.StopReply.succeeded(cmd.getAggregateId()));
  }

  @Override
  public EventHandler<BankAccountAggregateState, BankAccountEvent> eventHandler() {
    var builder = newEventHandlerBuilder();
    builder
        .forStateType(BankAccountAggregateState.NotCreated.class)
        .onEvent(BankAccountEvent.Created.class, this::applyCreated);

    builder
        .forStateType(BankAccountAggregateState.Created.class)
        .onEvent(BankAccountEvent.CashDeposited.class, this::applyCashDeposited)
        .onEvent(BankAccountEvent.CashWithdrew.class, this::applyCashWithdrew);

    return builder.build();
  }

  private BankAccountAggregateState applyCreated(
      BankAccountAggregateState.NotCreated state, BankAccountEvent.Created event) {
    ctx.getLog().debug("Applying Created event: {}", event);
    return new BankAccountAggregateState.Created(
        state.getAggregateId(),
        BankAccount.create(state.getAggregateId(), event.limit(), event.balance()));
  }

  private BankAccountAggregateState applyCashDeposited(
      BankAccountAggregateState.Created state, BankAccountEvent.CashDeposited event) {
    ctx.getLog().debug("Applying CashDeposited event: {}", event);
    var result = state.bankAccount().add(event.amount());
    if (result.isLeft()) {
      throw new IllegalStateException("Failed to apply event: " + result.getLeft());
    } else {
      return new BankAccountAggregateState.Created(state.getAggregateId(), result.getRight());
    }
  }

  private BankAccountAggregateState applyCashWithdrew(
      BankAccountAggregateState.Created state, BankAccountEvent.CashWithdrew event) {
    ctx.getLog().debug("Applying CashWithdrew event: {}", event);
    var result = state.bankAccount().subtract(event.amount());
    if (result.isLeft()) {
      throw new IllegalStateException("Failed to apply event: " + result.getLeft());
    } else {
      return new BankAccountAggregateState.Created(state.getAggregateId(), result.getRight());
    }
  }
}
