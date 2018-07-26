package pro.civitaspo.digdag.plugin.emr_fleet.operator

import com.amazonaws.services.elasticmapreduce.model.{ClusterState, ListClustersRequest}
import io.digdag.client.config.Config
import io.digdag.spi.{OperatorContext, TaskResult, TemplateEngine}

class EmrFleetDetectClustersOperator(
  context: OperatorContext,
  systemConfig: Config,
  templateEngine: TemplateEngine
) extends AbstractEmrFleetOperator(context, systemConfig, templateEngine) {

  val hoursCreatedWithin: Int = params.get("hours_created_within", classOf[Int])
  val regexp: String = params.get("regexp", classOf[String], ".*")
  val states: Seq[ClusterState] = {
    val list = params.getListOrEmpty("states", classOf[String])
    if (list.isEmpty) Seq(ClusterState.RUNNING, ClusterState.WAITING)
    else {
      val builder = Seq.newBuilder[ClusterState]
      for (s: String <- list) {
        builder += ClusterState.fromValue(s)
      }
      builder.result()
    }
  }

  override def runTask(): TaskResult = {
    emr.listClusters(new ListClustersRequest())
    TaskResult.empty(request)
  }



}
