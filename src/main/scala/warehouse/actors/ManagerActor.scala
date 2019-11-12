package warehouse.actors

import akka.actor.{Actor, ActorSystem, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.scaladsl.{Keep, Sink, Source, StreamRefs}
import akka.stream.{ActorMaterializer, OverflowStrategy, SourceRef}
import akka.util.Timeout
import akka.pattern.pipe
import warehouse.AppConfig
import warehouse.actors.ManagerActor.{CreateWs, CreatedWs, SendWs}
import warehouse.actors.write.ActorSharding

import scala.concurrent.Future
import scala.concurrent.duration._

class ManagerActor extends Actor with ActorSharding {
  private implicit val timeout: Timeout = Timeout(AppConfig.askTimeout)
  override implicit val system: ActorSystem = context.system
  implicit val dispatcher = context.dispatcher
  implicit val am = ActorMaterializer()

  context.setReceiveTimeout(120.seconds)

  val (down, publisher) = Source
    .actorRef[Message](1000, OverflowStrategy.fail)
    .toMat(Sink.asPublisher(fanout = false))(Keep.both)
    .run()

  val streamRef: Future[SourceRef[Message]] = Source.fromPublisher(publisher).runWith(StreamRefs.sourceRef())
  var id: String = ""

  override def receive: Receive = receivePassivate orElse {
    case CreateWs(newId) => {
      println(s"CreateWs ${id}")
      if (id.isEmpty) {
        id = newId
        streamRef.map(r => CreatedWs(Some(r))).pipeTo(sender())
      }
      else {
        sender() ! CreatedWs(None)
      }
    }
    case SendWs(id: String, msg: String) => {
      println(s"SendWs ${id} ${msg}")
      if (down != null) {
        down ! TextMessage.apply(msg)
      }
    }
    case _ =>
      println(">>>>>>>>>>>>ERROREEE<<<<<<<<<<<")
  }

  private def receivePassivate: Receive = {
    case ReceiveTimeout => context.parent ! ShardRegion.Passivate
    case ShardRegion.Passivate => context.stop(self)
  }
}

object ManagerActor {

  val numberOfShards = 100
  val actorName = "manager-actor"
  val extractEntityId: ShardRegion.ExtractEntityId = {
    case m: CreateWs => {
      println("MAN extractEntityId", m.id)
      (m.id, m)
    }
    case m: SendWs => {
      println("MAN extractEntityId", m.id)
      (m.id, m)
    }
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    def computeShardId(entityId: ShardRegion.EntityId): ShardRegion.ShardId =
      (math.abs(entityId.hashCode()) % numberOfShards).toString

    {
      case m: CreateWs => computeShardId(m.id)
      case m: SendWs => computeShardId(m.id)
      case ShardRegion.StartEntity(id) => computeShardId(id)
    }
  }

  def props(): Props = Props(new ManagerActor())

  case class CreateWs(id: String)

  case class CreatedWs(option: Option[SourceRef[Message]])

  case class SendWs(id: String, msg: String)

}