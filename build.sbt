import ReleaseTransformations._

name := "simple-configuration"
organization := "com.gu"

val scala_2_11: String = "2.11.12"
val scala_2_12: String = "2.12.4"

val awsSdkVersion = "1.11.204"

scalaVersion := scala_2_11

val sharedSettings = Seq(
  scalaVersion := scala_2_11,
  crossScalaVersions := Seq(scala_2_11, scala_2_12),
  releaseCrossBuild := true,
  licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html")),
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
      "com.amazonaws" % "aws-java-sdk-ec2" % awsSdkVersion,
      "com.typesafe" % "config" % "1.3.1",
      "org.slf4j" % "slf4j-api" % "1.7.25"
    )
  )

val s3 = project
  .settings(sharedSettings)
  .dependsOn(core)
  .settings(
    name := "simple-configuration-s3",
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion
    )
  )

lazy val root = project.in(file("."))
  .aggregate(core, s3)
  .settings(
    publish := {},
    releaseCrossBuild := true,
    crossScalaVersions := Seq(scala_2_11, scala_2_12)
  )

