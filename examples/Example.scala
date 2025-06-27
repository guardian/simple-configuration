import com.gu.conf.{ConfigurationLoader, ResourceConfigurationLocation, S3ConfigurationLoader, S3ConfigurationLocation, SSMConfigurationLoader, SSMConfigurationLocation}
import com.gu._
import com.typesafe.config.Config
import play.api.mvc.EssentialFilter
import play.api.routing.Router
import play.api.{Application, ApplicationLoader, BuiltInComponentsFromContext, Configuration, LoggerConfigurator, Mode}
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider

import scala.util.{Success, Try}

/*
This file has some up to date examples of how to use simple-configuration.

- ExamplePlayAppLoader for a Play application and
- ExampleLambdaConfigLoader for a Lambda function.
- ExampleUseClasspathConfigLocally for a lambda that uses a classpath resource instead of a file outside of AWS.

 */

/**
 * This example is what you would use in a Play application to load configuration from S3.
 *
 * In application.conf, ensure it contains the line:
 *
 * play.application.loader = "ExamplePlayAppLoader"
 */
class ExamplePlayAppLoader extends ApplicationLoader {

  val appName = "support-frontend"
  val credentialsProvider = DefaultCredentialsProvider.create()

  override def load(context: ApplicationLoader.Context): Application = {
    val isDev = context.environment.mode == Mode.Dev
    val triedApplication =
      for {
        identity <- if (isDev)
          Success(DevIdentity(appName))
        else
          Ec2AppIdentity.whoAmI(credentialsProvider)
        config <- Try(S3ConfigurationLoader.load(identity, credentialsProvider))
        playConfig = Configuration(config).withFallback(context.initialConfiguration)
        newContext = context.copy(initialConfiguration = playConfig.withFallback(context.initialConfiguration))
      } yield new BuiltInComponentsFromContext(newContext) {
        override def router: Router = ???
        override def httpFilters: Seq[EssentialFilter] = ???
      }.application

    triedApplication.get
  }
}

/**
 * this example for a non play app would load from SSM
 */
object ExampleLambdaConfigLoader {

  private val appName = "my-first-lambda"

  // Lambda entry point, loads from SSM
  def handleRequest() = {
    val config = loadConfig(LambdaAppIdentity.whoAmI().getOrElse(throw new RuntimeException("Not running in a Lambda environment")))

    println(config)
  }

  // When running locally, if you want to use shared SSM DEV config, create an AwsIdentity manually
  def localSharedRun() = {
    val config = loadConfig(AwsIdentity(appName, "support", "DEV", "eu-west-1"))

    println(config)
  }

  // When running locally, if you want to use your own config, create a DevIdentity
  def localCustomRun() = {
    val config = loadConfig(DevIdentity(appName))

    println(config)
  }

  // this uses the standard SSM location or local file ~/.gu/$app.conf
  private def loadConfig(appIdentity: AppIdentity): Config =
    SSMConfigurationLoader.load(appIdentity)

}

/**
 * this example for a non play app would also load from SSM, however for tests it will load from a classpath resource
 */
object ExampleUseClasspathConfigLocally {

  private val appName = "sf-move-subscriptions-api"

  // Lambda entry point, loads from SSM
  def handleRequest() = {
    val config = loadConfig(LambdaAppIdentity.whoAmI().getOrElse(throw new RuntimeException("Not running in a Lambda environment")))

    println(config)
  }

  // When running tests, the DevIdentity makes it read from a classpath resource
  def test() = {
    val config = loadConfig(DevIdentity(appName))

    println(config)
  }

  // this falls back from SSM to a classpath resource when running locally
  private def loadConfig(appIdentity: AppIdentity): Config =
    ConfigurationLoader.load(appIdentity) {
      case identity: AwsIdentity => SSMConfigurationLocation.default(identity)
      case DevIdentity(myApp) => ResourceConfigurationLocation(s"${myApp}-dev.conf")
    }

}
