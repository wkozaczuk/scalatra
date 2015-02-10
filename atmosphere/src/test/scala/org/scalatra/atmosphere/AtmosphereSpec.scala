package org.scalatra
package atmosphere

import java.util.concurrent.{ CountDownLatch, TimeUnit }
import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }

import _root_.akka.actor.ActorSystem
import org.atmosphere.wasync._
import org.atmosphere.wasync.impl.{ DefaultOptions, DefaultOptionsBuilder, DefaultRequestBuilder }
import org.json4s.JsonDSL._
import org.json4s.{ DefaultFormats, Formats, _ }
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.test.specs2.MutableScalatraSpec
import org.specs2.specification.{ Fragments, Step }

import scala.concurrent.duration._

class AtmosphereSpecServlet(implicit override protected val scalatraActorSystem: ActorSystem)
    extends ScalatraServlet with JacksonJsonSupport with SessionSupport with AtmosphereSupport {

  implicit protected def jsonFormats: Formats = DefaultFormats
  implicit val system = scalatraActorSystem.dispatcher

  get("/echo") {
    "echo ok"
  }

  get("/broadcast") {
    AtmosphereClient.broadcast("/atmosphere-endpoint", "ping")
  }

  get("/session") {
    session("foo") = "bar"
  }

  atmosphere("/atmosphere-endpoint") {
    new AtmosphereClient {
      def receive: AtmoReceive = {
        case Connected =>
          println(s"Connected client: $uuid")
          broadcast("connected", to = Everyone)
        case Disconnected(ClientDisconnected, _) =>
          println("Client %s disconnected" format uuid)
        case Disconnected(ServerDisconnected, _) =>
          println("Server disconnected the client %s" format uuid)
        case TextMessage(txt) =>
          println("Received text message: " + txt)
          send("seen" -> txt: JValue)
        case JsonMessage(json) =>
          println("Received json message: " + json)
          send(("seen" -> "test1") ~ ("data" -> json))
        case m =>
          println("Got unknown message " + m.getClass + " " + m.toString)
      }
    }
  }

  override def handle(request: HttpServletRequest, response: HttpServletResponse) {
    withRequestResponse(request, response) {
      println(request.headers)
      println("routeBasePath: " + routeBasePath(request))
      println("requestPath: " + requestPath(request))

      super.handle(request, response)
    }
  }
}

//object WaSync {
//
//
//  val Get = Request.METHOD.GET
//  val Post = Request.METHOD.POST
//  val Trace = Request.METHOD.TRACE
//  val Put = Request.METHOD.PUT
//  val Delete = Request.METHOD.DELETE
//  val Options = Request.METHOD.OPTIONS
//
//  val WebSocket = Request.TRANSPORT.WEBSOCKET
//  val Sse = Request.TRANSPORT.SSE
//  val Streaming = Request.TRANSPORT.STREAMING
//  val LongPolling = Request.TRANSPORT.LONG_POLLING
//
//  type ErrorHandler = PartialFunction[Throwable, Unit]
//
//  def printIOException: ErrorHandler = {
//    case e: IOException => e.printStackTrace()
//  }
//
//  implicit def scalaFunction2atmoFunction[T](fn: T => Unit): Function[T] = new Function[T] { def on(t: T) { fn(t) } }
//  implicit def scalaFunction2atmoEncoder[T, S](fn: T => S): Encoder[T, S] = new Encoder[T, S] { def encode(s: T): S = fn(s) }
//
//  implicit def scalaFunction2atmoDecoder[T <: AnyRef, S](fn: T => S): Decoder[T, S] = new Decoder[T, S] {
//    def decode(e: EVENT_TYPE, s: T): S = fn(s)
//  }
//  implicit def errorHandler2atmoFunction(fn: PartialFunction[Throwable, Unit]): Function[Throwable] = new Function[Throwable] {
//    def on(t: Throwable) {
//      if (fn.isDefinedAt(t)) fn(t)
//      else throw t
//    }
//  }
//
//
//}

class AtmosphereSpec extends MutableScalatraSpec {

  implicit val system = ActorSystem("scalatra")

  mount(new AtmosphereSpecServlet, "/*")

  implicit val formats = DefaultFormats

  sequential

