package pro.civitaspo.digdag.plugin.emr_fleet.operator

import com.amazonaws.services.elasticmapreduce.model.{Application, BootstrapActionConfig, ClusterState, Configuration, EbsBlockDeviceConfig, EbsConfiguration, InstanceFleetConfig, InstanceFleetProvisioningSpecifications, InstanceFleetType, InstanceTypeConfig, JobFlowInstancesConfig, PlacementType, RunJobFlowRequest, ScriptBootstrapActionConfig, SpotProvisioningSpecification, SpotProvisioningTimeoutAction, Tag, VolumeSpecification}
import com.amazonaws.services.elasticmapreduce.model.ClusterState.{RUNNING, TERMINATED, TERMINATED_WITH_ERRORS, TERMINATING, WAITING}
import com.amazonaws.services.elasticmapreduce.model.InstanceFleetType.{CORE, MASTER, TASK}
import com.amazonaws.services.elasticmapreduce.model.SpotProvisioningTimeoutAction.TERMINATE_CLUSTER
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

  protected val clusterName: String = params.get("name", classOf[String], s"digdag-${params.get("session_uuid", classOf[String])}")
  protected val tags: Map[String, String] = params.getMapOrEmpty("tags", classOf[String], classOf[String]).asScala.toMap
  protected val releaseLabel: String = params.get("release_label", classOf[String], "emr-5.16.0")
  protected val customAmiId: Optional[String] = params.getOptional("custom_ami_id", classOf[String])
  protected val masterSecurityGroups: Seq[String] = params.getListOrEmpty("master_security_groups", classOf[String]).asScala
  protected val slaveSecurityGroups: Seq[String] = params.getListOrEmpty("slave_security_groups", classOf[String]).asScala
  protected val sshKey: Optional[String] = params.getOptional("ssh_key", classOf[String])
  protected val subnetIds: Seq[String] = params.getListOrEmpty("subnet_ids", classOf[String]).asScala
  protected val availabilityZones: Seq[String] = params.getListOrEmpty("availability_zones", classOf[String]).asScala
  protected val spotSpec: Config = params.getNestedOrGetEmpty("spot_spec")
  protected val masterFleet: Config = params.getNested("master_fleet")
  protected val coreFleet: Config = params.getNested("core_fleet")
  protected val taskFleet: Config = params.getNestedOrGetEmpty("task_fleet")
  protected val logUri: Optional[String] = params.getOptional("log_uri", classOf[String])
  protected val additionalInfo: Optional[String] = params.getOptional("additional_info", classOf[String])
  protected val isVisible: Boolean = params.get("visible", classOf[Boolean], true)
  protected val securityConfiguration: Optional[String] = params.getOptional("security_configuration", classOf[String])
  protected val instanceProfile: String = params.get("instance_profile", classOf[String], "EMR_EC2_DefaultRole")
  protected val serviceRole: String = params.get("service_role", classOf[String], "EMR_DefaultRole")
  protected val applications: Seq[String] = params.getListOrEmpty("applications", classOf[String]).asScala
  protected val applicationConfigurations: Seq[Config] = params.getListOrEmpty("configurations", classOf[Config]).asScala
  protected val bootstrapActions: Seq[Config] = params.getListOrEmpty("bootstrap_actions", classOf[Config]).asScala
  protected val keepAliveWhenNoSteps: Boolean = params.get("keep_alive_when_no_steps", classOf[Boolean], true)
  protected val terminationProtected: Boolean = params.get("termination_protected", classOf[Boolean], false)
  protected val waitAvailableState: Boolean = params.get("wait_available_state", classOf[Boolean], true)
  protected val waitTimeoutDuration: DurationParam = params.get("wait_timeout_duration", classOf[DurationParam], DurationParam.parse("45m"))

  protected lazy val instanceFleetProvisioningSpecifications: InstanceFleetProvisioningSpecifications = {
    val blockDuration: Optional[DurationParam] = spotSpec.getOptional("block_duration", classOf[DurationParam])
    val timeoutAction: SpotProvisioningTimeoutAction = spotSpec.get("timeout_action", classOf[SpotProvisioningTimeoutAction], TERMINATE_CLUSTER)
    val timeoutDuration: DurationParam = spotSpec.get("timeout_duration", classOf[DurationParam], DurationParam.parse("45m"))

    val s = new SpotProvisioningSpecification()
    if (blockDuration.isPresent) {
      val bd: Int = blockDuration.get().getDuration.toMinutes.toInt
      if (!Seq[Int](1, 2, 3, 4, 5, 6).map(_ * 60).contains(bd)) {
        logger.warn(s"""[$operatorName] "1h", "2h", "3h", "4h", "5h", or "6h" are allowed for `block_duration`, so "${blockDuration.get().toString}" is invalid.""")
        logger.warn(s"""[$operatorName] `$operatorName` operator respects the options you set, but the behaviour depends on AWS. See the document ( https://docs.aws.amazon.com/emr/latest/ManagementGuide/emr-instance-fleet.html )""")
      }
      s.setBlockDurationMinutes(blockDuration.get().getDuration.toMinutes.toInt)
    }
    s.setTimeoutAction(timeoutAction)
    s.setTimeoutDurationMinutes(timeoutDuration.getDuration.toMinutes.toInt)

    new InstanceFleetProvisioningSpecifications().withSpotSpecification(s)
  }

  protected def masterFleetConfiguration: InstanceFleetConfig = {
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

  protected def configureSlaveFleet(fleetType: InstanceFleetType, fleetConfiguration: Config): InstanceFleetConfig = {
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

  protected def configureCandidate(candidate: Config, defaultBidPercentage: Double): InstanceTypeConfig = {
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

  protected def configureEbs(ebs: Config): EbsConfiguration = {
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

  protected def configureApplicationConfiguration(applicationConfiguration: Config): Configuration = {
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

  protected def configureBootstrapAction(bootstrapAction: Config): BootstrapActionConfig = {
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

  protected def instancesConfiguration: JobFlowInstancesConfig = {
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

  protected def buildCreateClusterRequest: RunJobFlowRequest = {
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

  protected def buildWaiterSubTaskConfig(clusterId: String): Config = {
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
