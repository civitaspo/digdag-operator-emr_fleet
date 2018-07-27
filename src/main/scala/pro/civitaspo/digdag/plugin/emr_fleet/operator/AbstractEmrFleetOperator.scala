package pro.civitaspo.digdag.plugin.emr_fleet.operator

import com.amazonaws.{ClientConfiguration, Protocol}
import com.amazonaws.auth.{AnonymousAWSCredentials, AWSCredentials, AWSCredentialsProvider, AWSStaticCredentialsProvider, BasicAWSCredentials, BasicSessionCredentials, EC2ContainerCredentialsProviderWrapper, EnvironmentVariableCredentialsProvider, SystemPropertiesCredentialsProvider}
import com.amazonaws.auth.profile.{ProfileCredentialsProvider, ProfilesConfigFile}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.regions.{DefaultAwsRegionProviderChain, Regions}
import com.amazonaws.services.elasticmapreduce.{AmazonElasticMapReduce, AmazonElasticMapReduceClientBuilder}
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest
import com.google.common.base.Optional
import io.digdag.client.config.{Config, ConfigException}
import io.digdag.spi.{OperatorContext, SecretProvider, TemplateEngine}
import io.digdag.util.BaseOperator
import org.slf4j.{Logger, LoggerFactory}

import scala.util.Try

