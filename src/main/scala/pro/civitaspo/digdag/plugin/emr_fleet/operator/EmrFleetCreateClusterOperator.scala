package pro.civitaspo.digdag.plugin.emr_fleet.operator

import com.amazonaws.services.elasticmapreduce.model.{Application, BootstrapActionConfig, ClusterState, Configuration, EbsBlockDeviceConfig, EbsConfiguration, InstanceFleetConfig, InstanceFleetProvisioningSpecifications, InstanceFleetType, InstanceTypeConfig, JobFlowInstancesConfig, PlacementType, RunJobFlowRequest, ScriptBootstrapActionConfig, SpotProvisioningSpecification, SpotProvisioningTimeoutAction, Tag, VolumeSpecification}
import com.amazonaws.services.elasticmapreduce.model.ClusterState.{RUNNING, TERMINATED, TERMINATED_WITH_ERRORS, TERMINATING, WAITING}
import com.amazonaws.services.elasticmapreduce.model.InstanceFleetType.{CORE, MASTER, TASK}
import com.google.common.base.Optional
import com.google.common.collect.ImmutableList
import io.digdag.client.config.{Config, ConfigKey}
import io.digdag.spi.{OperatorContext, TaskResult, TemplateEngine}
import io.digdag.util.DurationParam

import scala.collection.JavaConverters._

