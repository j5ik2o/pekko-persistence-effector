import Dependencies.*

ThisBuild / organization := "io.github.j5ik2o"
ThisBuild / organizationName := "io.github.j5ik2o"
ThisBuild / scalaVersion := "3.7.4"
ThisBuild / homepage := Some(url("https://github.com/j5ik2o/pekko-persistence-effector"))
ThisBuild / licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html"))
ThisBuild / developers := List(
  Developer(
    id = "j5ik2o",
    name = "Junichi Kato",
    email = "j5ik2o@gmail.com",
    url = url("https://blog.j5ik2o.me"),
  ),
)
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/j5ik2o/pekko-persistence-effector"),
    "scm:git@github.com:j5ik2o/pekko-persistence-effector.git",
  ),
)
ThisBuild / dynverSonatypeSnapshots := true
ThisBuild / dynverSeparator := "-"
ThisBuild / publishMavenStyle := true
ThisBuild / pomIncludeRepository := (_ => false)
ThisBuild / credentials += Credentials(Path.userHome / ".sbt" / "1.0" / "sonatype_credentials")

val testSettings = Seq(
  libraryDependencies ++= Seq(
    slf4j.julToSlf4J % Test,
    logback.classic % Test,
    scalatest.scalatest % Test,
    apachePekko.actorTestKitTyped % Test,
    apachePekko.serializationJackson % Test,
    orgIq80Leveldb.leveldb % Test,
    orgFusesourceLeveldbjni.leveldbjni % Test,
  ),
  Compile / unmanagedClasspath += Attributed.blank(fileConverter.value.toVirtualFile(
    (baseDirectory.value / "target" / s"scala-${scalaVersion.value}" / "test-classes").toPath): xsbti.HashedVirtualFileRef),
  Test / testOptions += Tests.Setup { () =>
    val journalDir = new java.io.File("target/journal")
    val snapshotDir = new java.io.File("target/snapshot")
    if (!journalDir.exists()) journalDir.mkdirs()
    if (!snapshotDir.exists()) snapshotDir.mkdirs()
  },
  Test / fork := true,
  // 暫定: sbt 2 の既定の並列で、example の in-memory 系のテストが静的なインメモリのストアと
  // target/journal をスイート間で共有して不安定になるため、並列実行を止める。
  // 本筋の直しは別の issue: #236 (https://github.com/j5ik2o/pekko-persistence-effector/issues/236)
  Test / parallelExecution := false,
  Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oD"),
  Test / javaOptions += s"-Djacoco-agent.destfile=target/scala-${scalaVersion.value}/jacoco/data/jacoco.exec",
  jacocoIncludes := Seq("*com.github.j5ik2o*"),
  jacocoExcludes := Seq(),
)

// 共通設定
val baseSettings = Seq(
  javacOptions ++= Seq("-source", "21", "-target", "21"),
  scalacOptions ++= Seq(
    "-encoding",
    "utf8",
    "-feature",
    "-deprecation",
    "-unchecked",
    "-source:3.7",
    "-language:implicitConversions",
    "-language:higherKinds",
    "-language:postfixOps",
    "-language:adhocExtensions",
    "-explain",
    "-explain-types",
    "-Wunused:imports,privates",
    "-no-indent",
  ),
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision,
)

// ルートプロジェクト（publishなし）
lazy val root = (project in file("."))
  .aggregate(library, example)
  .settings(
    name := "pekko-persistence-effector-root",
    publish / skip := true,
    // ルートはクラスを持たず JaCoCo の計装ディレクトリが作られないため、sbt 2 のファイル追跡が失敗する。
    // 常に存在する classes ディレクトリを指して計装対象なしにする（ルートは publish もしない）。
    jacocoInstrumentedDirectory := (Compile / classDirectory).value,
  )

// ライブラリプロジェクト（publish対象）
lazy val library = (project in file("library"))
  .settings(baseSettings)
  .settings(testSettings)
  .settings(
    name := "pekko-persistence-effector",
    // 現在のライブラリの依存関係をそのまま維持
    libraryDependencies ++= Seq(
      slf4j.api,
      apachePekko.slf4j,
      apachePekko.actorTyped,
      apachePekko.persistence,
    ),
  )

// サンプルプロジェクト（publishなし）
lazy val example = (project in file("example"))
  .settings(baseSettings)
  .settings(testSettings)
  .settings(
    name := "pekko-persistence-effector-example",
    publish / skip := true,
    // 利用者と同じく -experimental なしでコンパイルし、ライブラリに -experimental が戻ったら失敗させる
    scalacOptions -= "-experimental",
    libraryDependencies ++= Seq(
      logback.classic,
      apachePekko.slf4j,
      apachePekko.persistenceTyped,
    ),
  )
  .dependsOn(library)

// 既存のコマンドエイリアスを維持
addCommandAlias("lint", ";scalafmtCheck;Test/scalafmtCheck;scalafmtSbtCheck;scalafixAll --check;javafmtCheckAll")
addCommandAlias("fmt", ";scalafmtAll;scalafmtSbt;scalafix RemoveUnused;javafmtAll")
addCommandAlias("testCoverage", ";test;jacocoAggregateReport")
