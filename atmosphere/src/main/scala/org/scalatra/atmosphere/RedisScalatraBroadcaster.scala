package org.scalatra.atmosphere

import _root_.akka.actor._
import org.atmosphere.cpr._
import java.util.concurrent.ConcurrentLinkedQueue
import org.atmosphere.plugin.redis.RedisBroadcaster

import scala.concurrent.{Future, ExecutionContext}
import collection.JavaConverters._
import grizzled.slf4j.Logger

import org.json4s.ShortTypeHints
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.{read, write}


final class RedisScalatraBroadcaster()(implicit wireFormat: WireFormat, protected var _actorSystem: ActorSystem)
  extends RedisBroadcaster with ScalatraBroadcaster {

  private[this] val logger: Logger = Logger[RedisScalatraBroadcaster]
  protected var _resources: ConcurrentLinkedQueue[AtmosphereResource] = resources
  protected var _wireFormat: WireFormat = wireFormat
  implicit val formats = Serialization.formats(ShortTypeHints(List(classOf[Everyone], classOf[OnlySelf], classOf[SkipSelf])))

  override def broadcast[T <: OutboundMessage](msg: T, clientFilter: ClientFilter)
                                              (implicit executionContext: ExecutionContext): Future[T] = {
    logger.info("Resource [%s] sending message to [%s] with contents:  [%s]".format(clientFilter.uuid, clientFilter, msg))
    // Casting to ProtocolMessage because when writing the message everything gets wrapped by a 'content' element.
    // This seems to be because the actual message is a ProtocolMessage which defines a 'content' method.
    val protocolMsg = msg.asInstanceOf[ProtocolMessage[Object]]
    val content = protocolMsg.content
    val actualMessage = write(content)
    val wrappedMessage = new Message(actualMessage, clientFilter)
    val wrappedMessageString = write(wrappedMessage)

    broadcast(wrappedMessageString).map(_ => msg)
  }

  override protected def broadcastReceivedMessage(message: AnyRef) {
    try {
      val messageString = message.asInstanceOf[String]
      val redisMessage = read[Message](messageString)
      val embeddedMsg = redisMessage.msg
      val clientFilter = redisMessage.clientFilter
      val newMsg = filter(embeddedMsg)

      if (newMsg != null) {
        val atmosphereClients = _resources.asScala map (_.clientOption) filter(_.isDefined) map (_.get)
        val selectedAtmosphereClients = atmosphereClients filter clientFilter
        val selectedSet = selectedAtmosphereClients.map(_.resource).toSet.asJava
        push(new Entry(newMsg, selectedSet, new BroadcasterFuture[Any](newMsg), embeddedMsg))
      }
    } catch {
      case t: Throwable => logger.error("failed to push message: " + message, t)
    }
  }
}

class Message(val msg: String, val clientFilter: ClientFilter)

