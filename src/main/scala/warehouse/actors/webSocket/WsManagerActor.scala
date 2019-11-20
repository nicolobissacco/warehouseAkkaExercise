package warehouse.actors.webSocket

import akka.actor.{Actor, ActorRef, ActorSystem, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.http.scaladsl.model.ws.TextMessage
import akka.util.Timeout
import warehouse.AppConfig
import warehouse.actors.webSocket.WsConnectionActor.WsConnectionCreated
import warehouse.actors.webSocket.WsManagerActor.{CreateWsConnection, SendMessageWs}
import warehouse.actors.write.ActorSharding

import scala.concurrent.duration._

class WsManagerActor(var tenant: String) extends Actor with ActorSharding {
  private implicit val timeout: Timeout = Timeout(AppConfig.askTimeout)
  override implicit val system: ActorSystem = context.system
  context.setReceiveTimeout(120.seconds)

  var wsConnections: Map[String, ActorRef] = Map.empty

  private def receivePassivate: Receive = {
    case ReceiveTimeout => context.parent ! ShardRegion.Passivate
    case ShardRegion.Passivate => context.stop(self)
  }

  override def receive: Receive = receivePassivate orElse {
    case cmd: CreateWsConnection => {
      println(s"WsManagerActor->CreateWsConnection ${cmd.tenant}")
      if (tenant.isEmpty) {
        tenant = cmd.tenant
      }

      if (wsConnections.get(cmd.id).isEmpty) {
        //creo attore figlio
        val connectionActorRef = context.actorOf(WsConnectionActor.props(cmd.id), WsConnectionActor.actorName + cmd.id)
        //lo aggiungo alla lista delle connessioni
        wsConnections += (cmd.id -> connectionActorRef)
        //gli dico di creare la connessione e di rispondere al chiamante
        connectionActorRef forward cmd
      } else {
        WsConnectionCreated(None)
      }
    }
    case SendMessageWs(tenant: String, msg: String) => {
      println(s"WsManagerActor->SendMessageWs ${tenant} ${msg}")
      //faccio broadcast a tutte le connessioni figlie
      wsConnections.foreach(wsCon => {
        wsCon._2 ! TextMessage.apply(msg)
      })
    }
    case _ =>
      println("WsManagerActor->>>>>>>>>>>>>ERROREEE<<<<<<<<<<<")
  }
}

object WsManagerActor {

  val numberOfShards = 100
  val actorName = "ws-manager-actor"
  val extractEntityId: ShardRegion.ExtractEntityId = {
    case m: CreateWsConnection => {
      println("WsManagerActor->extractEntityId", m.tenant)
      (m.tenant, m)
    }
    case m: SendMessageWs => {
      println("WsManagerActor->extractEntityId", m.tenant)
      (m.tenant, m)
    }
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    def computeShardId(entityId: ShardRegion.EntityId): ShardRegion.ShardId =
      (math.abs(entityId.hashCode()) % numberOfShards).toString

    {
      case m: CreateWsConnection => computeShardId(m.tenant)
      case m: SendMessageWs => computeShardId(m.tenant)
      case ShardRegion.StartEntity(id) => computeShardId(id)
    }
  }

  def props(): Props = Props(new WsManagerActor(tenant = ""))

  case class CreateWsConnection(tenant: String, id: String)

  case class SendMessageWs(tenant: String, msg: String)

}