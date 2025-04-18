# pekko-persistence-effector プロジェクト概要

## 基本情報

- プロジェクト名: pekko-persistence-effector 
- 言語: Scala 3.6.4
- ベースフレームワーク: Apache Pekko

## プロジェクト目的

pekko-persistence-effector は、Apache Pekkoを使用したイベントソーシングパターンの実装を補助するライブラリです。アクターモデルとイベントソーシングを組み合わせ、状態マシン（State Machine）の状態とイベントを分離して管理するための機能を提供します。

本ライブラリは特に、従来のPekko Persistence Typedの実装における以下の問題点を解決することを目的としています：

1. 従来のアクタープログラミングスタイルとの違いによる学習・実装の困難さ
2. 複雑な状態遷移実装時の保守性低下
3. コマンドハンドラでの状態遷移の制限によるドメインオブジェクト活用の難しさ

## 設計思想と背景

pekko-persistence-effector は、Apache Pekko Persistence Typed の利用におけるいくつかの課題、特に従来のアクタープログラミングスタイルとの乖離や、複雑な状態遷移の実装・保守の難しさを解決するために開発されました。イベントソーシングの利点を活かしつつ、開発者がより直感的に、かつドメイン駆動設計（DDD）の原則に沿って実装を進められるような抽象化を提供することを目指しています。

特に、アクター内部で状態遷移ロジックを完結させるのではなく、ドメインオブジェクトにロジックを委譲しやすくすることで、アクターの責務をイベント永続化の調整役に集中させる設計を採用しました。これにより、テスト容易性やコードの再利用性が向上します。

また、最初から永続化機構を意識せずに開発を始められるよう、インメモリでの動作モードを提供し、段階的に永続化を導入できるアプローチをサポートしています。

## 主要コンポーネントの役割と意図

### 1. PersistenceEffector: イベント永続化の調整役

イベントソーシングにおけるイベントの永続化とスナップショット管理を担当する中心的なコンポーネントです。これは、ドメインロジックを持つ主アクター（集約アクターなど）の子アクターとして動作します。

**設計意図:**
- **従来スタイルの維持:** 主アクターは通常の Pekko Actor のスタイルで実装でき、`PersistenceEffector` がイベント永続化の詳細を隠蔽します。
- **ドメインロジックの二重実行回避:** 内部的に Untyped PersistentActor を利用していますが、これはイベント永続化のためだけであり、リカバリー時に主アクターのドメインロジックが再実行されることを防ぎます。イベント適用ロジックは主アクター側で一貫して管理されます。
- **柔軟なコールバック:** イベント永続化後のコールバック処理をサポートし、永続化完了をトリガーとした副作用（他のアクターへの通知など）を実行できます。

### 2. InMemoryEffector: 段階的実装とテストのためのインメモリ永続化

`PersistenceEffector` のインメモリ実装です。イベントやスナップショットをデータベースではなくメモリ上に保存します。

**設計意図:**
- **迅速な開発スタート:** データベース設定や依存関係なしに、イベントソーシングを用いた開発をすぐに開始できます。ビジネスロジックの実装に集中できます。
- **段階的導入:** まず `InMemoryEffector` でアプリケーションのコアロジックを構築し、後に実際の永続化バックエンド（JDBC, Cassandra など）を使用する `PersistenceEffector` に切り替える、という段階的な開発プロセスを可能にします。
- **高速なテスト:** データベースアクセスが不要なため、ユニットテストや統合テストを高速に実行できます。

### 3. PersistenceEffectorConfig: Effector の振る舞いを定義

`PersistenceEffector` の動作に必要な設定（永続化ID、初期状態、イベント適用ロジック、メッセージ変換方法など）を保持します。

**設計意図:**
- **明確な設定:** Effector の生成に必要な情報を一箇所に集約し、設定内容を明確にします。
- **`MessageConverter` との連携:** 状態、イベント、アクターメッセージ間の変換ロジック（`MessageConverter` で定義）を Effector に関連付けます。

### 4. MessageConverter: 状態・イベント・メッセージ間の通訳

アクターが送受信するメッセージと、内部で管理する状態（State）およびイベント（Event）との間の変換ルールを定義します。

**設計意図:**
- **関心の分離:** アクターのメッセージプロトコルと、永続化される状態・イベントの形式を分離します。これにより、それぞれを独立して変更しやすくなります。
- **型安全性:** 変換ロジックを型安全に定義できます。

### 5. Result: ドメイン操作の結果を明確化する構造

