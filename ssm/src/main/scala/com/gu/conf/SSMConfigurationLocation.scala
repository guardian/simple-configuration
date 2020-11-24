package com.gu.conf

import com.gu.{AppIdentity, AwsIdentity}
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest

import scala.annotation.tailrec
import scala.collection.JavaConverters._

case class SSMConfigurationLocation(
  path: String,
  region: String = AppIdentity.region
) extends ConfigurationLocation {

  private val logger = LoggerFactory.getLogger(this.getClass)

  override def load(credentials: => AwsCredentialsProvider): Config = {
    logger.info(s"Attempting to load configuration from SSM for path = $path and region = $region")
    val ssmClient = SsmClient.builder
      .credentialsProvider(credentials)
      .region(Region.of(region))
      .build()

    @tailrec
    def recursiveParamFetch(parameters: Map[String, String], nextToken: Option[String]): Map[String, String] = {

      val parameterRequest = GetParametersByPathRequest.builder
        .withDecryption(true)
        .path(path)
        .recursive(true)
        .nextToken(nextToken.orNull)
        .build()

      val result = ssmClient.getParametersByPath(parameterRequest)

      val results = result.parameters.asScala.map { p =>
        p.name.replaceFirst(s"$path/", "").replaceAll("/", ".") -> p.value
      }.toMap

      Option(result.nextToken) match {
        case Some(next) => recursiveParamFetch(parameters ++ results, Some(next))
        case None => parameters ++ results
      }
    }

    val parameters = recursiveParamFetch(Map.empty, None)

    ssmClient.close()

    ConfigFactory.parseMap(parameters.asJava, "AWS SSM parameters")
  }
}

object SSMConfigurationLocation {
  def default(identity: AwsIdentity): ConfigurationLocation = {
    SSMConfigurationLocation(s"/${identity.stage}/${identity.stack}/${identity.app}")
  }
}
