name := "activation-lab"
version := "0.1.0"
scalaVersion := "2.13.14"

libraryDependencies ++= Seq(
  "com.lihaoyi" %% "cask"       % "0.9.4",   // simple Flask-like web framework
  "com.lihaoyi" %% "upickle"    % "3.3.1",   // JSON, used to store event "properties"
  "org.postgresql" % "postgresql" % "42.7.3" // JDBC driver for Postgres
)

// Produces a single runnable "fat" jar: `sbt assembly` -> target/scala-2.13/activation-lab.jar
assembly / assemblyJarName := "activation-lab.jar"
assembly / mainClass := Some("app.Main")
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "services", _ @ _*) => MergeStrategy.filterDistinctLines
  case PathList("META-INF", _ @ _*)             => MergeStrategy.discard
  case _                                        => MergeStrategy.first
}
