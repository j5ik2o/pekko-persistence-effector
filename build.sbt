import Dependencies.*

ThisBuild / organization := "io.github.j5ik2o"
ThisBuild / organizationName := "io.github.j5ik2o"
ThisBuild / scalaVersion := "3.6.4"
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

val publishSettings = Seq(
  publishMavenStyle := true,
  pomIncludeRepository := (_ => false),
  dynverSonatypeSnapshots := true,
  dynverSeparator := "-",
  publishTo := {
    val nexus = "https://s01.oss.sonatype.org/"
    val snapshots = "snapshots" at nexus + "content/repositories/snapshots"
    val releases = "releases" at nexus + "service/local/staging/deploy/maven2"

    if (isSnapshot.value) {
      Some(snapshots) // 例: 0.9.0‑SNAPSHOT → Sonatype snapshot
    } else if (sys.props.get("bundlePublish").contains("true")) {
      // GitHub Action から ‑DbundlePublish=true が来たときだけ
      Some(Resolver.file("bundle", file("central-bundle")))
    } else {
      Some(releases) // 普通のリリース (= staging)
    }
  },
  credentials += Credentials(
    "Sonatype Nexus Repository Manager",
    "central.sonatype.com",
    sys.env.getOrElse("SONATYPE_USERNAME", ""),
    sys.env.getOrElse("SONATYPE_PASSWORD", ""),
  ),
)

val testSettings = Seq(
  libraryDependencies ++= Seq(
    slf4j.julToSlf4J % Test,
    logback.classic % Test,
    scalatest.scalatest % Test,
    apachePekko.actorTestKitTyped % Test,
    apachePekko.serializationJackson % Test,
    "org.iq80.leveldb" % "leveldb" % "0.12" % Test,
    "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8" % Test,
  ),
  // IntelliJでのテスト実行時にLevelDBの依存関係が確実に含まれるようにする
  Compile / unmanagedClasspath += baseDirectory.value / "target" / "scala-3.6.4" / "test-classes",
  Test / testOptions += Tests.Setup { () =>
    val journalDir = new java.io.File("target/journal")
    val snapshotDir = new java.io.File("target/snapshot")
    if (!journalDir.exists()) journalDir.mkdirs()
    if (!snapshotDir.exists()) snapshotDir.mkdirs()
  },
  Test / fork := true,
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
    "-source:future",
    "-language:implicitConversions",
    "-language:higherKinds",
    "-language:postfixOps",
    "-language:adhocExtensions",
    "-explain",
    "-explain-types",
    "-Wunused:imports,privates",
    "-rewrite",
    "-no-indent",
    "-experimental",
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
  )

// ライブラリプロジェクト（publish対象）
lazy val library = (project in file("library"))
  .settings(baseSettings)
  .settings(testSettings)
  .settings(publishSettings)
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
    libraryDependencies ++= Seq(
      logback.classic,
      apachePekko.slf4j,
      apachePekko.persistenceTyped,
    ),
  )
  .dependsOn(library)

// 既存のコマンドエイリアスを維持
addCommandAlias("lint", ";scalafmtCheck;test:scalafmtCheck;scalafmtSbtCheck;scalafixAll --check;javafmtCheckAll")
addCommandAlias("fmt", ";scalafmtAll;scalafmtSbt;scalafix RemoveUnused;javafmtAll")
addCommandAlias("testCoverage", ";test;jacocoAggregateReport")
