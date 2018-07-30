package pro.civitaspo.digdag.plugin.emr_fleet.operator

import com.amazonaws.services.elasticmapreduce.model.ClusterState
import io.digdag.client.config.Config
import io.digdag.spi.{OperatorContext, TaskResult, TemplateEngine}

import scala.collection.JavaConverters._

class EmrFleetWaitClusterOperator(
  context: OperatorContext,
  systemConfig: Config,
  templateEngine: TemplateEngine
) extends AbstractEmrFleetOperator(context, systemConfig, templateEngine) {

  val clusterId: String = params.get("_command", classOf[String])
  val successStates: Seq[ClusterState] = params.getList("success_states", classOf[ClusterState]).asScala
  val errorStates: Seq[ClusterState] = params.getListOrEmpty("error_states", classOf[ClusterState]).asScala
  val pollingIntervalSeconds: Int = params.get("polling_interval_seconds", classOf[Int], 5)
  val timeoutDurationSeconds: Int = params.get("timeout_duration_seconds", classOf[Int], 45*60)  // 45 min

  override def runTask(): TaskResult = {
    null
  }
}
