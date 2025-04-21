# pekko-persistence-effector

[![CI](https://github.com/j5ik2o/pekko-persistence-effector/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/j5ik2o/pekko-persistence-effector/actions/workflows/ci.yml)
[![Renovate](https://img.shields.io/badge/renovate-enabled-brightgreen.svg)](https://renovatebot.com)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Tokei](https://tokei.rs/b1/github/j5ik2o/pekko-persistence-dynamodb)](https://github.com/XAMPPRocky/tokei)

A library for efficient implementation of event sourcing and state transitions with Apache Pekko.

*Read this in other languages: [日本語](README.ja.md)*

## Project Structure

This project is organized into the following modules:

- **library**: Core library code implementing the Persistence Effector pattern
- **example**: Example implementations demonstrating different usage patterns

## Overview

`pekko-persistence-effector` is a library that improves the implementation of event sourcing patterns using Apache Pekko. It eliminates the constraints of traditional Pekko Persistence Typed and enables event sourcing with a more intuitive actor programming style, supporting both Scala and Java DSLs.

### Key Features

- **Traditional Actor Programming Style**: Enables event sourcing while maintaining the usual Behavior-based actor programming style (Scala & Java).
- **Multi-Language Support**: Works with Scala, Java, and Kotlin applications.
- **Single Execution of Domain Logic**: Eliminates the problem of double execution of domain logic in command handlers.
- **High Compatibility with DDD**: Supports seamless integration with domain objects.
- **Incremental Implementation**: Start with in-memory mode during development, then migrate to persistence later.
- **Type Safety**: Type-safe design utilizing Scala 3's type system (Scala DSL).
- **Enhanced Error Handling**: Includes configurable retry mechanisms for persistence operations, improving resilience against transient failures.
  - Timeout-based retry strategies for persistence operations
  - Configurable backoff settings for PersistenceStoreActor restart
  - Clear separation of persistence failures from domain validation errors

## Background: Why This Library is Needed

Traditional Pekko Persistence Typed has the following issues:

1. **Inconsistency with Traditional Actor Programming Style**:
   - Forces you to use EventSourcedBehavior patterns that differ from regular Behavior-based programming.
   - Makes learning curve steeper and implementation more difficult.

2. **Reduced Maintainability with Complex State Transitions**:
   - Command handlers become complex with multiple match/case statements.
   - Cannot split handlers based on state, leading to decreased code readability.

3. **Double Execution of Domain Logic**:
   - Domain logic is executed in both command handlers and event handlers.
   - Command handlers cannot use state updated by domain logic, making integration with domain objects awkward.

This library solves these problems by implementing "Persistent Actor as a child actor of the aggregate actor." The implementation specifically uses Untyped PersistentActor internally (rather than EventSourcedBehavior) to avoid the double execution of domain logic.

## Main Components

### PersistenceEffector

A core trait (Scala) / interface (Java) that provides event persistence functionality.

```scala
// Scala DSL
trait PersistenceEffector[S, E, M] {
  def persistEvent(event: E)(onPersisted: E => Behavior[M]): Behavior[M]
  def persistEvents(events: Seq[E])(onPersisted: Seq[E] => Behavior[M]): Behavior[M]
  def persistSnapshot(snapshot: S)(onPersisted: S => Behavior[M]): Behavior[M]
  // ... includes methods for retry logic
}
```

```java
// Java DSL
public interface PersistenceEffector<State, Event, Message> {
    Behavior<Message> persistEvent(Event event, Function<Event, Behavior<Message>> onPersisted);
    Behavior<Message> persistEvents(List<Event> events, Function<List<Event>, Behavior<Message>> onPersisted);
    Behavior<Message> persistSnapshot(State snapshot, Function<State, Behavior<Message>> onPersisted);
    // ... includes methods for retry logic
}
```

### PersistenceEffectorConfig

A trait (Scala) / class (Java) that defines the configuration for PersistenceEffector.

```scala
// Scala DSL
trait PersistenceEffectorConfig[S, E, M] {
  def persistenceId: String
  def initialState: S
  def applyEvent: (S, E) => S
  def persistenceMode: PersistenceMode
  def stashSize: Int
  def snapshotCriteria: Option[SnapshotCriteria[S, E]]
  def retentionCriteria: Option[RetentionCriteria]
  def backoffConfig: Option[BackoffConfig] // For PersistenceStoreActor restart
  def messageConverter: MessageConverter[S, E, M]
  // ... and other methods
}
```

```java
// Java DSL
public class PersistenceEffectorConfig<State, Event, Message> {
    private final String persistenceId;
    private final State initialState;
    private final BiFunction<State, Event, State> applyEvent;
    private final PersistenceMode persistenceMode;
    private final int stashSize;
    private final Optional<SnapshotCriteria<State, Event>> snapshotCriteria;
    private final Optional<RetentionCriteria> retentionCriteria;
    private final Optional<BackoffConfig> backoffConfig; // For PersistenceStoreActor restart
    private final MessageConverter<State, Event, Message> messageConverter;
    // Constructor and builder...
}
```

## Usage Examples

### BankAccount Example (Scala DSL)

Here's a complete example showing how to implement a bank account aggregate using pekko-persistence-effector:

```scala
// 1. Define domain model, commands, events, and replies using Scala 3 features

// Domain model
final case class BankAccountId(value: String)
final case class Money(amount: BigDecimal)
final case class BankAccount(id: BankAccountId, balance: Money = Money(0)) {
  def deposit(amount: Money): Either[BankAccountError, (BankAccount, BankAccountEvent)] = {
    if (amount.amount <= 0) {
      Left(BankAccountError.InvalidAmount)
    } else {
      Right((copy(balance = Money(balance.amount + amount.amount)), 
             BankAccountEvent.Deposited(id, amount, Instant.now())))
    }
  }
                
  def withdraw(amount: Money): Either[BankAccountError, (BankAccount, BankAccountEvent)] = {
    if (amount.amount <= 0) {
      Left(BankAccountError.InvalidAmount)
    } else if (balance.amount < amount.amount) {
      Left(BankAccountError.InsufficientFunds)
    } else {
      Right((copy(balance = Money(balance.amount - amount.amount)),
             BankAccountEvent.Withdrawn(id, amount, Instant.now())))
    }
  }
}

// Commands
enum BankAccountCommand {
  case Create(id: BankAccountId, replyTo: ActorRef[CreateReply])
  case Deposit(id: BankAccountId, amount: Money, replyTo: ActorRef[DepositReply])
  case Withdraw(id: BankAccountId, amount: Money, replyTo: ActorRef[WithdrawReply])
  case GetBalance(id: BankAccountId, replyTo: ActorRef[GetBalanceReply])
}

// Events
enum BankAccountEvent {
  def id: BankAccountId
  def occurredAt: Instant
  
  case Created(id: BankAccountId, occurredAt: Instant) extends BankAccountEvent
  case Deposited(id: BankAccountId, amount: Money, occurredAt: Instant) extends BankAccountEvent
  case Withdrawn(id: BankAccountId, amount: Money, occurredAt: Instant) extends BankAccountEvent
}

// Replies
enum CreateReply {
  case Succeeded(id: BankAccountId)
  case Failed(id: BankAccountId, error: BankAccountError)
}

enum DepositReply {
  case Succeeded(id: BankAccountId, amount: Money)
  case Failed(id: BankAccountId, error: BankAccountError)
}

// Error types
enum BankAccountError {
  case InvalidAmount
  case InsufficientFunds
  case AlreadyExists
  case NotFound
}

// 2. Define the aggregate actor
object BankAccountAggregate {
  // State definition using enum
  enum State {
    def id: BankAccountId
    
    case NotCreated(id: BankAccountId) extends State
    case Active(id: BankAccountId, account: BankAccount) extends State
    
    // Event application logic
    def applyEvent(event: BankAccountEvent): State = (this, event) match {
      case (NotCreated(id), BankAccountEvent.Created(_, _)) =>
        Active(id, BankAccount(id))
        
      case (active: Active, evt: BankAccountEvent.Deposited) =>
        val newAccount = active.account.copy(
          balance = Money(active.account.balance.amount + evt.amount.amount)
        )
        active.copy(account = newAccount)
        
      case (active: Active, evt: BankAccountEvent.Withdrawn) =>
        val newAccount = active.account.copy(
          balance = Money(active.account.balance.amount - evt.amount.amount)
        )
        active.copy(account = newAccount)
        
      case _ =>
        throw IllegalStateException(s"Invalid state transition: $this -> $event")
    }
  }
  
  // Actor factory
  def apply(id: BankAccountId): Behavior[BankAccountCommand] = {
    Behaviors.setup { context =>
      // Create PersistenceEffector configuration
      val config = PersistenceEffectorConfig[State, BankAccountEvent, BankAccountCommand](
        persistenceId = s"bank-account-${id.value}",
        initialState = State.NotCreated(id),
        applyEvent = (state, event) => state.applyEvent(event),
        persistenceMode = PersistenceMode.Persisted, // Or InMemory for development
        stashSize = 100
      )
      
      // Create PersistenceEffector
      PersistenceEffector.fromConfig(config) {
        case (state: State.NotCreated, effector) => handleNotCreated(state, effector)
        case (state: State.Active, effector) => handleActive(state, effector)
      }
    }
  }
  
  // Handler for NotCreated state
  private def handleNotCreated(
    state: State.NotCreated,
    effector: PersistenceEffector[State, BankAccountEvent, BankAccountCommand]
  ): Behavior[BankAccountCommand] = {
    Behaviors.receiveMessagePartial {
      case cmd: BankAccountCommand.Create =>
        // Create a new account and generate event
        val event = BankAccountEvent.Created(cmd.id, Instant.now())
        
        // Persist the event
        effector.persistEvent(event) { _ =>
          // After successful persistence, reply and change behavior
          cmd.replyTo ! CreateReply.Succeeded(cmd.id)
          handleActive(State.Active(cmd.id, BankAccount(cmd.id)), effector)
        }
    }
  }
  
  // Handler for Active state
  private def handleActive(
    state: State.Active,
    effector: PersistenceEffector[State, BankAccountEvent, BankAccountCommand]
  ): Behavior[BankAccountCommand] = {
    Behaviors.receiveMessagePartial {
      case cmd: BankAccountCommand.Deposit =>
        // Execute domain logic
        state.account.deposit(cmd.amount) match {
          case Left(error) =>
            // Domain validation failed
            cmd.replyTo ! DepositReply.Failed(cmd.id, error)
            Behaviors.same
            
          case Right((newAccount, event)) =>
            // Persist the event
            effector.persistEvent(event) { _ =>
              // After successful persistence, reply and update state
              cmd.replyTo ! DepositReply.Succeeded(cmd.id, cmd.amount)
              handleActive(state.copy(account = newAccount), effector)
            }
        }
        
      case cmd: BankAccountCommand.Withdraw =>
        // Similar implementation to Deposit...
        
      case cmd: BankAccountCommand.GetBalance =>
        // Read-only operation, no persistence needed
        cmd.replyTo ! GetBalanceReply.Success(cmd.id, state.account.balance)
        Behaviors.same
    }
  }
}
```

This example demonstrates:
1. Domain model with validation logic
2. Command, event, and reply message definitions
3. State definition with event application logic
4. PersistenceEffector configuration and creation
5. State-specific message handlers
6. Error handling for both domain validation and persistence failures

*(See Java DSL examples in the [example/src/main/java/com/github/j5ik2o/pekko/persistence/effector/example/javaimpl/persistenceEffector/oop](example/src/main/java/com/github/j5ik2o/pekko/persistence/effector/example/javaimpl/persistenceEffector/oop) directory)*

## Example Code Files

For more detailed implementation examples, see the following files:

**Scala DSL:**
- Aggregate: [BankAccountAggregate.scala](example/src/main/scala/com/github/j5ik2o/pekko/persistence/effector/example/scalaimpl/persistenceEffector/fp/BankAccountAggregate.scala)
- Domain Model: [BankAccount.scala](example/src/main/scala/com/github/j5ik2o/pekko/persistence/effector/example/scalaimpl/persistenceEffector/BankAccount.scala)
- Commands: [BankAccountCommand.scala](example/src/main/scala/com/github/j5ik2o/pekko/persistence/effector/example/scalaimpl/BankAccountCommand.scala)
- Events: [BankAccountEvent.scala](example/src/main/scala/com/github/j5ik2o/pekko/persistence/effector/example/scalaimpl/BankAccountEvent.scala)

**Java DSL:**
- Aggregate: [BankAccountAggregate.java](example/src/main/java/com/github/j5ik2o/pekko/persistence/effector/example/javaimpl/persistenceEffector/oop/BankAccountAggregate.java)
- Domain Model: [BankAccount.java](example/src/main/java/com/github/j5ik2o/pekko/persistence/effector/example/javaimpl/persistenceEffector/oop/BankAccount.java)
- Commands: [BankAccountCommand.java](example/src/main/java/com/github/j5ik2o/pekko/persistence/effector/example/javaimpl/BankAccountCommand.java)
- Events: [BankAccountEvent.java](example/src/main/java/com/github/j5ik2o/pekko/persistence/effector/example/javaimpl/BankAccountEvent.java)

## Implementation Approaches Comparison

The `example` directory contains different implementation approaches for the bank account aggregate. These approaches are compared in the following table:

| Criteria | defaultStyle<br>nonPersistence<br>fp | defaultStyle<br>nonPersistence<br>oop | defaultStyle<br>persistence<br>fp | persistenceEffector<br>fp | persistenceEffector<br>oop |
|---------|:-----:|:-----:|:-----:|:-----:|:-----:|
| Code Conciseness | ★★★★★ | ★★★☆☆ | ★★★☆☆ | ★★★★★ | ★★★☆☆ |
| Maintainability | ★★★★☆ | ★★★☆☆ | ★★★☆☆ | ★★★★☆ | ★★★☆☆ |
| Extensibility | ★★★★☆ | ★★★☆☆ | ★★★☆☆ | ★★★★☆ | ★★★☆☆ |
| Testability | ★★★★★ | ★★★☆☆ | ★★★☆☆ | ★★★★☆ | ★★★★☆ |
| Performance | ★★★★★ | ★★★★★ | ★★★☆☆ | ★★★★☆ | ★★★★☆ |
| Persistence Ease | N/A | N/A | ★★★★☆ | ★★★★★ | ★★★★★ |
| Configuration Flexibility | ★★☆☆☆ | ★★☆☆☆ | ★★★☆☆ | ★★★★★ | ★★★★★ |
| Error Handling | ★★★☆☆ | ★★★☆☆ | ★★★★☆ | ★★★★☆ | ★★★★☆ |
| Concurrency Safety | ★★★☆☆ | ★★★☆☆ | ★★★★☆ | ★★★★★ | ★★★★★ |
| Learning Curve | ★★★★★ | ★★★★☆ | ★★☆☆☆ | ★★★★★ | ★★★★☆ |

**Overall Assessment**:
- **Most concise and easy to learn**: defaultStyle/nonPersistence/fp
- **Highest maintainability**: persistenceEffector/fp
- **Highest extensibility**: persistenceEffector/fp and persistenceEffector/oop
- **Most comprehensive persistence features**: persistenceEffector/fp and persistenceEffector/oop
- **Highest performance**: defaultStyle/nonPersistence/fp and defaultStyle/nonPersistence/oop
- **Most balanced**: persistenceEffector/fp

For more details, see [implementation-comparison.md](implementation-comparison.md).

## When to Use This Library

This library is particularly well-suited for:

- When you want to implement incrementally, starting without persistence and later adding it.
- When dealing with complex state transitions that would be difficult to maintain with traditional Pekko Persistence Typed.
- When implementing DDD-oriented designs where you want to separate domain logic from actors.
- When you need a more natural actor programming style for event sourcing applications (Scala or Java).
- When resilience against temporary persistence failures is required.

## Installation

Note: This library does not depend on `pekko-persistence-typed`. You can use this library even without adding `pekko-persistence-typed` as a dependency.

### SBT

Add the following to your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "io.github.j5ik2o" % "pekko-persistence-effector_3" % "<latest_version>"
)
```

### Gradle

Add the following to your `build.gradle`:

```groovy
dependencies {
  implementation 'io.github.j5ik2o:pekko-persistence-effector_3:<latest_version>'
}
```

For Kotlin DSL (`build.gradle.kts`):

```kotlin
dependencies {
  implementation("io.github.j5ik2o:pekko-persistence-effector_3:<latest_version>")
}
```

### Maven

Add the following to your `pom.xml`:

```xml
<dependency>
  <groupId>io.github.j5ik2o</groupId>
  <artifactId>pekko-persistence-effector_3</artifactId>
  <version>LATEST</version>
</dependency>
```

## License

This library is licensed under the Apache License 2.0.
