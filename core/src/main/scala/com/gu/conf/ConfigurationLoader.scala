package com.gu.conf

import java.io.File

import com.amazonaws.auth.{AWSCredentialsProvider, DefaultAWSCredentialsProviderChain}
import com.gu.{AppIdentity, DevIdentity}
import com.typesafe.config.Config
import org.slf4j.LoggerFactory

object ConfigurationLoader {

  private val logger = LoggerFactory.getLogger(this.getClass)

  private def defaultDevLocation(identity: DevIdentity): ConfigurationLocation = {
    val home = System.getProperty("user.home")
    FileConfigurationLocation(new File(s"$home/.gu/${identity.app}.conf"))
  }

  def load(
    identity: AppIdentity,
    credentials: => AWSCredentialsProvider = DefaultAWSCredentialsProviderChain.getInstance
  )(locationFunction: PartialFunction[AppIdentity, ConfigurationLocation] = PartialFunction.empty): Config = {
    val getLocation = locationFunction.orElse[AppIdentity, ConfigurationLocation] {
      case devIdentity: DevIdentity => defaultDevLocation(devIdentity)
    }

    logger.info(s"Fetching configuration for $identity")

    getLocation(identity).load(credentials)
  }
}
