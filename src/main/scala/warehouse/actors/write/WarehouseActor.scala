package warehouse.actors.write

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorSystem, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.util.Timeout
import warehouse.AppConfig
import warehouse.actors.Message.{ProductMessageDone, ProductMessageError}
import warehouse.domain.Warehouse
import warehouse.domain.Warehouse.{ObtainedWarehouse, WarehouseCmd, WarehouseEvt}

import scala.concurrent.duration._

class WarehouseActor extends Actor with PersistentActor with ActorSharding with ActorLogging {
  override implicit val system: ActorSystem = context.system
  private implicit val timeout: Timeout = Timeout(AppConfig.askTimeout)

  override def persistenceId: String = s"${WarehouseActor.actorName}-${self.path.name}"

  var state: Warehouse = Warehouse.emptyWarehouse

  val snapShotInterval = 10

  context.setReceiveTimeout(120.seconds)

  def update(state: Warehouse, event: Warehouse.WarehouseEvt): Warehouse = event.applyTo(state)

  private def receivePassivate: Receive = {
    case ReceiveTimeout => context.parent ! ShardRegion.Passivate
    case ShardRegion.Passivate => context.stop(self)
  }

  override def receiveRecover: Receive = receivePassivate orElse {
    case RecoveryCompleted => println("Recovery completed!")
    case event: Warehouse.WarehouseEvt =>
      state = update(state, event)
      println("WA RECOVER EVT", event, state)
    case SnapshotOffer(_, snapshot: Warehouse) =>
      state = snapshot
    case unknown => println(s"Unknown message in receiveRecover: $unknown")
  }

  override def receiveCommand: Receive = {
    case cmd: Warehouse.AddProduct =>
      cmd.applyTo(state) match {
        case Right(Some(event)) =>
          persistEvent(event, ProductMessageDone(cmd.actor))
        case Right(None) => sender() ! ProductMessageDone(cmd.actor)
        case Left(error) =>
          println(cmd, error)
          sender() ! ProductMessageError(cmd.actor, error)
      }
    case cmd: Warehouse.WarehouseCmd =>
      cmd.applyTo(state) match {
        case Right(Some(event: ObtainedWarehouse)) =>
          println("WA RECEIVE CMD ObtainedWarehouse", cmd, state)
          sender() ! event.applyTo(state)
        case Right(Some(event)) =>
          persistEvent(event)
        case Right(None) => sender() ! Done
        case Left(error) =>
          println(cmd, error)
          sender() ! error
      }
  }

  private def commonPersistEvent(event: WarehouseEvt): Unit = {
    state = update(state, event)
    if (lastSequenceNr != 0 && lastSequenceNr % snapShotInterval == 0) {
      saveSnapshot(state)
    }
    println("WA PERSIST", event, state)
  }

  private def persistEvent(event: WarehouseEvt): Unit = {
    persist(event) { _ =>
      commonPersistEvent(event)
      sender() ! Done
    }
  }

  private def persistEvent(event: WarehouseEvt, msg: ProductMessageDone): Unit = {
    persist(event) { _ =>
      commonPersistEvent(event)
      sender() ! msg
    }
  }
}

object WarehouseActor {
  val numberOfShards = 100
  val actorName = "warehouse-writer-actor"
  val detailsTag: String = "warehouseTag"

  def props(): Props = Props(new WarehouseActor())

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case m: WarehouseCmd => {
      println("WA extractEntityId", m.warehouseId)
      (m.warehouseId, m)
    }
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    def computeShardId(entityId: ShardRegion.EntityId): ShardRegion.ShardId =
      (math.abs(entityId.hashCode()) % numberOfShards).toString

    {
      case m: WarehouseCmd => computeShardId(m.warehouseId.toString)
      case ShardRegion.StartEntity(id) => computeShardId(id)
    }
  }
}