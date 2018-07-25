package pro.civitaspo.digdag.plugin.emr_fleet

import java.util

import io.digdag.spi.{OperatorFactory, OperatorProvider, Plugin}

object EmrFleetPlugin {

  class EmrFleetOperatorProvider extends OperatorProvider {

    override def get(): util.List[OperatorFactory] = {
      util.Arrays.asList()
    }

  }
}

class EmrFleetPlugin extends Plugin {
  override def getServiceProvider[T](`type`: Class[T]): Class[_ <: T] = {
    if (`type` ne classOf[OperatorProvider]) return null
    classOf[EmrFleetPlugin.EmrFleetOperatorProvider].asSubclass(`type`)
  }
}
