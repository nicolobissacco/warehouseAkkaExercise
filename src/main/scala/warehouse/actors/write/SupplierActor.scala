package warehouse.actors.write

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.util.Timeout
import warehouse.AppConfig
import warehouse.actors.Message
import warehouse.actors.Message.{ProductMessageDone, ProductMessageError}
import warehouse.domain.Supplier.{ObtainedSupplier, SupplierCmd, SupplierEvt}
import warehouse.domain.{Supplier, Warehouse}

import scala.concurrent.duration._

class SupplierActor extends Actor with PersistentActor with ActorSharding with ActorLogging {
  override implicit val system: ActorSystem = context.system
  private implicit val timeout: Timeout = Timeout(AppConfig.askTimeout)

  override def persistenceId: String = s"${SupplierActor.actorName}-${self.path.name}"

  var state: Supplier = Supplier.emptySupplier

  val snapShotInterval = 10

  context.setReceiveTimeout(120.seconds)

  def update(state: Supplier, event: SupplierEvt): Supplier = event.applyTo(state)

  private def receivePassivate: Receive = {
    case ReceiveTimeout => context.parent ! ShardRegion.Passivate
    case ShardRegion.Passivate => context.stop(self)
  }

  override def receiveRecover: Receive = receivePassivate orElse {
    case RecoveryCompleted => println("Recovery completed!")
    case event: SupplierEvt =>
      state = update(state, event)
      println("SU RECOVER EVT", event, state)
    case SnapshotOffer(_, snapshot: Supplier) =>
      state = snapshot
    case unknown => println(s"Unknown message in receiveRecover: $unknown")
  }

  override def receiveCommand: Receive = {
    case cmd: SupplierCmd =>
      cmd.applyTo(state) match {
        case Right(Some(Supplier.AddedProduct(supplierId, warehouseId, productId))) =>
          warehouseRegion ! Warehouse.AddProduct(warehouseId, supplierId, productId, sender())
        case Right(Some(event: ObtainedSupplier)) =>
          println("SU RECEIVE CMD ObtainedSupplier", cmd, state)
          sender() ! event.applyTo(state)
        case Right(Some(event)) =>
          persistEvent(event, sender())
        case Right(None) => sender() ! Done
        case Left(error) =>
          println(cmd, error)
          sender() ! error
      }
    case msg: ProductMessageDone =>
      msg.actor ! Done
    case msg: ProductMessageError =>
      msg.actor ! msg.msg
  }

  private def persistEvent(event: SupplierEvt, actor: ActorRef): Unit = {
    persist(event) { _ =>
      state = update(state, event)
      if (lastSequenceNr != 0 && lastSequenceNr % snapShotInterval == 0) {
        saveSnapshot(state)
      }
      println("SU PERSIST", event, state)
      actor ! Done
    }
  }
}

object SupplierActor {
  val numberOfShards = 100
  val actorName = "supplier-writer-actor"
  val detailsTag: String = "supplierTag"

  def props(): Props = Props(new SupplierActor())

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case m: SupplierCmd => {
      println("SU extractEntityId", m.supplierId)
      (m.supplierId, m)
    }
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    def computeShardId(entityId: ShardRegion.EntityId): ShardRegion.ShardId =
      (math.abs(entityId.hashCode()) % numberOfShards).toString

    {
      case m: SupplierCmd => computeShardId(m.supplierId.toString)
      case ShardRegion.StartEntity(id) => computeShardId(id)
    }
  }
}