package com.github.j5ik2o.pekko.persistence.effector.internal.scalaimpl

import com.github.j5ik2o.pekko.persistence.effector.scaladsl.PersistenceMode

import java.io.File

/**
 * Persistedモードを使用したPersistenceEffectorのテスト
 */
class PersistedEffectorSpec extends PersistenceEffectorTestBase {
  override def persistenceMode: PersistenceMode = PersistenceMode.Persisted

  // スナップショットテストを実行する
  override def runSnapshotTests: Boolean = true

  // テスト前にLevelDBの保存ディレクトリを確実に作成
  override def beforeAll(): Unit = {
    val journalDir = new File("target/journal")
    val snapshotDir = new File("target/snapshot")

    if (!journalDir.exists()) {
      journalDir.mkdirs()
    }

    if (!snapshotDir.exists()) {
      snapshotDir.mkdirs()
    }

    super.beforeAll()
  }

  // テスト後にディレクトリをクリーンアップ
  override def afterAll(): Unit =
    super.afterAll()
}
