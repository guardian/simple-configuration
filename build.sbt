import ReleaseTransformations._

name := "simple-configuration"

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/guardian/simple-configuration"),
    "scm:git@github.com:guardian/simple-configuration.git"
  )
)

ThisBuild / homepage := Some(url("https://github.com/guardian/simple-configuration"))

ThisBuild / developers := List(Developer(
  id = "Guardian",
  name = "Guardian",
  email = null,
  url = url("https://github.com/guardian")
))

val scala_2_12: String = "2.12.18"
val scala_2_13: String = "2.13.11"

val awsSdkVersion = "2.20.95"

scalaVersion := scala_2_13

ThisBuild / publishTo := sonatypePublishTo.value

val sharedSettings = Seq(
  scalaVersion := scala_2_13,
  scalacOptions += "-target:jvm-1.8",
  crossScalaVersions := Seq(scala_2_12, scala_2_13),
  releaseCrossBuild := true,
  licenses += ("Apache-2.0", url(
    "http://www.apache.org/licenses/LICENSE-2.0.html"
  )),
  organization := "com.gu",
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    releaseStepCommandAndRemaining("+publishSigned"),
    releaseStepCommand("sonatypeBundleRelease"),
    setNextVersion,
    commitNextVersion,
    pushChanges
  )
)

val core = project
  .settings(sharedSettings)
  .settings(
    name := "simple-configuration-core",
    libraryDependencies ++= Seq(
      "software.amazon.awssdk" % "ec2" % awsSdkVersion,
      "software.amazon.awssdk" % "autoscaling" % awsSdkVersion,
      "com.typesafe" % "config" % "1.4.2",
      "org.slf4j" % "slf4j-api" % "2.0.7"
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
    publish := {},
    releaseCrossBuild := true,
    crossScalaVersions := Seq(scala_2_12, scala_2_13)
  )