class EmrFleetCreateClusterOperator(
  context: OperatorContext,
  systemConfig: Config,
  templateEngine: TemplateEngine
) extends AbstractEmrFleetOperator(context, systemConfig, templateEngine) {

  val clusterName: String = params.get("name", classOf[String], s"digdag-${params.get("session_uuid", classOf[String])}")
  val tags: Map[String, String] = params.getMapOrEmpty("tags", classOf[String], classOf[String]).asScala.toMap
  val releaseLabel: String = params.get("release_label", classOf[String], "emr-5.16.0")
  val customAmiId: Optional[String] = params.getOptional("custom_ami_id", classOf[String])
  val masterSecurityGroups: Seq[String] = params.getListOrEmpty("master_security_groups", classOf[String]).asScala
  val slaveSecurityGroups: Seq[String] = params.getListOrEmpty("slave_security_groups", classOf[String]).asScala
  val sshKey: Optional[String] = params.getOptional("ssh_key", classOf[String])
  val subnetIds: Seq[String] = params.getListOrEmpty("subnet_ids", classOf[String]).asScala
  val availabilityZones: Seq[String] = params.getListOrEmpty("availability_zones", classOf[String]).asScala
  val spotSpec: Config = params.getNestedOrGetEmpty("spot_spec")
  val masterFleet: Config = params.getNested("master_fleet")
  val coreFleet: Config = params.getNested("core_fleet")
  val taskFleet: Config = params.getNestedOrGetEmpty("task_fleet")
  val logUri: Optional[String] = params.getOptional("log_uri", classOf[String])
  val additionalInfo: Optional[String] = params.getOptional("additional_info", classOf[String])
  val isVisible: Boolean = params.get("visible", classOf[Boolean], true)
  val securityConfiguration: Optional[String] = params.getOptional("security_configuration", classOf[String])
  val instanceProfile: String = params.get("instance_profile", classOf[String], "EMR_EC2_DefaultRole")
  val serviceRole: String = params.get("service_role", classOf[String], "EMR_DefaultRole")
  val applications: Seq[String] = params.getListOrEmpty("applications", classOf[String]).asScala
  val applicationConfigurations: Seq[Config] = params.getListOrEmpty("configurations", classOf[Config]).asScala
  val bootstrapActions: Seq[Config] = params.getListOrEmpty("bootstrap_actions", classOf[Config]).asScala
  val keepAliveWhenNoSteps: Boolean = params.get("keep_alive_when_no_steps", classOf[Boolean], true)
  val terminationProtected: Boolean = params.get("termination_protected", classOf[Boolean], false)
  val waitAvailableState: Boolean = params.get("wait_available_state", classOf[Boolean], true)
  val waitTimeoutDuration: DurationParam = params.get("wait_timeout_duration", classOf[DurationParam], DurationParam.parse("45m"))

  lazy val instanceFleetProvisioningSpecifications: InstanceFleetProvisioningSpecifications = {
    val blockDurationMinutes: Optional[Int] = spotSpec.getOptional("block_duration_minutes", classOf[Int])
    val timeoutAction: String = spotSpec.get("timeout_action", classOf[String], "TERMINATE_CLUSTER")
    val timeoutDurationMinutes: Int = spotSpec.get("timeout_duration_minutes", classOf[Int], 45)

    val s = new SpotProvisioningSpecification()
    if (blockDurationMinutes.isPresent) s.setBlockDurationMinutes(blockDurationMinutes.get())
    s.setTimeoutAction(SpotProvisioningTimeoutAction.fromValue(timeoutAction))
    s.setTimeoutDurationMinutes(timeoutDurationMinutes)

    new InstanceFleetProvisioningSpecifications().withSpotSpecification(s)
  }

  def masterFleetConfiguration: InstanceFleetConfig = {
    val name: String = masterFleet.get("name", classOf[String], "master instance fleet")
    val useSpotInstance: Boolean = masterFleet.get("use_spot_instance", classOf[Boolean], true)
    val defaultBidPercentage: Double = masterFleet.get("bid_percentage", classOf[Double], 100.0)
    val candidates: Seq[Config] = masterFleet.getList("candidates", classOf[Config]).asScala

    val c = new InstanceFleetConfig()
    c.setInstanceFleetType(MASTER)
    c.setName(name)
    c.setLaunchSpecifications(instanceFleetProvisioningSpecifications)
    if (useSpotInstance) {
      c.setTargetSpotCapacity(1)
      c.setTargetOnDemandCapacity(0)
    }
    else {
      c.setTargetSpotCapacity(0)
      c.setTargetOnDemandCapacity(1)
    }
    c.setInstanceTypeConfigs(seqAsJavaList(candidates.map(configureCandidate(_, defaultBidPercentage))))
    c
  }

  def configureSlaveFleet(fleetType: InstanceFleetType, fleetConfiguration: Config): InstanceFleetConfig = {
    val name: String = fleetConfiguration.get("name", classOf[String], s"${fleetType.toString.toLowerCase} instance fleet")
    val targetCapacity: Int = fleetConfiguration.get("target_capacity", classOf[Int])
    val defaultBidPercentage: Double = masterFleet.get("bid_percentage", classOf[Double], 100.0)
    val candidates: Seq[Config] = fleetConfiguration.getList("candidates", classOf[Config]).asScala

    new InstanceFleetConfig()
      .withInstanceFleetType(fleetType)
      .withName(name)
      .withLaunchSpecifications(instanceFleetProvisioningSpecifications)
      .withTargetSpotCapacity(targetCapacity)
      .withInstanceTypeConfigs(seqAsJavaList(candidates.map(configureCandidate(_, defaultBidPercentage))))
  }

  def configureCandidate(candidate: Config, defaultBidPercentage: Double): InstanceTypeConfig = {
    val bidPrice: Optional[String] = candidate.getOptional("bid_price", classOf[String])
    val bidPercentage: Double = candidate.get("bid_percentage", classOf[Double], defaultBidPercentage)
    val instanceType: String = candidate.get("instance_type", classOf[String])
    val applicationConfigurations: Seq[Config] = candidate.getListOrEmpty("configurations", classOf[Config]).asScala
    val ebs: Config = candidate.getNestedOrGetEmpty("ebs")
    val spotUnits: Int = candidate.get("spot_units", classOf[Int], 1)

    val c = new InstanceTypeConfig()
    if (bidPrice.isPresent) c.setBidPrice(bidPrice.get())
    c.setBidPriceAsPercentageOfOnDemandPrice(bidPercentage)
    c.setInstanceType(instanceType)
    c.setConfigurations(seqAsJavaList(applicationConfigurations.map(configureApplicationConfiguration)))
    c.setEbsConfiguration(configureEbs(ebs))
    c.setWeightedCapacity(spotUnits)
    c
  }

  def configureEbs(ebs: Config): EbsConfiguration = {
    val isOptimized: Boolean = ebs.get("optimized", classOf[Boolean], true)
    val iops: Optional[Int] = ebs.getOptional("iops", classOf[Int])
    val size: Int = ebs.get("size", classOf[Int], 256)
    val volumeType: String = ebs.get("type", classOf[String], "gp2")
    val volumesPerInstance: Int = ebs.get("volumes_per_instance", classOf[Int], 1)

    val volumeSpecification = new VolumeSpecification()
    if (iops.isPresent) volumeSpecification.setIops(iops.get())
    volumeSpecification.setSizeInGB(size)
    volumeSpecification.setVolumeType(volumeType)

    new EbsConfiguration()
      .withEbsOptimized(isOptimized)
      .withEbsBlockDeviceConfigs(new EbsBlockDeviceConfig()
          .withVolumesPerInstance(volumesPerInstance)
          .withVolumeSpecification(volumeSpecification)
      )
  }

  def configureApplicationConfiguration(applicationConfiguration: Config): Configuration = {
    val ac = applicationConfiguration  // to shorten var name
    val classification: String = ac.get("classification", classOf[String])
    val properties: Map[String, String] = ac.getMapOrEmpty("properties", classOf[String], classOf[String]).asScala.toMap
    val configurations: Seq[Config] = ac.getListOrEmpty("configurations", classOf[Config]).asScala

    val c = new Configuration()
    c.setClassification(classification)
    if (properties.nonEmpty) c.setProperties(mapAsJavaMap(properties))
    if (configurations.nonEmpty) c.setConfigurations(seqAsJavaList(configurations.map(configureApplicationConfiguration)))
    c
  }

  def configureBootstrapAction(bootstrapAction: Config): BootstrapActionConfig = {
    val name: String = bootstrapAction.get("name", classOf[String])
    val script: Config = bootstrapAction.getNested("script")
    val path: String = script.get("path", classOf[String])
    val args: Seq[String] = script.getListOrEmpty("args", classOf[String]).asScala

    new BootstrapActionConfig()
      .withName(name)
      .withScriptBootstrapAction(new ScriptBootstrapActionConfig()
          .withPath(path)
          .withArgs(args: _*)
      )
  }

  def instancesConfiguration: JobFlowInstancesConfig = {
    val c = new JobFlowInstancesConfig()

    if (masterSecurityGroups.nonEmpty) {
      c.setEmrManagedMasterSecurityGroup(masterSecurityGroups.head)
      if (masterSecurityGroups.tail.nonEmpty) {
        c.setAdditionalMasterSecurityGroups(seqAsJavaList(masterSecurityGroups.tail))
      }
    }
    if (slaveSecurityGroups.nonEmpty) {
      c.setEmrManagedSlaveSecurityGroup(slaveSecurityGroups.head)
      if (slaveSecurityGroups.tail.nonEmpty) {
        c.setAdditionalSlaveSecurityGroups(seqAsJavaList(slaveSecurityGroups.tail))
      }
    }
    if (availabilityZones.nonEmpty) c.setPlacement(new PlacementType().withAvailabilityZones(availabilityZones: _*))
    if (sshKey.isPresent) c.setEc2KeyName(sshKey.get())
    if (subnetIds.nonEmpty) c.setEc2SubnetIds(seqAsJavaList(subnetIds))

    val instanceTypeConfigsBuilder = Seq.newBuilder[InstanceFleetConfig]
    instanceTypeConfigsBuilder += masterFleetConfiguration
    instanceTypeConfigsBuilder += configureSlaveFleet(CORE, coreFleet)
    if (!taskFleet.isEmpty) instanceTypeConfigsBuilder += configureSlaveFleet(TASK, taskFleet)
    c.setInstanceFleets(seqAsJavaList(instanceTypeConfigsBuilder.result()))

    c.setKeepJobFlowAliveWhenNoSteps(keepAliveWhenNoSteps)
    c.setTerminationProtected(terminationProtected)

    c
  }

  def buildCreateClusterRequest: RunJobFlowRequest = {
    new RunJobFlowRequest()
      .withAdditionalInfo(additionalInfo.orNull)
      .withApplications(applications.map(a => new Application().withName(a)): _*)
      .withBootstrapActions(bootstrapActions.map(configureBootstrapAction): _*)
      .withConfigurations(applicationConfigurations.map(configureApplicationConfiguration): _*)
      .withCustomAmiId(customAmiId.orNull)
      .withJobFlowRole(instanceProfile)
      .withLogUri(logUri.orNull)
      .withName(clusterName)
      .withReleaseLabel(releaseLabel)
      .withSecurityConfiguration(securityConfiguration.orNull)
      .withServiceRole(serviceRole)
      .withTags(tags.toSeq.map(m => new Tag().withKey(m._1).withValue(m._2)): _*)
      .withVisibleToAllUsers(isVisible)
      .withInstances(instancesConfiguration)
  }

  override def runTask(): TaskResult = {
    val r = withEmr {emr =>
      emr.runJobFlow(buildCreateClusterRequest)
    }
    logger.info(s"""[$operatorName] The request to create a cluster is accepted: ${r.getJobFlowId}""")

    val p = newEmptyParams
    p.getNestedOrSetEmpty("emr_fleet").getNestedOrSetEmpty("last_cluster").set("id", r.getJobFlowId)

    val builder = TaskResult.defaultBuilder(request)
    builder.storeParams(p)
    builder.resetStoreParams(ImmutableList.of(ConfigKey.of("emr_fleet", "last_cluster")))
    if (waitAvailableState) {
      logger.info(s"""[$operatorName] run a sub task: emr_fleet.wait_cluster""")
      builder.subtaskConfig(buildWaiterSubTaskConfig(r.getJobFlowId))
    }
    builder.build()
  }

  def buildWaiterSubTaskConfig(clusterId: String): Config = {
    val p = newEmptyParams
    p.set("_command", clusterId)
    p.set("_type", "emr_fleet.wait_cluster")
    p.set("success_states", seqAsJavaList(Seq[ClusterState](RUNNING, WAITING)))
    p.set("error_states", seqAsJavaList(Seq[ClusterState](TERMINATED, TERMINATED_WITH_ERRORS, TERMINATING)))
    p.set("timeout_duration", waitTimeoutDuration.toString)

    p.set("auth_method", authMethod)
    p.set("profile_name", profileName)
    if (profileFile.isPresent)p.set("profile_file", profileFile.get())
    p.set("use_http_proxy", useHttpProxy)
    if (region.isPresent) p.set("region", region.get())
    if (endpoint.isPresent) p.set("endpoint", endpoint.get())

    p
  }
}
