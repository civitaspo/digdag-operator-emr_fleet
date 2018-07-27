package pro.civitaspo.digdag.plugin.emr_fleet.operator

import com.amazonaws.services.elasticmapreduce.model.TerminateJobFlowsRequest
import io.digdag.client.config.Config
import io.digdag.spi.{OperatorContext, TaskResult, TemplateEngine}

class EmrFleetShutdownClusterOperator(
  context: OperatorContext,
  systemConfig: Config,
  templateEngine: TemplateEngine
) extends AbstractEmrFleetOperator(context, systemConfig, templateEngine) {

  val clusterId: String = params.get("_command", classOf[String])

  override def runTask(): TaskResult = {
    emr.terminateJobFlows(new TerminateJobFlowsRequest().withJobFlowIds(clusterId))
    logger.info(s"Shutdown => Id: $clusterId")
    TaskResult.empty(request)
  }
}
