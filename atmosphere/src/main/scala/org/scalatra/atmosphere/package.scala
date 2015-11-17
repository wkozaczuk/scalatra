package org.scalatra

import org.atmosphere.cpr.{AtmosphereResourceSessionFactory, AtmosphereResourceSession, AtmosphereResource}

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._
import _root_.akka.actor.ActorSystem
import scala.util.control.Exception._

package object atmosphere {

  type AtmoReceive = PartialFunction[InboundMessage, Unit]

  abstract class ClientFilter(val uuid: String) extends Function[AtmosphereClient, Boolean]

  class Everyone extends ClientFilter(null) {
    def apply(v1: AtmosphereClient): Boolean = true
    override def toString(): String = "Everyone"
  }

  class OnlySelf(uuid: String) extends ClientFilter(uuid) {
    def apply(v1: AtmosphereClient): Boolean = v1.uuid == uuid
    override def toString(): String = "OnlySelf"
  }

  class SkipSelf(uuid: String) extends ClientFilter(uuid) {
    def apply(v1: AtmosphereClient): Boolean = v1.uuid != uuid
    override def toString(): String = "Others"
  }

  val AtmosphereClientKey = "org.scalatra.atmosphere.AtmosphereClientConnection"
  val AtmosphereRouteKey = "org.scalatra.atmosphere.AtmosphereRoute"
  val ActorSystemKey = "org.scalatra.atmosphere.ActorSystem"
  val TrackMessageSize = "org.scalatra.atmosphere.TrackMessageSize"

  implicit def atmoResourceWithClient(resource: AtmosphereResource) = new {
    def clientOption:Option[AtmosphereClient] = resolveAtmosphereResourceSessionOption(resource)
      .flatMap(s => Option(s.getAttribute(org.scalatra.atmosphere.AtmosphereClientKey)))
      .map(_.asInstanceOf[AtmosphereClient])

    private def resolveAtmosphereResourceSessionOption(resource: AtmosphereResource): Option[AtmosphereResourceSession] = {
      Option(resource).flatMap(r => Option(AtmosphereResourceSessionFactory.getDefault.getSession(r, false)))
    }

  }
}
