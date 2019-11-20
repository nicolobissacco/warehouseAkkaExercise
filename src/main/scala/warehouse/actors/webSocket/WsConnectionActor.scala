package warehouse.actors.webSocket

import java.util.UUID

import akka.actor.{Actor, ActorRef, ActorSystem, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.pattern.pipe
import akka.stream.scaladsl.{Keep, Sink, Source, StreamRefs}
import akka.stream.{ActorMaterializer, OverflowStrategy, SourceRef}
import akka.util.Timeout
import org.reactivestreams.Publisher
import warehouse.AppConfig
import warehouse.actors.webSocket.WsConnectionActor.WsConnectionCreated
import warehouse.actors.webSocket.WsManagerActor.CreateWsConnection

import scala.concurrent.Future

class WsConnectionActor(id: String) extends Actor {
  private implicit val timeout: Timeout = Timeout(AppConfig.askTimeout)
  implicit val system: ActorSystem = context.system
  implicit val dispatcher = context.dispatcher
  implicit val am = ActorMaterializer()

  val connForUser : Map[UUID, (ActorRef, Publisher[Message])]

  val (down, publisher) = Source
    .actorRef[Message](1000, OverflowStrategy.fail)
    .toMat(Sink.asPublisher(fanout = false))(Keep.both)
    .run()

  val streamRef: Future[SourceRef[Message]] = Source.fromPublisher(publisher).runWith(StreamRefs.sourceRef())

  private def receivePassivate: Receive = {
    case ReceiveTimeout => context.parent ! ShardRegion.Passivate
    case ShardRegion.Passivate => context.stop(self)
  }

  override def receive: Receive = receivePassivate orElse {
    case cmd: CreateWsConnection => {
      println(s"WsConnectionActor->CreateWsConnection ${cmd.id}")
      //creo la connesisone e la ritorno al chiamante
      streamRef.map(r => WsConnectionCreated(Some(r))).pipeTo(sender())
    }
    case msg: TextMessage => {
      println(s"WsConnectionActor->TextMessage ${msg.toString}")
      //invio il messaggio
      if (down != null) {
        down ! msg
      }
    }
    case _ =>
      println("WsConnectionActor->>>>>>>>>>>>>ERROREEE<<<<<<<<<<<")
  }
}

object WsConnectionActor {
  val actorName = "ws-connection-actor"

  def props(id: String): Props = Props(new WsConnectionActor(id))

  case class WsConnectionCreated(option: Option[SourceRef[Message]])

}