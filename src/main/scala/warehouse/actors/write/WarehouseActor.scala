package warehouse.actors.write

import akka.Done
import akka.actor.{Actor, ActorSystem, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import warehouse.domain.Warehouse
import warehouse.domain.Warehouse.WarehouseCmd

import scala.concurrent.duration._

class WarehouseActor extends Actor with PersistentActor with ActorSharding {
  override implicit val system: ActorSystem = context.system

  override def persistenceId: String = s"${WarehouseActor.actorName}"

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
      println("EVT", event, state)
    case SnapshotOffer(_, snapshot: Warehouse) =>
      println("SNAPSHOTOFFER")
      state = snapshot
    case unknown => println(s"Unknown message in receiveRecover: $unknown")
  }

  override def receiveCommand: Receive = {
    case cmd: Warehouse.WarehouseCmd =>
      cmd.applyTo(state) match {
        case Right(Some(event)) =>
          persist(event) { _ =>
            state = update(state, event)
            if (lastSequenceNr != 0 && lastSequenceNr % snapShotInterval == 0) {
              saveSnapshot(state)
              println("SNAPSHOT")
            }
            println("CMD", cmd, state)
            sender() ! Done
          }
        case Right(None) => sender() ! Done
        case Left(error) =>
          println(cmd, error)
          sender() ! error
      }

  }
}

object WarehouseActor {
  val numberOfShards = 100
  val actorName = "warehouse-writer-actor"
  val detailsTag: String = "warehouseTag"

  def props: Props = Props(new WarehouseActor())

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case m: WarehouseCmd => (m.warehouseId, m)
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