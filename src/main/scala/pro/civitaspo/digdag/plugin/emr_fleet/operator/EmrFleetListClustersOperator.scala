package pro.civitaspo.digdag.plugin.emr_fleet.operator

import io.digdag.spi.{Operator, OperatorContext, OperatorFactory, TaskResult}
import io.digdag.util.BaseOperator
import wvlet.log.LogSupport

object EmrFleetListClustersOperator {

  class EmrFleetListClustersOperatorFactory extends OperatorFactory {

    override def getType: String = "emr_fleet.list_clusters"
    override def newOperator(context: OperatorContext): Operator = {
      new EmrFleetListClustersOperator(context)
    }
  }
}

class EmrFleetListClustersOperator(context: OperatorContext)
  extends BaseOperator(context)
    with LogSupport {

  override def runTask(): TaskResult = {
    null
  }

}
