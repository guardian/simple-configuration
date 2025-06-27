package com.gu

import org.slf4j.LoggerFactory

object LambdaAppIdentity {
  private val logger = LoggerFactory.getLogger(this.getClass)

  private def getEnv(variableName: String): Option[String] =
    Option(System.getenv(variableName))

  def whoAmI(): Option[AppIdentity] =
    for {
      app <- getEnv("App")
      stack <- getEnv("Stack")
      stage <- getEnv("Stage")
      region <- getEnv("AWS_DEFAULT_REGION")
    } yield
      AwsIdentity(app = app, stack = stack, stage = stage, region = region)

}
