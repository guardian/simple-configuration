import ReleaseTransformations._
import sbtversionpolicy.withsbtrelease.ReleaseVersion

name := "simple-configuration"

val scala_2_12: String = "2.12.19"
val scala_2_13: String = "2.13.13"

val awsSdkVersion = "2.25.26"

scalaVersion := scala_2_13

val sharedSettings = Seq(
  scalaVersion := scala_2_13,
  crossScalaVersions := Seq(scala_2_12, scala_2_13),
  licenses := Seq(License.Apache2),
  organization := "com.gu",
  scalacOptions := Seq("-release:11")
)

val core = project
  .settings(sharedSettings)
  .settings(
    name := "simple-configuration-core",
    libraryDependencies ++= Seq(
      "software.amazon.awssdk" % "ec2" % awsSdkVersion,
      "software.amazon.awssdk" % "autoscaling" % awsSdkVersion,
      "com.typesafe" % "config" % "1.4.3",
      "org.slf4j" % "slf4j-api" % "2.0.12"
    )
  )

val s3 = project
  .settings(sharedSettings)
  .dependsOn(core)
  .settings(
    name := "simple-configuration-s3",
    libraryDependencies ++= Seq("software.amazon.awssdk" % "s3" % awsSdkVersion)
  )

val ssm = project
  .settings(sharedSettings)
  .dependsOn(core)
  .settings(
    name := "simple-configuration-ssm",
    libraryDependencies ++= Seq(
      "software.amazon.awssdk" % "ssm" % awsSdkVersion
    )
  )

lazy val root = project
  .in(file("."))
  .aggregate(core, s3, ssm)
  .settings(
    publish / skip := true,
    releaseCrossBuild := true,
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      setNextVersion,
      commitNextVersion
    ),
    crossScalaVersions := Seq(scala_2_12, scala_2_13),
    releaseVersion := ReleaseVersion.fromAggregatedAssessedCompatibilityWithLatestRelease().value
  )
