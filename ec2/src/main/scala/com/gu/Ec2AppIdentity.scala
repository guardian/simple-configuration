package com.gu

import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.regions.internal.util.EC2MetadataUtils
import software.amazon.awssdk.services.autoscaling.AutoScalingClient
import software.amazon.awssdk.services.autoscaling.model.{
  DescribeAutoScalingGroupsRequest,
  DescribeAutoScalingInstancesRequest
}

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

object Ec2AppIdentity {
  private val logger = LoggerFactory.getLogger(this.getClass)

  private def safeAwsOperation[A](errorMessage: => String)(operation: => A): Try[A] = {
    val result = Try(operation)
    result.failed.foreach(e => logger.error(errorMessage, e))
    result
  }

  private def fromASGTags(credentials: => AwsCredentialsProvider): Try[AwsIdentity] = {
    // We read tags from the AutoScalingGroup rather than the instance itself to avoid problems where the
    // tags have not been applied to the instance before we start up (they are eventually consistent)
    def withOneOffAsgClient[T](region: String, f: AutoScalingClient => T): T = {
      val asgClient: AutoScalingClient = AutoScalingClient.builder
        .region(Region.of(region))
        .credentialsProvider(credentials)
        .build()
      val returned = f(asgClient)
      asgClient.close()
      returned
    }

    def getTags(asgClient: AutoScalingClient, instanceId: String): Map[String, String] = {
      val describeAutoScalingInstancesRequest = DescribeAutoScalingInstancesRequest.builder
          .instanceIds(instanceId)
          .build()
      val describeAutoScalingInstancesResult = asgClient.describeAutoScalingInstances(describeAutoScalingInstancesRequest)
      val autoScalingGroupName = describeAutoScalingInstancesResult.autoScalingInstances.asScala.head.autoScalingGroupName
      val describeAutoScalingGroupsRequest = DescribeAutoScalingGroupsRequest.builder
          .autoScalingGroupNames(autoScalingGroupName)
          .build()
      val describeAutoScalingGroupsResult = asgClient.describeAutoScalingGroups(describeAutoScalingGroupsRequest)
      val autoScalingGroup = describeAutoScalingGroupsResult.autoScalingGroups.asScala.head

      autoScalingGroup.tags.asScala.map { t => t.key -> t.value }.toMap
    }

    // `getInstanceId` may return null or throw an exception, depending what went wrong
    val result = for {
      maybeId <- Try(Option(EC2MetadataUtils.getInstanceId)).recoverWith {
        case e: SdkClientException => Failure(new RuntimeException("Running in DEV?  this may cause unnecessary delays due to timeouts outside of EC2: " + e.toString, e))
      }
      instanceId <- maybeId.toRight(new RuntimeException("no metadata available - this may cause delays due to timeouts when called in DEV")).toTry
      region <- region
      tags = withOneOffAsgClient(region, client => getTags(client, instanceId))
    } yield
      AwsIdentity(
        app = tags("App"),
        stack = tags("Stack"),
        stage = tags("Stage"),
        region = region
      )
    result

  }

  def region: Try[String] = {
    lazy val ec2Region = safeAwsOperation("Running in DEV? Don't call this - Timed out trying to identify the regionName of the instance") {
        EC2MetadataUtils.getEC2InstanceRegion
      }
    lazy val lambdaRegion = Option(System.getenv("AWS_DEFAULT_REGION"))
    lambdaRegion.map(Success(_)).getOrElse(ec2Region)
  }

  /**
   * Don't call this when running in DEV, as it will spend around 7 seconds timing out outside of EC2
   * @param credentials
   * @return
   */
  def whoAmI(
    credentials: => AwsCredentialsProvider
  ): Try[AppIdentity] = {
    val result = fromASGTags(credentials)
    result.foreach(result => logger.info(s"Detected the following AppIdentity: $result"))
    result
  }

}
