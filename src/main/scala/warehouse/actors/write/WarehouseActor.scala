package warehouse.actors.write

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.pattern.{ask, pipe}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.util.Timeout
import warehouse.AppConfig
import warehouse.domain.Warehouse.{ObtainedWarehouse, WarehouseCmd, WarehouseEvt}
import warehouse.domain.{Supplier, Warehouse}

import scala.concurrent.ExecutionContext.Implicits.global
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
    case cmd: Warehouse.WarehouseCmd =>
      cmd.applyTo(state) match {
        case Right(Some(event@Warehouse.AddedProduct(_, supplierId, _))) =>
          context.become(supplierCheck(sender, event))
          val future = supplierRegion ? Supplier.GetSupplier(supplierId)
          future pipeTo self
        case Right(Some(event: ObtainedWarehouse)) =>
          println("WA RECEIVE CMD ObtainedWarehouse", cmd, state)
          sender() ! event.applyTo(state)
        case Right(Some(event)) =>
          persistEvent(event, sender())
        case Right(None) => sender() ! Done
        case Left(error) =>
          println(cmd, error)
          sender() ! error
      }
  }

  private def persistEvent(event: WarehouseEvt, actor: ActorRef): Unit = {
    persist(event) { _ =>
      state = update(state, event)
      if (lastSequenceNr != 0 && lastSequenceNr % snapShotInterval == 0) {
        saveSnapshot(state)
      }
      println("WA PERSIST", event, state)
      actor ! Done
    }
  }

  private def supplierCheck(sender: ActorRef, event: WarehouseEvt): Receive = {
    case _: Supplier =>
      persistEvent(event, sender)
      context.unbecome()
      unstashAll()
    case error: String =>
      context.unbecome()
      sender ! error
      unstashAll()
    case unknown =>
      context.unbecome()
      sender ! "Error supplierCheck"
      unstashAll()
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