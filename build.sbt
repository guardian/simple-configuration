import ReleaseTransformations._

name := "simple-configuration"
organization := "com.gu"

val scala_2_11: String = "2.11.12"
val scala_2_12: String = "2.12.12"
val scala_2_13: String = "2.13.5"

val awsSdkVersion = "2.16.7"

scalaVersion := scala_2_11

val sharedSettings = Seq(
  scalaVersion := scala_2_11,
  scalacOptions += "-target:jvm-1.8",
  crossScalaVersions := Seq(scala_2_11, scala_2_12, scala_2_13),
  releaseCrossBuild := true,
  licenses += ("Apache-2.0", url(
    "http://www.apache.org/licenses/LICENSE-2.0.html"
  )),
  organization := "com.gu",
  bintrayOrganization := Some("guardian"),
  bintrayRepository := "platforms",
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    publishArtifacts,
    releaseStepTask(bintrayRelease),
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
      "com.typesafe" % "config" % "1.4.1",
      "org.slf4j" % "slf4j-api" % "1.7.30"
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
    crossScalaVersions := Seq(scala_2_11, scala_2_12, scala_2_13)
  )
