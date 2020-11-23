package com.gu.conf

import java.io.File

import com.typesafe.config.{Config, ConfigFactory}
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider

trait ConfigurationLocation {
  def load(credentials: => AwsCredentialsProvider): Config
}

case class FileConfigurationLocation(file: File) extends ConfigurationLocation {
  override def load(credentials: => AwsCredentialsProvider): Config =
    ConfigFactory.parseFile(file)
}

case class ResourceConfigurationLocation(resourceName: String)
    extends ConfigurationLocation {
  override def load(credentials: => AwsCredentialsProvider): Config =
    ConfigFactory.parseResources(resourceName)
}

case class ComposedConfigurationLocation(locations: List[ConfigurationLocation])
    extends ConfigurationLocation {
  override def load(credentials: => AwsCredentialsProvider): Config = {
    val aggregatedConfig =
      locations.map(_.load(credentials)).foldLeft(ConfigFactory.empty()) {
        case (agg, loadedConfig) => agg.withFallback(loadedConfig)
      }
    aggregatedConfig.resolve()
  }
}
