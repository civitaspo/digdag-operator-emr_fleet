package pro.civitaspo.digdag.plugin.emr_fleet.operator

import java.util.Date

import com.amazonaws.services.elasticmapreduce.model.{ClusterState, ClusterSummary, ListClustersRequest, ListClustersResult}
import com.google.common.collect.ImmutableList
import io.digdag.client.config.{Config, ConfigKey}
import io.digdag.spi.{OperatorContext, TaskResult, TemplateEngine}

import scala.collection.JavaConverters._


class EmrFleetDetectClustersOperator(
  context: OperatorContext,
  systemConfig: Config,
  templateEngine: TemplateEngine
) extends AbstractEmrFleetOperator(context, systemConfig, templateEngine) {

  val timezone: String = params.get("timezone", classOf[String])
  val hoursCreatedWithin: Int = params.get("hours_created_within", classOf[Int])
  val regexp: String = params.get("regexp", classOf[String], ".*")
  val states: Seq[ClusterState] = {
    val list = params.getListOrEmpty("states", classOf[String])
    if (list.isEmpty) Seq(ClusterState.RUNNING, ClusterState.WAITING)
    else list.asScala.map(ClusterState.fromValue)
  }

  override def runTask(): TaskResult = {
    val clusters = detectClusters()

    val isDetected = clusters.nonEmpty
    val detectedClusterSummaries = clusters.map {cs =>
      val p = clusterSummaryToStoreParams(cs)
      logger.info(s"""[$operatorName] detected: ${p}""")
      p
    }

    val p = newEmptyParams
    val lastDetection = p.getNestedOrSetEmpty("emr_fleet").getNestedOrSetEmpty("last_detection")
    lastDetection.set("is_detected", isDetected)
    lastDetection.set("clusters", seqAsJavaList(detectedClusterSummaries))

    val builder = TaskResult.defaultBuilder(request)
    builder.storeParams(p)
    builder.resetStoreParams(ImmutableList.of(ConfigKey.of("emr_fleet", "last_detection")))
    builder.build()
  }

  private def clusterSummaryToStoreParams(cs: ClusterSummary): Config = {
    val p = newEmptyParams
    p.set("id", cs.getId)
    p.set("name", cs.getName)
    p.set("normalized_instance_hours", cs.getNormalizedInstanceHours)
    p.set("current_state", cs.getStatus.getState)
    p.set("last_state_change_reason_code", cs.getStatus.getStateChangeReason.getCode)
    p.set("last_state_change_reason_message", cs.getStatus.getStateChangeReason.getMessage)
    p.set("created_at", cs.getStatus.getTimeline.getCreationDateTime.getTime)
    p.set("end_at", Option(cs.getStatus.getTimeline.getEndDateTime) match {
      case Some(x) => x.getTime
      case None => null
    })
    p.set("ready_at", Option(cs.getStatus.getTimeline.getReadyDateTime) match {
      case Some(x) => x.getTime
      case None => null
    })
    p
  }

  private def detectClusters(maker: Option[String] = None): Seq[ClusterSummary] = {
    val req = new ListClustersRequest()
      .withClusterStates(states: _*)
      .withCreatedAfter(createdAfter)
    maker match {
      case Some(x) => req.setMarker(x)
      case None =>  // Do nothing
    }
    val result: ListClustersResult = withEmr(_.listClusters(req))
    val builder = Seq.newBuilder[ClusterSummary]
    for (cs: ClusterSummary <- result.getClusters.asScala if cs.getName.matches(regexp)) builder += cs

    Option(result.getMarker) match {
      case Some(x) => detectClusters(maker = Some(x)).foreach(cs => builder += cs)
      case None =>  // Do nothing
    }
    builder.result()
  }

  private def createdAfter: Date = {
    val minusMillis: Long = hoursCreatedWithin * 3600 * 1000
    new Date(System.currentTimeMillis() - minusMillis)
  }
}
