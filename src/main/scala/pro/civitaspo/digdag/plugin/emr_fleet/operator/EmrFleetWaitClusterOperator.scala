package pro.civitaspo.digdag.plugin.emr_fleet.operator

import com.amazonaws.services.elasticmapreduce.model.{ClusterState, DescribeClusterRequest, DescribeClusterResult}
import io.digdag.client.config.Config
import io.digdag.spi.{OperatorContext, TaskExecutionException, TaskResult, TemplateEngine}
import io.digdag.util.DurationParam

import scala.collection.JavaConverters._

class EmrFleetWaitClusterOperator(
  context: OperatorContext,
  systemConfig: Config,
  templateEngine: TemplateEngine
) extends AbstractEmrFleetOperator(context, systemConfig, templateEngine) {

  val clusterId: String = params.get("_command", classOf[String])
  val successStates: Seq[ClusterState] = params.getList("success_states", classOf[ClusterState]).asScala
  val errorStates: Seq[ClusterState] = params.getListOrEmpty("error_states", classOf[ClusterState]).asScala
  val pollingInterval: DurationParam = params.get("polling_interval", classOf[DurationParam], DurationParam.parse("5s"))
  val timeoutDuration: DurationParam = params.get("timeout_duration", classOf[DurationParam], DurationParam.parse("45m"))

  override def runTask(): TaskResult = {
    pollingCluster()
    TaskResult.empty(request)
  }

  private def pollingCluster(): Unit = {
    val timeoutSeconds: Int = timeoutDuration.getDuration.getSeconds.toInt
    val pollingIntervalSeconds: Int = pollingInterval.getDuration.getSeconds.toInt
    val counter: Iterator[Int] = Stream.from(0).iterator
    while (!pollCluster) {
      val timeSpentSeconds: Int = counter.next * pollingIntervalSeconds
      if (timeSpentSeconds >= timeoutSeconds) {
        throw new TaskExecutionException(s"timeout => spent: ${timeSpentSeconds}s >= ${timeoutSeconds}s")
      }
      Thread.sleep(pollingIntervalSeconds * 1000)  // millis
    }
  }

  private def pollCluster: Boolean = {
    val result: DescribeClusterResult = describeCluster
    val state: ClusterState = ClusterState.fromValue(result.getCluster.getStatus.getState)

    logger.info(s"current state: ${state.toString}")

    if (errorStates.exists(_.equals(state))) {
      throw new TaskExecutionException(s"Cluster State is error state: ${state.toString}")
    }
    successStates.exists(_.equals(state))
  }

  private def describeCluster: DescribeClusterResult = {
     withEmr { emr =>
       emr.describeCluster(
         new DescribeClusterRequest()
           .withClusterId(clusterId)
       )
     }
  }
}