ドメインロジック（例: 集約ルートのメソッド）が、操作の結果として新しい状態（State）と発生したイベント（Event）の両方を返すための専用の構造です。

**設計意図:**
- **明確性:** 単純なタプル `(State, Event)` よりも、`Result(newState, event)` の方がコードを読む際に意図が明確になります。
- **保守性:** 将来的に操作結果に追加情報（例: 副作用を引き起こすコマンド）が必要になった場合でも、`Result` クラスを拡張することで対応しやすくなります。

## 実装の考え方と使用例

pekko-persistence-effector を使用する際の基本的な考え方は、**ドメインロジックを持つアクター（主アクター）** と **イベント永続化を担当する `PersistenceEffector`（子アクター）** を分離することです。

1.  **状態 (State) と イベント (Event) の定義:** まず、永続化したいアクターの状態と、その状態を変更するイベントを定義します。これらはプレーンな Scala のクラスやケースクラスで構いません。
2.  **ドメインロジックの実装:** 主アクター、あるいは主アクターが利用するドメインオブジェクト（例: 集約ルート）内に、コマンドを受け取り、状態遷移ロジックを実行し、結果として新しい状態と発生したイベントを返すメソッドを実装します。この際、`Result` クラスを使うと結果を明確に表現できます。
3.  **メッセージ (Message) の定義:** 主アクターが送受信するメッセージ型を定義します。`PersistenceEffector` から永続化完了通知やリカバリー完了通知を受け取るためのメッセージ型も定義する必要があります（これらは特定のトレイトを継承する必要はありませんが、`MessageConverter` で識別できるようにします）。
4.  **`MessageConverter` の実装:** 状態、イベント、メッセージ間の変換ロジックを実装します。例えば、「永続化されたイベントリストを、主アクターが受け取るべき単一の通知メッセージに変換する」といった処理を定義します。
5.  **`PersistenceEffectorConfig` の設定:** 永続化ID、初期状態、イベントを状態に適用する関数、そして作成した `MessageConverter` などを設定します。
6.  **`PersistenceEffector` の生成と利用:** 主アクター内で `PersistenceEffector.create` を呼び出して子アクターを生成します。ドメインロジックを実行して `Result(newState, event)` を得たら、`effector ! PersistenceEffector.Persist(event)` のようにしてイベントの永続化を依頼します。永続化が完了すると、`MessageConverter` で定義されたメッセージが主アクターに送られてきます。

**銀行口座集約の例 (`BankAccountAggregate.scala`) が示すこと:**
このサンプルコードは、上記のような考え方に基づき、銀行口座というドメインモデルの状態遷移（入金、出金、開設など）を、通常の Pekko Actor のスタイルで実装する方法を示しています。`BankAccountAggregate` が主アクターであり、その内部で `PersistenceEffector` を利用しています。状態遷移ロジックの多くは `BankAccount` ドメインオブジェクト内にカプセル化されており、`BankAccountAggregate` は主にコマンドの受付、ドメインオブジェクトへの処理委譲、`PersistenceEffector` への永続化依頼、そして永続化完了後の応答処理を担当しています。これにより、アクターのコードがシンプルに保たれ、ドメインロジックのテストも容易になります。

## メリット: なぜ pekko-persistence-effector を使うのか？

- **イベントソーシング実装の簡素化:** イベント永続化の複雑な詳細を `PersistenceEffector` が隠蔽するため、開発者はドメインロジックとアクターのメッセージングに集中できます。
- **従来のアクタープログラミングスタイルを維持:** Pekko Persistence Typed のような専用の DSL を学習する必要が少なく、既存の Pekko Actor の知識や設計パターン（`Behaviors.receive`, `Behaviors.setup` など）をそのまま活かせます。状態に応じた `Behavior` の切り替えなども通常通り行えます。これは、特に Pekko Persistence Typed のスタイルに馴染めない開発者にとって大きな利点です。
- **段階的な実装のサポート:** `InMemoryEffector` を使うことで、データベース設定なしに開発を開始し、後から容易に永続化バックエンド（JDBC, Cassandra など）を追加できます。プロトタイピングや、永続化要件が後から決まる場合に有効です。`PersistenceEffector` への切り替えは、主に設定（`PersistenceEffectorConfig`）の変更と依存ライブラリの追加で行えます。
- **DDD との高い親和性:** ドメインロジックをアクターから分離し、ドメインオブジェクト（集約ルートなど）に実装することが容易になります。アクターはコマンドを受け付けてドメインオブジェクトに処理を委譲し、その結果（新しい状態とイベント）を `PersistenceEffector` に永続化させる、という流れを自然に実現できます。これにより、ドメインモデルの純粋性が保たれ、テスト容易性も向上します。
- **複雑な状態遷移の保守性向上:** アクターの状態遷移ロジックが、Pekko Persistence Typed のように単一のコマンドハンドラやイベントハンドラに集中するのではなく、通常の Behavior 切り替えやメソッド分割によって整理できます。これにより、コードの見通しが良くなり、変更やデバッグが容易になります。
- **型安全性:** Scala の型システムを活用し、状態、イベント、メッセージ間の変換などを型安全に行えます。

