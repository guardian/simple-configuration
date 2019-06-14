package com.gu.conf

import java.io.File

import com.amazonaws.auth.AWSCredentialsProvider
import com.typesafe.config.{Config, ConfigFactory}

trait ConfigurationLocation {
  def load(credentials: => AWSCredentialsProvider): Config
}

case class FileConfigurationLocation(file: File) extends ConfigurationLocation {
  override def load(credentials: => AWSCredentialsProvider): Config = ConfigFactory.parseFile(file)
}

case class ResourceConfigurationLocation(resourceName: String) extends ConfigurationLocation {
  override def load(credentials: => AWSCredentialsProvider): Config = ConfigFactory.parseResources(resourceName)
}

case class ComposedConfigurationLocation(locations: List[ConfigurationLocation]) extends ConfigurationLocation {
  override def load(credentials: => AWSCredentialsProvider): Config = {
    val aggregatedConfig = locations.map(_.load(credentials)).foldLeft(ConfigFactory.empty()) {
      case (agg, loadedConfig) => agg.withFallback(loadedConfig)
    }
    aggregatedConfig.resolve()
  }
}
