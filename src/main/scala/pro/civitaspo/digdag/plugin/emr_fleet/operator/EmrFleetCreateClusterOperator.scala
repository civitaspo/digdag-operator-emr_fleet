package pro.civitaspo.digdag.plugin.emr_fleet.operator

import io.digdag.client.config.Config
import io.digdag.spi.{OperatorContext, TaskResult, TemplateEngine}

class EmrFleetCreateClusterOperator(
  context: OperatorContext,
  systemConfig: Config,
  templateEngine: TemplateEngine
) extends AbstractEmrFleetOperator(context, systemConfig, templateEngine) {
  override def runTask(): TaskResult = null
}
