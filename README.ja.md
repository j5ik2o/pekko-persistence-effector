# pekko-persistence-effector

[![CI](https://github.com/j5ik2o/pekko-persistence-effector/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/j5ik2o/pekko-persistence-effector/actions/workflows/ci.yml)
[![Renovate](https://img.shields.io/badge/renovate-enabled-brightgreen.svg)](https://renovatebot.com)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Tokei](https://tokei.rs/b1/github/j5ik2o/pekko-persistence-dynamodb)](https://github.com/XAMPPRocky/tokei)

イベントソーシングと状態遷移を Apache Pekko で効率的に実装するためのライブラリです。

*他の言語で読む: [English](README.md)*

## プロジェクト構造

このプロジェクトは以下のモジュールで構成されています：

- **library**: Persistence Effectorパターンを実装するコアライブラリコード
- **example**: 異なる使用パターンを示すサンプル実装

## 概要

`pekko-persistence-effector` は Apache Pekko を使用したイベントソーシングパターンの実装を改善するライブラリです。従来の Pekko Persistence Typed の制約を解消し、より直感的なアクタープログラミングスタイルでイベントソーシングを実現します。Scala と Java の両方の DSL をサポートしています。

### 主な特徴

- **従来のアクタープログラミングスタイル**: 通常の Behavior ベースのアクタープログラミングスタイルを維持しながらイベントソーシングが可能 (Scala & Java)。
- **マルチ言語サポート**: Scala、Java、Kotlinアプリケーションで利用可能。
- **ドメインロジックの単一実行**: コマンドハンドラでのドメインロジックの二重実行問題を解消。
- **DDDとの高い親和性**: ドメインオブジェクトとのシームレスな統合をサポート。
- **段階的な実装**: 最初はインメモリモードで開発し、後から永続化対応へ移行可能。
- **型安全**: Scala 3 の型システムを活用した型安全な設計 (Scala DSL)。
- **強化されたエラーハンドリング**: 永続化操作のための設定可能なリトライ機構を含み、一時的な障害に対する回復力を向上。
  - タイムアウトベースの永続化操作リトライ戦略
  - PersistenceStoreActorの再起動のための設定可能なバックオフ設定
  - 永続化失敗とドメインバリデーションエラーの明確な分離

## 背景: なぜこのライブラリが必要か

従来の Pekko Persistence Typed には以下の問題がありました：

1. **従来のアクタープログラミングスタイルとの不一致**:
   - EventSourcedBehavior のパターンを使用することを強制され、通常の Behavior ベースのプログラミングと異なる。
   - 学習曲線が急で実装が難しくなる。

2. **複雑な状態遷移の保守性低下**:
   - コマンドハンドラが多数の match/case ステートメントで複雑になる。
   - 状態に基づいてハンドラを分割できないため、コードの可読性が低下する。

3. **ドメインロジックの二重実行**:
   - ドメインロジックがコマンドハンドラとイベントハンドラの両方で実行される。
   - コマンドハンドラはドメインロジックによって更新された状態を使用できないため、ドメインオブジェクトとの統合がぎこちない。

このライブラリは「永続化アクターを集約アクターの子アクターとして実装する」というアプローチにより、これらの問題を解決します。具体的には、ドメインロジックの二重実行を避けるため、内部的にUntyped PersistentActorを使用しています（EventSourcedBehaviorではない）。

## 主要コンポーネント

### PersistenceEffector

イベントの永続化機能を提供するコアトレイト (Scala) / インターフェース (Java) です。

```scala
// Scala DSL
trait PersistenceEffector[S, E, M] {
  def persistEvent(event: E)(onPersisted: E => Behavior[M]): Behavior[M]
  def persistEvents(events: Seq[E])(onPersisted: Seq[E] => Behavior[M]): Behavior[M]
  def persistSnapshot(snapshot: S)(onPersisted: S => Behavior[M]): Behavior[M]
  // ... リトライロジックを含むメソッド
}
```

```java
// Java DSL
public interface PersistenceEffector<State, Event, Message> {
    Behavior<Message> persistEvent(Event event, Function<Event, Behavior<Message>> onPersisted);
    Behavior<Message> persistEvents(List<Event> events, Function<List<Event>, Behavior<Message>> onPersisted);
    Behavior<Message> persistSnapshot(State snapshot, Function<State, Behavior<Message>> onPersisted);
    // ... リトライロジックを含むメソッド
}
```

### PersistenceEffectorConfig

PersistenceEffector の設定を定義するトレイト (Scala) / クラス (Java) です。

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
  def backoffConfig: Option[BackoffConfig] // PersistenceStoreActor 再起動用
  def messageConverter: MessageConverter[S, E, M]
  // ... その他のメソッド
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
    private final Optional<BackoffConfig> backoffConfig; // PersistenceStoreActor 再起動用
    private final MessageConverter<State, Event, Message> messageConverter;
    // コンストラクタとビルダー...
}
```

## 使用例

### 銀行口座の例 (Scala DSL)

pekko-persistence-effectorを使用した銀行口座集約の完全な実装例を以下に示します：

```scala
// 1. Scala 3の機能を活用したドメインモデル、コマンド、イベント、応答の定義

// ドメインモデル
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

// コマンド
enum BankAccountCommand {
  case Create(id: BankAccountId, replyTo: ActorRef[CreateReply])
  case Deposit(id: BankAccountId, amount: Money, replyTo: ActorRef[DepositReply])
  case Withdraw(id: BankAccountId, amount: Money, replyTo: ActorRef[WithdrawReply])
  case GetBalance(id: BankAccountId, replyTo: ActorRef[GetBalanceReply])
}

// イベント
enum BankAccountEvent {
  def id: BankAccountId
  def occurredAt: Instant
  
  case Created(id: BankAccountId, occurredAt: Instant) extends BankAccountEvent
  case Deposited(id: BankAccountId, amount: Money, occurredAt: Instant) extends BankAccountEvent
  case Withdrawn(id: BankAccountId, amount: Money, occurredAt: Instant) extends BankAccountEvent
}

// 応答メッセージ
enum CreateReply {
  case Succeeded(id: BankAccountId)
  case Failed(id: BankAccountId, error: BankAccountError)
}

enum DepositReply {
  case Succeeded(id: BankAccountId, amount: Money)
  case Failed(id: BankAccountId, error: BankAccountError)
}

// エラー型
enum BankAccountError {
  case InvalidAmount
  case InsufficientFunds
  case AlreadyExists
  case NotFound
}

// 2. 集約アクターの定義
object BankAccountAggregate {
  // enumを使用した状態定義
  enum State {
    def id: BankAccountId
    
    case NotCreated(id: BankAccountId) extends State
    case Active(id: BankAccountId, account: BankAccount) extends State
    
    // イベント適用ロジック
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
  
  // アクターファクトリ
  def apply(id: BankAccountId): Behavior[BankAccountCommand] = {
    Behaviors.setup { context =>
      // PersistenceEffector設定の作成
      val config = PersistenceEffectorConfig[State, BankAccountEvent, BankAccountCommand](
        persistenceId = s"bank-account-${id.value}",
        initialState = State.NotCreated(id),
        applyEvent = (state, event) => state.applyEvent(event),
        persistenceMode = PersistenceMode.Persisted, // 開発時はInMemoryも選択可能
        stashSize = 100
      )
      
      // PersistenceEffectorの作成
      PersistenceEffector.fromConfig(config) {
        case (state: State.NotCreated, effector) => handleNotCreated(state, effector)
        case (state: State.Active, effector) => handleActive(state, effector)
      }
    }
  }
  
  // NotCreated状態のハンドラ
  private def handleNotCreated(
    state: State.NotCreated,
    effector: PersistenceEffector[State, BankAccountEvent, BankAccountCommand]
  ): Behavior[BankAccountCommand] = {
    Behaviors.receiveMessagePartial {
      case cmd: BankAccountCommand.Create =>
        // 新しいアカウントを作成しイベントを生成
        val event = BankAccountEvent.Created(cmd.id, Instant.now())
        
        // イベントを永続化
        effector.persistEvent(event) { _ =>
          // 永続化成功後、応答を返し振る舞いを変更
          cmd.replyTo ! CreateReply.Succeeded(cmd.id)
          handleActive(State.Active(cmd.id, BankAccount(cmd.id)), effector)
        }
    }
  }
  
  // Active状態のハンドラ
  private def handleActive(
    state: State.Active,
    effector: PersistenceEffector[State, BankAccountEvent, BankAccountCommand]
  ): Behavior[BankAccountCommand] = {
    Behaviors.receiveMessagePartial {
      case cmd: BankAccountCommand.Deposit =>
        // ドメインロジックを実行
        state.account.deposit(cmd.amount) match {
          case Left(error) =>
            // ドメインバリデーション失敗
            cmd.replyTo ! DepositReply.Failed(cmd.id, error)
            Behaviors.same
            
          case Right((newAccount, event)) =>
            // イベントを永続化
            effector.persistEvent(event) { _ =>
              // 永続化成功後、応答を返し状態を更新
              cmd.replyTo ! DepositReply.Succeeded(cmd.id, cmd.amount)
              handleActive(state.copy(account = newAccount), effector)
            }
        }
        
      case cmd: BankAccountCommand.Withdraw =>
        // Depositと同様の実装...
        
      case cmd: BankAccountCommand.GetBalance =>
        // 読み取り専用操作、永続化不要
        cmd.replyTo ! GetBalanceReply.Success(cmd.id, state.account.balance)
        Behaviors.same
    }
  }
}
```

この例では以下を示しています：
1. バリデーションロジックを持つドメインモデル
2. コマンド、イベント、応答メッセージの定義
3. イベント適用ロジックを持つ状態定義
4. PersistenceEffectorの設定と作成
5. 状態固有のメッセージハンドラ
6. ドメインバリデーションと永続化失敗の両方に対するエラー処理

*(Java DSL の例は [example/src/main/java/com/github/j5ik2o/pekko/persistence/effector/example/javaimpl/persistenceEffector/oop](example/src/main/java/com/github/j5ik2o/pekko/persistence/effector/example/javaimpl/persistenceEffector/oop) ディレクトリを参照してください)*

## サンプルコードファイル

より詳細な実装例については、以下のファイルを参照してください：

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

## 実装方法の比較

`example`ディレクトリには、銀行口座アグリゲートの異なる実装方法が含まれています。これらの実装方法を比較した表は以下の通りです：

| 評価基準 | defaultStyle<br>nonPersistence<br>fp | defaultStyle<br>nonPersistence<br>oop | defaultStyle<br>persistence<br>fp | persistenceEffector<br>fp | persistenceEffector<br>oop |
|---------|:-----:|:-----:|:-----:|:-----:|:-----:|
| コードの簡潔さ | ★★★★★ | ★★★☆☆ | ★★★☆☆ | ★★★★★ | ★★★☆☆ |
| 保守性 | ★★★★☆ | ★★★☆☆ | ★★★☆☆ | ★★★★☆ | ★★★☆☆ |
| 拡張性 | ★★★★☆ | ★★★☆☆ | ★★★☆☆ | ★★★★☆ | ★★★☆☆ |
| テスト容易性 | ★★★★★ | ★★★☆☆ | ★★★☆☆ | ★★★★☆ | ★★★★☆ |
| パフォーマンス | ★★★★★ | ★★★★★ | ★★★☆☆ | ★★★★☆ | ★★★★☆ |
| 永続化の容易さ | N/A | N/A | ★★★★☆ | ★★★★★ | ★★★★★ |
| 設定の柔軟性 | ★★☆☆☆ | ★★☆☆☆ | ★★★☆☆ | ★★★★★ | ★★★★★ |
| エラーハンドリング | ★★★☆☆ | ★★★☆☆ | ★★★★☆ | ★★★★☆ | ★★★★☆ |
| 並行処理の安全性 | ★★★☆☆ | ★★★☆☆ | ★★★★☆ | ★★★★★ | ★★★★★ |
| 学習の容易さ | ★★★★★ | ★★★★☆ | ★★☆☆☆ | ★★★★★ | ★★★★☆ |

**総合評価**:
- **最も簡潔で学習が容易**: defaultStyle/nonPersistence/fp
- **最も保守性が高い**: persistenceEffector/fp
- **最も拡張性が高い**: persistenceEffector/fp と persistenceEffector/oop
- **最も永続化機能が充実**: persistenceEffector/fp と persistenceEffector/oop
- **最もパフォーマンスが高い**: defaultStyle/nonPersistence/fp と defaultStyle/nonPersistence/oop
- **最もバランスが取れている**: persistenceEffector/fp

詳細については、[implementation-comparison.md](implementation-comparison.md)を参照してください。

## このライブラリを使用するシーン

このライブラリは、特に以下のようなケースに適しています：

- 最初から永続化を考慮せず、段階的に実装したい場合。
- 従来のPekko Persistence Typedでは保守が難しい複雑な状態遷移を扱う場合。
- ドメインロジックをアクターから分離したいDDD指向の設計を実装する場合。
- イベントソーシングアプリケーションに、より自然なアクタープログラミングスタイルが必要な場合 (Scala または Java)。
- 一時的な永続化失敗に対する回復力が必要な場合。

## インストール方法

注意: 本ライブラリは `pekko-persistence-typed` に依存していません。`pekko-persistence-typed` を依存関係に追加しなくても本ライブラリを使用できます。

### SBT

`build.sbt` に以下を追加してください：

```scala
libraryDependencies ++= Seq(
  "io.github.j5ik2o" %% "pekko-persistence-effector" % "<最新バージョン>"
)
```

### Gradle

`build.gradle` に以下を追加してください：

```groovy
dependencies {
  implementation 'io.github.j5ik2o:pekko-persistence-effector_3:<最新バージョン>'
}
```

Kotlin DSL (`build.gradle.kts`) の場合：

```kotlin
dependencies {
  implementation("io.github.j5ik2o:pekko-persistence-effector_3:<最新バージョン>")
}
```

### Maven

`pom.xml` に以下を追加してください：

```xml
<dependency>
  <groupId>io.github.j5ik2o</groupId>
  <artifactId>pekko-persistence-effector_3</artifactId>
  <version>LATEST</version>
</dependency>
```

## ライセンス

このライブラリは Apache License 2.0 の下でライセンスされています。