  def buildSocket(): (Socket, DefaultRequestBuilder) = {
    val client: Client[DefaultOptions, DefaultOptionsBuilder, DefaultRequestBuilder] =
      ClientFactory.getDefault.newClient.asInstanceOf[Client[DefaultOptions, DefaultOptionsBuilder, DefaultRequestBuilder]]

    val req = client.newRequestBuilder
      .method(Request.METHOD.GET)
      .uri(baseUrl + "/atmosphere-endpoint")
      .transport(Request.TRANSPORT.WEBSOCKET)

    val opts = client.newOptionsBuilder().reconnect(false).build()

    val socket = client.create(opts)

    (socket, req)
  }

  def buildSocketWithSessionId(sessionId: String): (Socket, DefaultRequestBuilder) = {
    val client: Client[DefaultOptions, DefaultOptionsBuilder, DefaultRequestBuilder] =
      ClientFactory.getDefault.newClient.asInstanceOf[Client[DefaultOptions, DefaultOptionsBuilder, DefaultRequestBuilder]]

    val req = client.newRequestBuilder
      .method(Request.METHOD.GET)
      .uri(baseUrl + "/atmosphere-endpoint")
      .header("Cookie", sessionId)
      .transport(Request.TRANSPORT.WEBSOCKET)

    val opts = client.newOptionsBuilder().reconnect(false).build()

    val socket = client.create(opts)

    (socket, req)
  }

  def getSessionId: String = {
    get("/session") {
      header must haveKey("Set-Cookie")
      header("Set-Cookie") must contain("JSESSIONID")
      header("Set-Cookie").split(";")(0)
    }
  }

  "To support Atmosphere, Scalatra" should {

    "allow regular requests" in {
      get("/echo") {
        println(header)
        status must_== 200
        body must_== "echo ok"
      }
    }

    "allow one client to connect and close" in {
      val messageLatch = new CountDownLatch(1)
      val closeLatch = new CountDownLatch(1)

      val (socket, req) = buildSocket()

      socket.on(Event.MESSAGE, new Function[String] {
        def on(r: String) = {
          messageLatch.countDown()
          println(s"Socket received: $r")
        }
      }).on(Event.CLOSE, new Function[String] {
        def on(r: String) = {
          closeLatch.countDown()
          println(s"Socket closed: $r")
        }
      }).on(new Function[Throwable] {
        def on(t: Throwable) = {
          t.printStackTrace()
        }
      })

      socket.open(req.build()).fire("echo")
      messageLatch.await(5, TimeUnit.SECONDS) must beTrue

      socket.close()
      closeLatch.await(5, TimeUnit.SECONDS) must beTrue
    }

    "receive an event when two AtmosphereResources existed for the same session and the first AtmosphereResource is closed already" in {
      val messageLatch1 = new CountDownLatch(1)
      val messageLatch2 = new CountDownLatch(3)
      val closeLatch1 = new CountDownLatch(1)
      val closeLatch2 = new CountDownLatch(1)

      val sessionId = getSessionId
      val (socket1, req1) = buildSocketWithSessionId(sessionId)
      val (socket2, req2) = buildSocketWithSessionId(sessionId)

      socket1.on(Event.MESSAGE, new Function[String] {
        def on(r: String) = {
          messageLatch1.countDown()
          println(s"Socket 1 received: $r")
        }
      }).on(Event.CLOSE, new Function[String] {
        def on(r: String) = {
          closeLatch1.countDown()
          println(s"Socket 1 closed: $r")
        }
      })

      socket2.on(Event.MESSAGE, new Function[String] {
        def on(r: String) = {
          messageLatch2.countDown()
          println(s"Socket 2 received: $r")
        }
      }).on(Event.CLOSE, new Function[String] {
        def on(r: String) = {
          closeLatch2.countDown()
          println(s"Socket 2 closed: $r")
        }
      })

      // connect two sockets
      socket1.open(req1.build()).fire("echo1")
      socket2.open(req2.build()).fire("echo2")

      messageLatch1.await(5, TimeUnit.SECONDS) must beTrue

      // close first socket -> removes AtmosphereClient from AtmosphereResourceSession
      socket1.close()
      closeLatch1.await(5, TimeUnit.SECONDS) must beTrue

      // send message again and it should work
      socket2.fire("echo3")

      // get broadcast and it should work
      get("/broadcast") {
        status must be_===(200)
      }

      messageLatch2.await(5, TimeUnit.SECONDS) must beTrue

      // wait for receive
      socket2.close()
      closeLatch2.await(5, TimeUnit.SECONDS) must beTrue
    }
  }

  private def stopSystem() {
    system.shutdown()
    system.awaitTermination(Duration(1, TimeUnit.MINUTES))
  }

  override def map(fs: => Fragments): Fragments = super.map(fs) ^ Step(stopSystem())
}
