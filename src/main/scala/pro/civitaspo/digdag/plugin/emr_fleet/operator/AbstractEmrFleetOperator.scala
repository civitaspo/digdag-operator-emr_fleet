package pro.civitaspo.digdag.plugin.emr_fleet.operator

import io.digdag.client.config.Config
import io.digdag.spi.{OperatorContext, TemplateEngine}
import io.digdag.util.BaseOperator
import org.slf4j.{Logger, LoggerFactory}

abstract class AbstractEmrFleetOperator(
  context: OperatorContext,
  systemConfig: Config,
  templateEngine: TemplateEngine
) extends BaseOperator(context) {

  protected val logger: Logger = LoggerFactory.getLogger(this.getClass)

}