## Pekko Persistence Typed との比較

| 特徴                     | pekko-persistence-effector                     | Pekko Persistence Typed                      |
| :----------------------- | :--------------------------------------------- | :------------------------------------------- |
| **プログラミングモデル** | 通常の Pekko Actor スタイル (Behavior ベース)  | 専用の DSL (EventSourcedBehavior)            |
| **状態遷移ロジック**     | アクターの Behavior / ドメインオブジェクト     | CommandHandler / EventHandler 内             |
| **イベント永続化**       | `PersistenceEffector` (子アクター) が担当      | `EventSourcedBehavior` が内部で処理          |
| **学習コスト**           | 低 (Pekko Actor の知識が活かせる)              | 中 (専用 DSL の学習が必要)                   |
| **DDD との親和性**       | 高 (ドメインオブジェクトへの委譲が容易)        | 中〜高 (工夫次第で可能)                      |
| **段階的実装**           | 容易 (`InMemoryEffector` から切り替え)         | 可能 (インメモリジャーナル利用)              |
| **コードの複雑さ**       | 状態遷移が複雑な場合にシンプルに保ちやすい     | 状態遷移が複雑になるとハンドラが肥大化しがち |
| **ボイラープレート**     | `MessageConverter` 等の実装が必要              | DSL に従うため、ある程度の定型コードが必要   |

**どちらを選ぶべきか？**

- **pekko-persistence-effector が適しているケース:**
    - 既存の Pekko Actor プロジェクトにイベントソーシングを導入したい。
    - Pekko Persistence Typed の DSL に抵抗がある、または学習コストを抑えたい。
    - ドメインロジックをアクターから明確に分離したい (DDD)。
    - 状態遷移が非常に複雑で、通常の Behavior 切り替えで管理したい。
    - 永続化を後から導入する段階的な開発を行いたい。
- **Pekko Persistence Typed が適しているケース:**
    - Pekko が提供する標準的なイベントソーシング実装を使いたい。
    - DSL を用いたイベントソーシングの定型的な書き方にメリットを感じる。
    - コミュニティのサポートやドキュメントの豊富さを重視する。

## 利用上の注意点 / はまりどころ

- **`MessageConverter` の実装:** 状態・イベント・メッセージ間の変換ロジックを正確に実装する必要があります。特にリカバリー時のメッセージ変換 (`wrapRecoveredState`, `unwrapRecoveredState`) は忘れがちなので注意が必要です。
- **永続化ID の一意性:** `PersistenceEffectorConfig` で指定する `persistenceId` は、システム全体で一意である必要があります。
- **イベントの不変性:** 永続化されたイベントは変更しないことが原則です。イベントスキーマの変更が必要な場合は、スキーマバージョニングやイベントアダプターなどの戦略を検討する必要があります（これは本ライブラリ固有ではなく、イベントソーシング一般の課題です）。
- **`PersistenceEffector` のライフサイクル:** `PersistenceEffector` は主アクターの子アクターとして動作するため、主アクターの停止に伴って停止します。

## 適用シナリオ

以下のような状況で特に有効です：

- **段階的なイベントソーシング導入:** まずは `InMemoryEffector` でコアロジックを開発し、後から永続化バックエンドを接続する。
- **複雑なドメインロジック:** 状態遷移が多岐にわたる、または外部サービス連携などを含む複雑なドメインロジックを、通常のアクタープログラミングスタイルで整理しながら実装したい場合。
- **DDD 実践プロジェクト:** ドメインモデルの純粋性を保ち、アクターを集約の管理や永続化の調整役に専念させたい場合。
- **Pekko Persistence Typed からの移行:** Pekko Persistence Typed の実装スタイルに課題を感じているプロジェクトが、より従来のアクタースタイルに近い形でイベントソーシングを再実装したい場合。
