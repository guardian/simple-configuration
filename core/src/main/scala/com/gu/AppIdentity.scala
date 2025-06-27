package com.gu

sealed trait AppIdentity

case class AwsIdentity(
  app: String,
  stack: String,
  stage: String,
  region: String
) extends AppIdentity

case class DevIdentity(app: String) extends AppIdentity
