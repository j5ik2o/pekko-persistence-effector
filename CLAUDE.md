# CLAUDE.md

このファイルは、このリポジトリでコードを扱う際のClaude Code (claude.ai/code)向けのガイダンスを提供します。

## ビルドと開発コマンド

### ビルドコマンド
```bash
# プロジェクトのコンパイル
sbt compile

# テストの実行
sbt test

# カバレッジレポート付きテスト実行
sbt testCoverage

# 特定モジュールのテスト実行
sbt "library/test"
sbt "example/test"

# 単一テストクラスの実行
sbt "library/testOnly com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl.SnapshotCriteriaIntegrationSpec"

# パターンマッチングでテスト実行
sbt "library/testOnly *SnapshotHelper*"
```

### コード品質コマンド
```bash
# リンティング（フォーマットとスタイルのチェック）
sbt lint

# コードの自動フォーマット
sbt fmt

# フォーマットチェック（修正なし）
sbt scalafmtCheck
```

### SBTインタラクティブモード
```bash
# SBTシェルの起動
sbt

# SBTシェル内 - 継続的コンパイル
~compile

# SBTシェル内 - ファイル変更時のテスト実行
~test
```

## プロジェクトアーキテクチャ

### コアアブストラクション: PersistenceEffectorパターン

このライブラリは、従来のPekkoパーシステンスを親アクターでラップする革新的なパターンを実装し、標準的なEventSourcedBehaviorで発生するドメインロジックの二重実行問題を解決します。

**主要なアーキテクチャの洞察**: EventSourcedBehaviorを直接使用する代わりに、ライブラリは状態を管理し、永続化操作を子のPersistenceStoreActor（untyped）に委譲する親アクターを作成します。この分離により以下が可能になります：
- ドメインロジックの単一実行（親アクターのみ）
- 自然なBehaviorベースのプログラミングスタイル
- 状態固有のメッセージハンドラー
- インメモリモードと永続化モード間のシームレスな切り替え

### モジュール構造

- **library/**: コアとなるpersistence effector実装
  - `scaladsl/`: Scala 3の機能を使用した型安全なAPIを持つScala DSL
  - `javadsl/`: Java/Kotlin互換性のためのビルダーパターンを持つJava DSL
  - `internal/scalaimpl/`: コア実装（PersistenceStoreActor、effector実装）
  - `internal/javaimpl/`: Java固有のラッパーとコンバーター

- **example/**: 異なる実装アプローチを示す包括的な例
  - `scalaimpl/`: Scalaの例（FPとOOPスタイル）
  - `javaimpl/`: Javaの例（ビルダーパターンを使用したOOPスタイル）
  - それぞれ`defaultStyle/`（従来のPekko）と`persistenceEffector/`の比較を含む

### 重要な実装詳細

1. **PersistenceStoreActor**: 実際の永続化操作を処理するuntypedアクター。リトライメカニズムとバックオフ戦略を含むプロトコルを通じて親と通信します。

2. **メッセージ変換**: ライブラリはMessageConverterを使用してドメインメッセージと内部永続化プロトコルメッセージ間を変換し、柔軟性を保ちながら型安全性を維持します。

3. **状態管理**: 状態遷移は永続化成功後に親アクターで一度だけ発生し、二重実行問題を回避します。

4. **エラーハンドリング階層**:
   - ドメイン検証エラー: 親アクターで即座に処理
   - 永続化失敗: 設定可能なバックオフでリトライメカニズムをトリガー
   - システム障害: スーパービジョン戦略を通じてエスカレーション

5. **スナップショットと保持**: 設定可能なスナップショット基準と保持ポリシーは、いつスナップショットを取るか、どのイベントを保持するかを決定するヘルパークラス（SnapshotHelper、RetentionHelper）を使用して評価されます。

## テストアプローチ

テストはPekko TestKitを使用したScalaTestを使用します。テストファイルは`*Spec.scala`パターンに従い、`src/test/scala`に配置されます。テストには永続化設定（ローカルテスト用のleveldb）を含む適切なアクターシステムのセットアップが必要です。

テスト作成時の注意点：
- typedアクターには`ActorTestKit`を使用
- `TEST_TIME_FACTOR`環境変数を使用して適切なタイムアウトを設定
- テスト実行間でjournal/snapshotディレクトリをクリーンアップ
- 包括的なカバレッジのためにインメモリモードと永続化モードの両方をテスト

## 主要な依存関係

- Apache Pekko（actors、persistence、typed、testkit）
- プライマリ言語としてScala 3.7.0
- ロギング用のSLF4J/Logback
- テスト用のScalaTest
- ローカル永続化テスト用のLevelDB