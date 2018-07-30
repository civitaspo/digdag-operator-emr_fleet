package pro.civitaspo.digdag.plugin.emr_fleet.operator

import io.digdag.client.config.Config
import io.digdag.spi.{OperatorContext, TaskResult, TemplateEngine}

class EmrFleetWaitClusterOperator(
  context: OperatorContext,
  systemConfig: Config,
  templateEngine: TemplateEngine
) extends AbstractEmrFleetOperator(context, systemConfig, templateEngine) {

  val clusterId: String = params.get("_command", classOf[String])

  override def runTask(): TaskResult = {
    null
  }
}
