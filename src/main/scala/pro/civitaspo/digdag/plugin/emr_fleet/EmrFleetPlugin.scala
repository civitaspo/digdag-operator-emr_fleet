package pro.civitaspo.digdag.plugin.emr_fleet

import java.util
import java.lang.reflect.Constructor

import io.digdag.client.config.Config
import io.digdag.spi.{Operator, OperatorContext, OperatorFactory, OperatorProvider, Plugin, TemplateEngine}
import javax.inject.Inject
import pro.civitaspo.digdag.plugin.emr_fleet.operator.{AbstractEmrFleetOperator, EmrFleetListClustersOperator}

object EmrFleetPlugin {

  class EmrFleetOperatorProvider extends OperatorProvider {

    @Inject protected var systemConfig: Config = null
    @Inject protected var templateEngine: TemplateEngine = null

    override def get(): util.List[OperatorFactory] = {
      util.Arrays.asList(
        operatorFactory("emr_fleet.list_clusters", classOf[EmrFleetListClustersOperator])
      )
    }

    private def operatorFactory[T <: AbstractEmrFleetOperator](operatorName: String, klass: Class[T]): OperatorFactory = {
      new OperatorFactory {
        override def getType: String = operatorName
        override def newOperator(context: OperatorContext): Operator = {
          val constructor: Constructor[T] = klass.getConstructor(
            classOf[OperatorContext], classOf[Config], classOf[TemplateEngine]
          )
          constructor.newInstance(context, systemConfig, templateEngine)
        }
      }
    }
  }
}

class EmrFleetPlugin extends Plugin {
  override def getServiceProvider[T](`type`: Class[T]): Class[_ <: T] = {
    if (`type` ne classOf[OperatorProvider]) return null
    classOf[EmrFleetPlugin.EmrFleetOperatorProvider].asSubclass(`type`)
  }
}