abstract class AbstractEmrFleetOperator(
  context: OperatorContext,
  systemConfig: Config,
  templateEngine: TemplateEngine
) extends BaseOperator(context) {

  protected val logger: Logger = LoggerFactory.getLogger(this.getClass)
  protected val params: Config = request.getConfig.mergeDefault(
    request.getConfig.getNestedOrGetEmpty("emr_fleet")
  )
  protected val secrets: SecretProvider = context.getSecrets.getSecrets("emr_fleet")

  protected val isAllowedAuthMethodEnv: Boolean = systemConfig.get("allow_auth_method_env", classOf[Boolean], false)
  protected val isAllowedAuthMethodInstance: Boolean = systemConfig.get("allow_auth_method_instance", classOf[Boolean], false)
  protected val isAllowedAuthMethodProfile: Boolean = systemConfig.get("allow_auth_method_profile", classOf[Boolean], false)
  protected val isAllowedAuthMethodProperties: Boolean = systemConfig.get("allow_auth_method_properties", classOf[Boolean], false)
  protected val assumeRoleTimeoutSeconds: Int = systemConfig.get("assume_role_timeout_seconds", classOf[Int], 3600)

  protected val accessKeyId: Optional[String] = secrets.getSecretOptional("access_key_id")
  protected val secretAccessKey: Optional[String] = secrets.getSecretOptional("secret_access_key")
  protected val sessionToken: Optional[String] = secrets.getSecretOptional("session_token")
  protected val roleArn: Optional[String] = secrets.getSecretOptional("role_arn")
  protected val roleSessionName: String = secrets.getSecretOptional("role_session_name")
    .or(s"digdag-emr_fleet-${params.get("session_uuid", classOf[String])}")
  protected val httpProxy: SecretProvider = secrets.getSecrets("http_proxy")

  protected val authMethod: String = params.get("auth_method", classOf[String], "basic")
  protected val profileName: String = params.get("profile_name", classOf[String], "default")
  protected val profileFile: Optional[String] = params.getOptional("profile_file", classOf[String])
  protected val useHttpProxy: Boolean = params.get("use_http_proxy", classOf[Boolean], false)
  protected val region: Optional[String] = params.getOptional("region", classOf[String])
  protected val endpoint: Optional[String] = params.getOptional("endpoint", classOf[String])

  protected lazy val emr: AmazonElasticMapReduce = {
    val builder = AmazonElasticMapReduceClientBuilder.standard()
      .withClientConfiguration(clientConfiguration)
      .withCredentials(credentialsProvider)

    if (region.isPresent && endpoint.isPresent) {
      val ec = new EndpointConfiguration(endpoint.get(), region.get())
      builder.setEndpointConfiguration(ec)
    }
    else if (region.isPresent && !endpoint.isPresent) {
      builder.setRegion(region.get())
    }
    else if (!region.isPresent && endpoint.isPresent) {
      val r = Try(new DefaultAwsRegionProviderChain().getRegion).getOrElse(Regions.DEFAULT_REGION.getName)
      val ec = new EndpointConfiguration(endpoint.get(), r)
      builder.setEndpointConfiguration(ec)
    }

    builder.build()
  }

  protected def newEmptyParams: Config = {
    request.getConfig.getFactory.create()
  }

  private def credentialsProvider: AWSCredentialsProvider = {
    if (!roleArn.isPresent) return standardCredentialsProvider
    assumeRoleCredentialsProvider(standardCredentialsProvider)
  }

  private def standardCredentialsProvider: AWSCredentialsProvider = {
    authMethod match {
      case "basic" => basicAuthMethodAWSCredentialsProvider
      case "env" => envAuthMethodAWSCredentialsProvider
      case "instance" => instanceAuthMethodAWSCredentialsProvider
      case "profile" => profileAuthMethodAWSCredentialsProvider
      case "properties" => propertiesAuthMethodAWSCredentialsProvider
      case "anonymous" => anonymousAuthMethodAWSCredentialsProvider
      case "session" => sessionAuthMethodAWSCredentialsProvider
      case _ =>
        throw new ConfigException(s"""auth_method: "$authMethod" is not supported. available `auth_method`s are "basic", "env", "instance", "profile", "properties", "anonymous", or "session".""")
    }
  }

  private def assumeRoleCredentialsProvider(
    credentialsProviderToAssumeRole: AWSCredentialsProvider
  ): AWSCredentialsProvider = {
    // TODO: require EndpointConfiguration so on ?
    val sts = AWSSecurityTokenServiceClientBuilder.standard()
      .withClientConfiguration(clientConfiguration)
      .withCredentials(credentialsProviderToAssumeRole)
      .build()

    val role = sts.assumeRole(new AssumeRoleRequest()
      .withRoleArn(roleArn.get())
      .withDurationSeconds(assumeRoleTimeoutSeconds)
      .withRoleSessionName(roleSessionName)
    )
    val credentials = new BasicSessionCredentials(
      role.getCredentials.getAccessKeyId,
      role.getCredentials.getSecretAccessKey,
      role.getCredentials.getSessionToken
    )
    new AWSStaticCredentialsProvider(credentials)
  }

  private def basicAuthMethodAWSCredentialsProvider: AWSCredentialsProvider = {
    if (!accessKeyId.isPresent) throw new ConfigException(s"""`access_key_id` must be set when `auth_method` is "$authMethod".""")
    if (!secretAccessKey.isPresent) throw new ConfigException(s"""`secret_access_key` must be set when `auth_method` is "$authMethod".""")
    val credentials: AWSCredentials = new BasicAWSCredentials(accessKeyId.get(), secretAccessKey.get())
    new AWSStaticCredentialsProvider(credentials)
  }

  private def envAuthMethodAWSCredentialsProvider: AWSCredentialsProvider = {
    if (!isAllowedAuthMethodEnv) throw new ConfigException(s"""auth_method: "$authMethod" is not allowed.""")
    new EnvironmentVariableCredentialsProvider
  }

  private def instanceAuthMethodAWSCredentialsProvider: AWSCredentialsProvider = {
    if (!isAllowedAuthMethodInstance) throw new ConfigException(s"""auth_method: "$authMethod" is not allowed.""")
    // NOTE: combination of InstanceProfileCredentialsProvider and ContainerCredentialsProvider
    new EC2ContainerCredentialsProviderWrapper
  }

  private def profileAuthMethodAWSCredentialsProvider: AWSCredentialsProvider = {
    if (!isAllowedAuthMethodProfile) throw new ConfigException(s"""auth_method: "$authMethod" is not allowed.""")
    if (!profileFile.isPresent) return new ProfileCredentialsProvider(profileName)
    val pf: ProfilesConfigFile = new ProfilesConfigFile(profileFile.get())
    new ProfileCredentialsProvider(pf, profileName)
  }

  private def propertiesAuthMethodAWSCredentialsProvider: AWSCredentialsProvider = {
    if (!isAllowedAuthMethodProperties) throw new ConfigException(s"""auth_method: "$authMethod" is not allowed.""")
    new SystemPropertiesCredentialsProvider()
  }

  private def anonymousAuthMethodAWSCredentialsProvider: AWSCredentialsProvider = {
    val credentials: AWSCredentials = new AnonymousAWSCredentials
    new AWSStaticCredentialsProvider(credentials)
  }

  private def sessionAuthMethodAWSCredentialsProvider: AWSCredentialsProvider = {
    if (!accessKeyId.isPresent) throw new ConfigException(s"""`access_key_id` must be set when `auth_method` is "$authMethod".""")
    if (!secretAccessKey.isPresent) throw new ConfigException(s"""`secret_access_key` must be set when `auth_method` is "$authMethod".""")
    if (!sessionToken.isPresent) throw new ConfigException(s"""`session_token` must be set when `auth_method` is "$authMethod".""")
    val credentials: AWSCredentials = new BasicSessionCredentials(accessKeyId.get(), secretAccessKey.get(), sessionToken.get())
    new AWSStaticCredentialsProvider(credentials)
  }

  private def clientConfiguration: ClientConfiguration = {
    if (!useHttpProxy) return new ClientConfiguration()

    val host: String = httpProxy.getSecret("host")
    val port: Optional[String] = httpProxy.getSecretOptional("port")
    val protocol: Protocol = httpProxy.getSecretOptional("scheme").or("https") match {
      case "http" => Protocol.HTTP
      case "https" => Protocol.HTTPS
      case _ => throw new ConfigException("""`emr_fleet.http_proxy.scheme` must be "http" or "https".""")
    }
    val user: Optional[String] = httpProxy.getSecretOptional("user")
    val password: Optional[String] = httpProxy.getSecretOptional("password")

    val cc = new ClientConfiguration()
      .withProxyHost(host)
      .withProtocol(protocol)

    if (port.isPresent) cc.setProxyPort(port.get().toInt)
    if (user.isPresent) cc.setProxyUsername(user.get())
    if (password.isPresent) cc.setProxyPassword(password.get())

    cc
  }

}
