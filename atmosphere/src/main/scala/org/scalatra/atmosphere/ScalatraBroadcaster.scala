package org.scalatra
package atmosphere

import _root_.akka.actor._
import collection.JavaConverters._
import grizzled.slf4j.Logger
import org.atmosphere.cpr._
import concurrent.Future
import java.util.concurrent.ConcurrentLinkedQueue

trait ScalatraBroadcaster extends Broadcaster {

  private[this] val logger: Logger = Logger[ScalatraBroadcaster]
  protected var _resources: ConcurrentLinkedQueue[AtmosphereResource]
  protected var _wireFormat: WireFormat
  protected implicit var _actorSystem: ActorSystem

  def broadcast[T <: OutboundMessage](msg: T, clientFilter: ClientFilter): java.util.concurrent.Future[AnyRef] = {
    val atmosphereClients = _resources.asScala map (_.clientOption) filter(_.isDefined) map (_.get)
    val selectedAtmosphereClients = atmosphereClients filter clientFilter
    logger.trace("Sending %s to %s".format(msg, selectedAtmosphereClients.map(_.uuid)))
    broadcast(_wireFormat.render(msg), selectedAtmosphereClients.map(_.resource).toSet.asJava)
  }
}