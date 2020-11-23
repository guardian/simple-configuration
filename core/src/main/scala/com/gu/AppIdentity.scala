package com.gu

import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.{
  AwsCredentialsProvider,
  DefaultCredentialsProvider
}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.regions.internal.util.EC2MetadataUtils
import software.amazon.awssdk.services.autoscaling.AutoScalingClient
import software.amazon.awssdk.services.autoscaling.model.{
  DescribeAutoScalingGroupsRequest,
  DescribeAutoScalingInstancesRequest
}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

sealed trait AppIdentity

case class AwsIdentity(app: String,
                       stack: String,
                       stage: String,
                       region: String)
    extends AppIdentity

case class DevIdentity(app: String) extends AppIdentity

object AppIdentity {
  private val logger = LoggerFactory.getLogger(this.getClass)

  private def safeAwsOperation[A](
    errorMessage: => String
  )(operation: => A): Option[A] = Try(operation) match {
    case Success(value) => Option(value)
    case Failure(e) =>
      logger.error(errorMessage, e)
      None
  }

  private def fromASGTags(
    credentials: => AwsCredentialsProvider
  ): Option[AwsIdentity] = {
    // We read tags from the AutoScalingGroup rather than the instance itself to avoid problems where the
    // tags have not been applied to the instance before we start up (they are eventually consistent)
    def withOneOffAsgClient[T](f: AutoScalingClient => T): T = {
      val asgClient: AutoScalingClient = AutoScalingClient.builder
        .region(Region.of(region))
        .credentialsProvider(credentials)
        .build()
      val returned = f(asgClient)
      asgClient.close()
      returned
    }

    def getTags(asgClient: AutoScalingClient,
                instanceId: String): Map[String, String] = {
      val describeAutoScalingInstancesRequest =
        DescribeAutoScalingInstancesRequest.builder
          .instanceIds(instanceId)
          .build()
      val describeAutoScalingInstancesResult =
        asgClient.describeAutoScalingInstances(
          describeAutoScalingInstancesRequest
        )
      val autoScalingGroupName =
        describeAutoScalingInstancesResult.autoScalingInstances.asScala.head.autoScalingGroupName
      val describeAutoScalingGroupsRequest =
        DescribeAutoScalingGroupsRequest.builder
          .autoScalingGroupNames(autoScalingGroupName)
          .build()
      val describeAutoScalingGroupsResult =
        asgClient.describeAutoScalingGroups(describeAutoScalingGroupsRequest)
      val autoScalingGroup =
        describeAutoScalingGroupsResult.autoScalingGroups.asScala.head

      autoScalingGroup.tags.asScala.map { t =>
        t.key -> t.value
      }.toMap
    }

    Option(EC2MetadataUtils.getInstanceId).map { instanceId =>
      val tags = withOneOffAsgClient(client => getTags(client, instanceId))
      AwsIdentity(
        app = tags("App"),
        stack = tags("Stack"),
        stage = tags("Stage"),
        region = EC2MetadataUtils.getEC2InstanceRegion
      )
    }
  }

  private def getEnv(variableName: String): Option[String] =
    Option(System.getenv(variableName))

  private def fromLambdaEnvVariables(): Option[AppIdentity] = {
    for {
      app <- getEnv("App")
      stack <- getEnv("Stack")
      stage <- getEnv("Stage")
      region <- getEnv("AWS_DEFAULT_REGION")
    } yield
      AwsIdentity(app = app, stack = stack, stage = stage, region = region)
  }

  private def fromTeamcityEnvVariables(
    defaultAppName: String
  ): Option[AppIdentity] = {
    for {
      _ <- getEnv("TEAMCITY_VERSION")
    } yield DevIdentity(defaultAppName)
  }

  def region: String = {
    lazy val ec2Region =
      safeAwsOperation("Failed to identify the regionName of the instance") {
        EC2MetadataUtils.getEC2InstanceRegion
      }
    lazy val lambdaRegion = Option(System.getenv("AWS_DEFAULT_REGION"))
    lambdaRegion orElse ec2Region getOrElse "eu-west-1"
  }

  def whoAmI(defaultAppName: String,
             credentials: => AwsCredentialsProvider =
               DefaultCredentialsProvider.create()): AppIdentity = {
    val result = fromTeamcityEnvVariables(defaultAppName)
      .orElse(fromLambdaEnvVariables())
      .orElse(fromASGTags(credentials))
      .getOrElse(DevIdentity(defaultAppName))
    logger.info(s"Detected the following AppIdentity: $result")
    result
  }
}
