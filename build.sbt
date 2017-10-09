name := "simple-configuration"
organization := "com.gu"

scalaVersion := "2.11.11"

bintrayOrganization := Some("guardian")
bintrayRepository := "platforms"

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))

val awsSdkVersion = "1.11.204"

val core = project
  .settings(
    name := "simple-configuration-core",
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-ec2" % awsSdkVersion,
      "com.typesafe" % "config" % "1.3.1",
      "org.slf4j" % "slf4j-api" % "1.7.25"
    )
  )

val s3 = project
  .dependsOn(core)
  .settings(
    name := "simple-configuration-s3",
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion
    )
  )

lazy val root = project.in(file("."))
  .aggregate(core, s3)

// Release
import ReleaseTransformations._
releasePublishArtifactsAction := PgpKeys.publishSigned.value
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