package com.gu.conf

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.regions.Regions
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathRequest
import com.gu.AwsIdentity
import com.typesafe.config.{Config, ConfigFactory}
import scala.collection.JavaConverters._

import scala.annotation.tailrec

case class SSMConfigurationLocation(
  path: String,
  region: String = Regions.getCurrentRegion.getName
) extends ConfigurationLocation {

  override def load(credentials: => AWSCredentialsProvider): Config = {
    val ssmClient = {
      AWSSimpleSystemsManagementClientBuilder
        .standard()
        .withCredentials(credentials)
        .withRegion(region)
        .build()
    }

    @tailrec
    def recursiveParamFetch(parameters: Map[String, String], nextToken: Option[String]): Map[String, String] = {

      val parameterRequest = new GetParametersByPathRequest()
        .withWithDecryption(true)
        .withPath(path)
        .withRecursive(true)
        .withNextToken(nextToken.orNull)

      val result = ssmClient.getParametersByPath(parameterRequest)

      val results = result.getParameters.asScala.map {
        p => p.getName.replaceFirst(s"$path/", "").replaceAll("/", ".") -> p.getValue
      }.toMap

      Option(result.getNextToken) match {
        case Some(next) => recursiveParamFetch(parameters ++ results, Some(next))
        case None => parameters ++ results
      }
    }

    val parameters = recursiveParamFetch(Map.empty, None)

    ssmClient.shutdown()

    ConfigFactory.parseMap(parameters.asJava, "AWS SSM parameters")
  }
}

object SSMConfigurationLocation {
  def default(identity: AwsIdentity): ConfigurationLocation = {
    SSMConfigurationLocation(s"/${identity.stage}/${identity.stack}/${identity.app}")
  }
}
