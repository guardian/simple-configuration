import ReleaseTransformations.*
import sbtversionpolicy.withsbtrelease.ReleaseVersion

name := "simple-configuration"

val awsSdkVersion = "2.25.47"

scalaVersion := "2.13.13"

val sharedSettings = Seq(
  scalaVersion := "2.13.13",
  crossScalaVersions := Seq("3.3.3", scalaVersion.value, "2.12.19"),
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
    releaseVersion := ReleaseVersion.fromAggregatedAssessedCompatibilityWithLatestRelease().value,
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
    )
  )
