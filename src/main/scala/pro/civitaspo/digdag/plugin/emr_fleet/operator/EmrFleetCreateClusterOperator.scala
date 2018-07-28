package pro.civitaspo.digdag.plugin.emr_fleet.operator

import java.util
import java.net.URI

import com.amazonaws.services.elasticmapreduce.model.{EbsBlockDeviceConfig, EbsConfiguration, InstanceFleetConfig, InstanceFleetProvisioningSpecifications, InstanceFleetType, InstanceTypeConfig, SpotProvisioningSpecification, SpotProvisioningTimeoutAction, VolumeSpecification}
import com.google.common.base.Optional
import io.digdag.client.config.Config
import io.digdag.spi.{OperatorContext, TaskResult, TemplateEngine}

import scala.collection.JavaConverters._

class EmrFleetCreateClusterOperator(
  context: OperatorContext,
  systemConfig: Config,
  templateEngine: TemplateEngine
) extends AbstractEmrFleetOperator(context, systemConfig, templateEngine) {

  val clusterName: String = params.get("name", classOf[String], s"digdag-${params.get("session_uuid", classOf[String])}")
  val tags: Map[String, String] = params.get("tags", classOf[util.Map[String, String]]).asScala.toMap
  val releaseLabel: String = params.get("release_label", classOf[String], "emr-5.16.0")
  val customAmiId: Optional[String] = params.getOptional("custom_ami_id", classOf[String])
  val masterSecurityGroups: Seq[String] = params.getListOrEmpty("master_security_groups", classOf[String]).asScala
  val slaveSecurityGroups: Seq[String] = params.getListOrEmpty("slave_security_groups", classOf[String]).asScala
  val sshKey: Optional[String] = params.getOptional("ssh_key", classOf[String])
  val subnetIds: Seq[String] = params.getListOrEmpty("subnet_ids", classOf[String]).asScala
  val availabilityZones: Seq[String] = params.getListOrEmpty("availability_zones", classOf[String]).asScala
  val spotSpec: Config = params.getNestedOrGetEmpty("spot_specs")
  val masterFleet: Config = params.getNested("master_fleet")
  val coreFleet: Config = params.getNested("core_fleet")
  val taskFleet: Config = params.getNestedOrGetEmpty("task_fleet")
  val logUri: Optional[URI] = params.getOptional("log_uri", classOf[URI])
  val additionalInfo: Optional[String] = params.getOptional("additional_info", classOf[String])
  val isVisible: Boolean = params.get("visible", classOf[Boolean], true)
  val securityConfiguration: Optional[String] = params.getOptional("security_configuration", classOf[String])
  val instanceProfile: String = params.get("instance_profile", classOf[String], "EMR_EC2_DefaultRole")
  val serviceRole: String = params.get("service_role", classOf[String], "EMR_DefaultRole")
  val applications: Seq[String] = params.getListOrEmpty("applications", classOf[String]).asScala
  val configurations: Seq[Config] = params.getListOrEmpty("configurations", classOf[Config]).asScala
  val bootstrapActions: Seq[Config] = params.getListOrEmpty("bootstrap_actions", classOf[Config]).asScala
  val keepAliveWhenNoSteps: Boolean = params.get("keep_alive_when_no_steps", classOf[Boolean], true)
  val terminationProtected: Boolean = params.get("termination_protected", classOf[Boolean], false)

  def configureEbs(ebs: Config): EbsConfiguration = {
    val isOptimized: Boolean = ebs.get("optimized", classOf[Boolean], true)
    val iops: Optional[Int] = ebs.getOptional("iops", classOf[Int])
    val size: Int = ebs.get("size", classOf[Int], 256)
    val volumeType: String = ebs.get("type", classOf[String], "gp2")
    val volumesPerInstance: Int = ebs.get("volumes_per_instance", classOf[Int], 1)

    new EbsConfiguration()
      .withEbsOptimized(isOptimized)
      .withEbsBlockDeviceConfigs(new EbsBlockDeviceConfig()
          .withVolumesPerInstance(volumesPerInstance)
          .withVolumeSpecification(new VolumeSpecification()
              .withIops(iops.orNull)
              .withSizeInGB(size)
              .withVolumeType(volumeType)
          )
      )
  }

  override def runTask(): TaskResult = null
}
