package pro.civitaspo.digdag.plugin.emr_fleet.operator

import com.amazonaws.services.elasticmapreduce.model.{
  Cluster,
  ClusterState,
  DescribeClusterRequest,
  DescribeClusterResult,
  Instance,
  InstanceFleetType,
  ListInstancesRequest
}
import com.google.common.collect.ImmutableList
import io.digdag.client.config.{Config, ConfigKey}
import io.digdag.spi.{OperatorContext, TaskExecutionException, TaskResult, TemplateEngine}
import io.digdag.util.DurationParam
import pro.civitaspo.digdag.plugin.emr_fleet.wrapper.{ParamInGiveup, ParamInRetry, RetryExecutorWrapper}

import scala.collection.JavaConverters._

class EmrFleetWaitClusterOperator(context: OperatorContext, systemConfig: Config, templateEngine: TemplateEngine)
    extends AbstractEmrFleetOperator(context, systemConfig, templateEngine) {

  class EmrFleetWaitClusterOperatorException(message: String) extends TaskExecutionException(message)
  class EmrFleetWaitClusterOperatorRetryableStateException(message: String) extends EmrFleetWaitClusterOperatorException(message)

  protected val clusterId: String = params.get("_command", classOf[String])
  protected val successStates: Seq[ClusterState] = params.getList("success_states", classOf[ClusterState]).asScala
  protected val errorStates: Seq[ClusterState] = params.getListOrEmpty("error_states", classOf[ClusterState]).asScala
  protected val pollingInterval: DurationParam = params.get("polling_interval", classOf[DurationParam], DurationParam.parse("5s"))
  protected val timeoutDuration: DurationParam = params.get("timeout_duration", classOf[DurationParam], DurationParam.parse("45m"))

  override def runTask(): TaskResult = {
    pollingCluster()

    val p = newEmptyParams
    p.getNestedOrSetEmpty("emr_fleet").getNestedOrSetEmpty("last_cluster").set("id", clusterId)

    val c = p.getNestedOrSetEmpty("emr_fleet").getNestedOrSetEmpty("last_cluster").getNestedOrSetEmpty("master")
    storeParamMasterInstance(c)

    val builder = TaskResult.defaultBuilder(request)
    builder.resetStoreParams(ImmutableList.of(ConfigKey.of("emr_fleet", "last_cluster")))
    builder.storeParams(p)
    builder.build()
  }

  protected def pollingCluster(): Unit = {
    RetryExecutorWrapper()
      .withInitialRetryWait(pollingInterval.getDuration)
      .withMaxRetryWait(pollingInterval.getDuration)
      .withRetryLimit(Int.MaxValue)
      .withTimeout(timeoutDuration.getDuration)
      .withWaitGrowRate(1.0)
      .onGiveup { p: ParamInGiveup =>
        logger.error(s"[${operatorName}] failed to wait cluster: ${p.firstException.getMessage}", p.firstException)
      }
      .onRetry { p: ParamInRetry =>
        logger.info(s"[${operatorName}] polling ${p.e.getMessage} (next: ${p.retryCount}, total wait: ${p.totalWaitMillis} ms)")
      }
      .retryIf {
        case ex: EmrFleetWaitClusterOperatorRetryableStateException => true
        case _ => false
      }
      .runInterruptible {
        val result: DescribeClusterResult = describeCluster
        val cluster: Cluster = result.getCluster
        val state: ClusterState = ClusterState.fromValue(cluster.getStatus.getState)

        if (errorStates.exists(_.equals(state))) {
          throw new EmrFleetWaitClusterOperatorException(
            s"""[$operatorName] cluster: ${cluster.getName} (id: ${cluster.getId}) is one of the error states: ${state.toString}"""
          )
        }
        if (!successStates.exists(_.equals(state))) {
          // This exception is caught and the message is used for retrying, so $operatorName is not needed as prefix.
          throw new EmrFleetWaitClusterOperatorRetryableStateException(
            s"""cluster: ${cluster.getName} (id: ${cluster.getId}) is not one of the success states: ${state.toString}"""
          )
        }
      }
  }

  protected def describeCluster: DescribeClusterResult = {
    withEmr { emr =>
      emr.describeCluster(
        new DescribeClusterRequest()
          .withClusterId(clusterId)
      )
    }
  }

  private def describeMasterInstance: Option[Instance] = {
    val list = withEmr { emr =>
      emr.listInstances(
        new ListInstancesRequest()
          .withClusterId(clusterId)
          .withInstanceFleetType(InstanceFleetType.MASTER)
      )
    }
    val instances: Seq[Instance] = list.getInstances.asScala
    if (instances.isEmpty) return None
    Some(instances.head)
  }

  protected def storeParamMasterInstance(to: Config): Unit = {
    describeMasterInstance match {
      case None =>
        logger.info(s"""[$operatorName] The cluster: $clusterId does not have the master node info yet.""")
      case Some(i) =>
        if (i.getEc2InstanceId != null) to.set("instance_id", i.getEc2InstanceId)
        if (i.getInstanceType != null) to.set("instance_type", i.getInstanceType)
        if (i.getMarket != null) to.set("market", i.getMarket)
        if (i.getPrivateDnsName != null) to.set("private_dns_name", i.getPrivateDnsName)
        if (i.getPrivateIpAddress != null) to.set("private_ip_address", i.getPrivateIpAddress)
        if (i.getPublicDnsName != null) to.set("public_dns_name", i.getPublicDnsName)
        if (i.getPublicIpAddress != null) to.set("public_ip_address", i.getPublicIpAddress)
    }
  }
}
