package pro.civitaspo.digdag.plugin.emr_fleet.operator

import com.amazonaws.services.elasticmapreduce.model.TerminateJobFlowsRequest
import io.digdag.client.config.Config
import io.digdag.spi.{OperatorContext, TaskResult, TemplateEngine}

class EmrFleetShutdownClusterOperator(context: OperatorContext, systemConfig: Config, templateEngine: TemplateEngine)
    extends AbstractEmrFleetOperator(context, systemConfig, templateEngine) {

  protected val clusterId: String = params.get("_command", classOf[String])

  override def runTask(): TaskResult = {
    withEmr(_.terminateJobFlows(new TerminateJobFlowsRequest().withJobFlowIds(clusterId)))
    logger.info(s"""[$operatorName] cluster id: $clusterId""")
    TaskResult.empty(request)
  }
}
