# simple-configuration

[![simple-configuration-core Scala version support](https://index.scala-lang.org/guardian/simple-configuration/simple-configuration-core/latest-by-scala-version.svg?platform=jvm)](https://index.scala-lang.org/guardian/simple-configuration/simple-configuration-core)
[![Release](https://github.com/guardian/simple-configuration/actions/workflows/release.yml/badge.svg)](https://github.com/guardian/simple-configuration/actions/workflows/release.yml)

_A configuration library without any magic_

# Publishing a new release

This repo uses [`gha-scala-library-release-workflow`](https://github.com/guardian/gha-scala-library-release-workflow)
to automate publishing releases (both full & preview releases) - see
[**Making a Release**](https://github.com/guardian/gha-scala-library-release-workflow/blob/main/docs/making-a-release.md).

## Goal
This library will help you find and load the configuration of your application from the SSM parameter store.

It relies on [lightbend's configuration library](https://github.com/typesafehub/config), SSM SDK and EC2 SDK.

Although SSM is preferred, apps can load their configuration from S3 instead.

## Usage

In your `build.sbt`:
```scala
libraryDependencies += "com.gu" %% "simple-configuration-ssm" % "1.5.7"
```
and if you're running on EC2 and want to read the app identity from the ASG tags:
```scala
libraryDependencies += "com.gu" %% "simple-configuration-ec2" % "1.5.7"
```
S3 support is available by using 
```scala
libraryDependencies += "com.gu" %% "simple-configuration-s3" % "1.5.7"
```

Then in your code follow one of the examples:

[examples/Example.scala](examples/Example.scala)

Let's look in detail at what's happening here.

### DevIdentity

A DevIdentity can locate a local configuration file, classpath resource, or other location.
```scala
case class DevIdentity(app: String) extends AppIdentity
```
Create one manually if you know you need to use local config e.g. in a test.

### AwsIdentity
An AwsIdentity can locate configuration in the standard AWS locations.

```scala
case class AwsIdentity(
  app: String,
  stack: String,
  stage: String,
  region: String
) extends AppIdentity
```

If you know you are running in AWS, you can auto-detect.  Warning: locally, attempting to use Ec2AppIdentity hangs for 7 seconds.

The `Ec2AppIdentity.whoAmI` returns an `AwsIdentity` based on the tags (`App`, `Stack`, `Stage`) automatically set via riff-raff on the cloudformation stack.   It will need the appropriate IAM permission to be able to query the ec2 API (see [IAM paragraph below](#iam-permissions))

The `LambdaAppIdentity.whoAmI` uses the environment variables that [CDK sets](https://github.com/guardian/cdk/blob/074dca40986e4a667561e75d51b1deb841584cf7/src/constructs/lambda/lambda.ts#L134-L138) on your lambda (`App`, `Stack`, `Stage`, and the one provided by AWS `AWS_DEFAULT_REGION`).

### SSMConfigurationLoader.load

This function will load your configuration from SSM, or locally if you are in dev mode.
It will use the identity to understand where the app is running, and load the configuration accordingly. It will of course need the appropriate IAM permission, as defined in the [paragraph below](#iam-permissions).

By default the configuration are loaded from the following locations:

`~/.gu/${identity.app}.conf` for the local file if you are in dev mode (AppIdentity is of type DevIdentity)

`/${identity.stage}/${identity.stack}/${identity.app}/*` if you're loading it from the SSM parameter store

If wish to use S3, `S3ConfigurationLoader.load` will load from the following location:

`s3://${identity.app}-dist/${identity.stage}/${identity.stack}/${identity.app}/${identity.app}.conf`

### Customised config locations

If you want to load from a custom location, for example a classpath resource, or a different S3 location, you can use the `ConfigurationLoader.load` method.

```scala
def load(
  identity: AppIdentity,
  credentials: => AWSCredentialsProvider = DefaultAWSCredentialsProviderChain.getInstance
)(locationFunction: PartialFunction[AppIdentity, ConfigurationLocation] = PartialFunction.empty): Config
```

The only parameter you need to provide is the identity, other parameters will use a sensible default.

`identity`: identity is a parameter of type `AppIdentity` that describes your application (name, stack, stage, awsRegion). See [above paragraph](#appidentitywhoami-optional) about AppIdentity.whoAmI

`credentials`: These are the AWS credentials that will be used to load your configuration from AWS. The default behaviour should be enough, but if you wanted to customise the way the credentials are picked you could pass it as a parameter. Note that it's a pass-by-name parameter so the content won't be evaluated unless needed. The default behaviour when running locally is to load the configuration from a local file, so credentials won't be evaluated in that case.

`locationFunction`: This function is a way to customise where to load the configuration depending on the environment. For instance if your configuration is in the same place for two different stacks or if you're using the same configuration file for multiple apps (multi-module project) you could override the path that will be used. It's a partial function, so it's thought to be used as pattern matching on the `AppIdentity` you provided. You can see an [example below](#examples) or you can see what return types are possible in the [Location Types paragraph](#location-types).

## Examples

### Provide your own credentials
```scala
val identity = AppIdentity.whoAmI(defaultAppName = "mobile-apps-api", credentials = myOwnCredentials)
val config = ConfigurationLoader.load(identity, credentials = myOwnCredentials) {
  case identity: AwsIdentity => S3ConfigurationLocation.default(identity)
}
```

### Custom S3 location
See [Location Types](#location-types) for a list of all the location types.

```scala
val config = ConfigurationLoader.load(identity) {
  case AwsIdentity(app, "stack1", stage, _) => S3ConfigurationLocation("mybucket", s"somepath/$stage/$app.conf")
  case DevIdentity(myApp) => ResourceConfigurationLocation(s"localdev-${myApp}.conf")
}
```

### Custom SSM location
See [Location Types](#location-types) for a list of all the location types.

```scala
val config = ConfigurationLoader.load(identity) {
  case AwsIdentity(app, "stack1", stage, _) => SSMConfigurationLocation(s"/myAPI/$stage")
  case DevIdentity(myApp) => ResourceConfigurationLocation(s"localdev-${myApp}.conf")
}
```

### Play application with Compile time Dependency Injection
```scala
import play.api.Configuration
import com.gu.AppIdentity
import com.gu.conf.ConfigurationLoader


class MyApplicationLoader extends ApplicationLoader {
  override def load(context: Context): Application = {
    LoggerConfigurator(context.environment.classLoader) foreach { _.configure(context.environment) }
    val identity = AppIdentity.whoAmI(defaultAppName = "myApp")
    val loadedConfig = ConfigurationLoader.load(identity) {
      case identity: AwsIdentity => S3ConfigurationLocation.default(identity)
    }
    val newContext = context.copy(initialConfiguration = Configuration(loadedConfig).withFallback(context.initialConfiguration))
    (new BuiltInComponentsFromContext(newContext) with AppComponents).application
  }
}
```

Here's what we're doing above:
 - Initialise the logs (standard behaviour when using compile time dependencies with Play)
 - Auto detect the application identity
 - Load the Lightbend (Typesafe) Config
 - Wrap the loaded config in a Play configuration and concatenate the initial play configuration (application.conf) with what has been loaded from S3 or locally
 - Use the resulting configuration to instantiate the Play app

## Location types

When providing your own mapping between `AppIdentity` and location, you can specify five location types:

- `S3ConfigurationLocation(bucket: String, path: String, region: String)`
- `SSMConfigurationLocation(path: String, region: String)`
- `FileConfigurationLocation(file: File)`
- `ResourceConfigurationLocation(resourceName: String)`
- `ComposedConfigurationLocation(locations: List[ConfigurationLocation])`

### S3ConfigurationLocation
This will help `ConfigurationLoader.load` locate the file on an S3 bucket. You must provide the bucket name and the complete path to the file. The region defaults to the autodetected one, but you can override it if you please. The file fetched from S3 is expected to be a valid typesafe config file (HOCON, json or properties)

### SSMConfigurationLocation
This will help `ConfigurationLoader.load` select all the parameters in the SSM parameter store. The path must start with a / and should not finish with one. Here's a path example `"/this/is/valid"`.
Parameters can be encrypted and are named following this convention: `"/path/my.typesafe.config.key"`. Here's an example: `/PROD/mobile/mobile-fronts/apis.capi.timeout`

### FileConfigurationLocation
This will be useful when loading a file ouside of your classpath. Typically, a configuration that can contain secrets and that shouldn't be committed to the repository. This is used by default when in DEV mode and points to `~/.gu/${identity.app}.conf`

### ResourceConfigurationLocation
This will load a configuration file from within your classpath. Typically a file under the `resource` folder of your project. It is useful if your configuration can be committed to your repo and is directly accessible from the classpath. 

### ComposedConfigurationLocation
Composes a list of `ConfigurationLocation`s. Configurations earlier in the list take precedence over ones later in the list.

## IAM permissions
### When using AppIdentity.whoAmI on an EC2 instance
_Note that you won't need it for a lambda as these are passed as environment variables_
```json
{
    "Effect": "Allow",
    "Action": [ 
      "autoscaling:DescribeAutoScalingInstances",
      "autoscaling:DescribeAutoScalingGroups",
      "ec2:DescribeTags"
    ],
    "Resource": "*"
}
```
### When loading the configuration from S3
```json
{
    "Effect": "Allow",
    "Action": "s3:GetObject",
    "Resource": "arn:aws:s3:::mybucket/*"
}
```
### When loading the configuration from SSM
Note that CDK GuLambdaFunction automatically gives [suitable permissions](https://github.com/guardian/cdk/blob/074dca40986e4a667561e75d51b1deb841584cf7/src/constructs/iam/policies/parameter-store-read.ts#L10) to the lambda.
```json
{
    "Effect": "Allow",
    "Action": "ssm:GetParametersByPath",
    "Resource": "arn:aws:ssm:${AWS::Region}:${AWS::AccountId}:parameter/path/used"
}
```
or when using cloudformation
```yaml
  - Action:
    - ssm:GetParametersByPath
    Effect: Allow
    Resource: !Sub arn:aws:ssm:${AWS::Region}:${AWS::AccountId}:parameter/${Stage}/${Stack}/${App}
```
