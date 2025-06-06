# Pekko Persistence Typedの実装方法の改善について

## 目的

このドキュメントの目的は、Pekko Persistence Typedの実装方法の改善についての提案です。

## Pekko Persistence Typed における課題

Pekko Persistence Typed は Pekko におけるイベントソーシングの標準的な実装方法ですが、特定の実装スタイルやユースケースにおいて、いくつかの課題が生じる可能性があります。

- **課題1: 従来のアクタープログラミングスタイルとの乖離**
    - Pekko Persistence Typed は `EventSourcedBehavior` という専用の DSL を導入しており、これは `Behaviors.receive` や `Behaviors.setup` を中心とした従来の Pekko Actor のプログラミングスタイルとは異なります。
    - これにより、既存の Pekko Actor の知識や設計パターン（例: 状態に応じた `Behavior` の切り替え）が直接適用しにくく、開発者は新しいスタイルを学習する必要があります。特に、イベントソーシングを初めて導入する場合や、既存のアクターベースのシステムに組み込む場合に、学習コストや実装上の戸惑いが生じることがあります。

- **課題2: 複雑な状態遷移における保守性の低下**
    - `EventSourcedBehavior` では、コマンド処理ロジックは主に単一の `commandHandler` に、イベント適用ロジックは単一の `eventHandler` に記述されます。
    - アクターが持つ状態の種類が多く、状態遷移が複雑になってくると、これらのハンドラ関数が肥大化し、条件分岐（`match/case`）が複雑になりがちです。
    - 従来のアクタースタイルであれば `Behavior` を切り替えることで状態ごとの処理を明確に分離できますが、`EventSourcedBehavior` の構造ではそれが難しく、結果としてコードの見通しが悪くなり、保守性が低下する可能性があります。（※ ハンドラ関数を内部で分割するなどの対策は可能ですが、基本的な構造は変わりません。）

- **課題3: ドメインオブジェクトとの連携の難しさ (DDD との相性)**
    - ドメイン駆動設計 (DDD) では、ビジネスロジックをドメインオブジェクト（エンティティや値オブジェクト）にカプセル化することが推奨されます。
    - しかし、Pekko Persistence Typed の `commandHandler` は、直接アクターの状態を変更するのではなく、永続化すべきイベント（または副作用なしを示す `Effect.none` など）を含む `ReplyEffect` を返す必要があります。
    - この制約のため、ドメインオブジェクトのメソッドが新しい状態オブジェクトを返したとしても（例: `bankAccount.add(amount)` が新しい `BankAccount` インスタンスを返す）、その新しい状態を `commandHandler` 内で直接アクターの次の状態として利用することができません。状態の更新は、`eventHandler` がイベントを適用する際に間接的に行われます。
    - これにより、ドメインオブジェクトの設計とアクターの実装との間にギャップが生じ、ドメインロジックを自然な形でアクターに組み込むことが難しくなる場合があります。特に、コマンド処理の結果として得られた新しい状態をすぐに利用したい場合に、実装が煩雑になる可能性があります。

これらの課題は、特に状態遷移が複雑なドメインや、DDD の原則に厳密に従いたい場合、あるいは既存の Pekko Actor の開発スタイルを維持したい場合に顕著になる可能性があります。

## pekko-persistence-effector による解決アプローチ

pekko-persistence-effector は、これらの課題に対処するために、異なるアーキテクチャを採用しています。

- **解決策1: 従来スタイルの維持と永続化の分離**
    - イベント永続化の責務を、主アクターの子アクターとして動作する `PersistenceEffector` に委譲します。
    - これにより、主アクターは `EventSourcedBehavior` を使う必要がなくなり、**通常の `Behavior` ベースのプログラミングスタイルをそのまま利用できます**。状態に応じた `Behavior` の切り替えも自由に行えます。開発者は慣れ親しんだスタイルで実装を進めることができます。

- **解決策2: 状態遷移ロジックの自由な配置**
    - 主アクターは通常の `Behavior` を使用するため、状態遷移ロジックを状態ごとにメソッドに分割するなど、**従来のアクタースタイルでコードを整理できます**。これにより、複雑な状態遷移を持つアクターでも、見通し良く保守性の高いコードを維持しやすくなります。

- **解決策3: ドメインオブジェクトとの自然な連携**
    - 主アクターのメッセージハンドラ（`Behavior` 内）では、**ドメインオブジェクトのメソッドを呼び出し、その結果（新しい状態とイベントを含む `Result` など）を直接受け取って利用できます**。
    - アクターは、受け取った新しい状態を自身の次の状態として保持し、同時に `Result` に含まれるイベントを `PersistenceEffector` に渡して永続化を依頼します。
    - このように、**状態の更新は主アクター側で直接的に行われ、永続化は副作用として分離される**ため、ドメインオブジェクトとの連携がより自然になり、DDD のプラクティスとも整合しやすくなります。

### InMemoryEffector による段階的実装の支援

さらに、`InMemoryEffector` は、これらの課題に対する**段階的なアプローチ**を可能にします。

- **開発初期:** 開発者は、まず `InMemoryEffector` を使用して、永続化の詳細や `EventSourcedBehavior` の制約を意識することなく、**通常のアクタープログラミングとドメインロジックの実装に集中できます**。これにより、ビジネスロジックの検証やテストを迅速に行えます。
- **永続化の導入:** アプリケーションのコアロジックが固まった後、必要に応じて `PersistenceEffector` の実装を `DefaultPersistenceEffector` に切り替え、実際の永続化バックエンド（データベースなど）を設定します。

このワークフローにより、イベントソーシング導入の初期障壁が低減され、よりスムーズな開発プロセスが実現します。

## まとめ: どのような場合に検討すべきか？

Pekko Persistence Typed が多くのケースで有効な選択肢である一方、以下のような課題を感じている、または要件がある場合には、pekko-persistence-effector のアプローチを検討する価値があります。

- **従来のアクタースタイルを維持したい:** `EventSourcedBehavior` の DSL よりも、`Behaviors` を使ったプログラミングスタイルを好む、または既存のコードベースとの一貫性を保ちたい場合。
- **状態遷移が複雑:** アクターの状態が多く、遷移ロジックが複雑になり、`commandHandler` が肥大化して保守性に懸念がある場合。状態ごとに `Behavior` を分割したい場合。
- **DDD との親和性を高めたい:** ドメインオブジェクトを積極的に活用し、コマンドハンドラ内でドメインオブジェクトが返した新しい状態を直接利用したい場合。アクターの責務をドメインロジックの調整役に限定したい場合。
- **段階的にイベントソーシングを導入したい:** まずは永続化なしでアクターとドメインロジックを実装・テストし、後から永続化機能を追加したい場合。
