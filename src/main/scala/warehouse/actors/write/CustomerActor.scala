package warehouse.actors.write

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.util.Timeout
import warehouse.AppConfig
import warehouse.domain.Customer
import warehouse.domain.Customer.{CustomerCmd, CustomerEvt, ObtainedCustomer}

import scala.concurrent.duration._

class CustomerActor extends Actor with PersistentActor with ActorSharding with ActorLogging {
  override implicit val system: ActorSystem = context.system
  private implicit val timeout: Timeout = Timeout(AppConfig.askTimeout)
  val snapShotInterval = 10
  var state: Customer = Customer.emptyCustomer

  override def persistenceId: String = s"${CustomerActor.actorName}-${self.path.name}"

  context.setReceiveTimeout(120.seconds)

  override def receiveRecover: Receive = receivePassivate orElse {
    case RecoveryCompleted => println("Recovery completed!")
    case event: CustomerEvt =>
      state = update(state, event)
      println("CU RECOVER EVT", event, state)
    case SnapshotOffer(_, snapshot: Customer) =>
      state = snapshot
    case unknown => println(s"Unknown message in receiveRecover: $unknown")
  }

  private def receivePassivate: Receive = {
    case ReceiveTimeout => context.parent ! ShardRegion.Passivate
    case ShardRegion.Passivate => context.stop(self)
  }

  override def receiveCommand: Receive = {
    case cmd: CustomerCmd =>
      cmd.applyTo(state) match {
        /*case Right(Some(event@Customer.Created(_, warehouseId))) =>
          context.become(afterWarehouseCheck(sender, event))
          val future = warehouseRegion ? Warehouse.GetWarehouse(warehouseId)
          future pipeTo self*/
        case Right(Some(event: ObtainedCustomer)) =>
          println("CU RECEIVE CMD ObtainedCustomer", cmd, state)
          sender() ! event.applyTo(state)
        case Right(Some(event)) =>
          persistEvent(event)
        case Right(None) => sender() ! Done
        case Left(error) =>
          println(cmd, error)
          sender() ! error
      }
  }

  private def persistEvent(event: CustomerEvt): Unit = {
    persistEvent(event, sender())
  }

  private def persistEvent(event: CustomerEvt, actor: ActorRef): Unit = {
    persist(event) { _ =>
      state = update(state, event)
      if (lastSequenceNr != 0 && lastSequenceNr % snapShotInterval == 0) {
        saveSnapshot(state)
      }
      println("CU PERSIST", event, state)
      actor ! Done
    }
  }

  def update(state: Customer, event: CustomerEvt): Customer = event.applyTo(state)

  private def afterWarehouseCheck(sender: ActorRef, event: CustomerEvt): Receive = {
    case _: Customer =>
      persistEvent(event, sender)
      context.unbecome()
      unstashAll()
    case error: String =>
      context.unbecome()
      sender ! error
      unstashAll()
    case unknown =>
      context.unbecome()
      sender ! "Error afterWarehouseCheck"
      unstashAll()
  }
}

object CustomerActor {
  val numberOfShards = 100
  val actorName = "customer-writer-actor"
  val detailsTag: String = "customerTag"

  def props(): Props = Props(new CustomerActor())

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case m: CustomerCmd => {
      println("CU extractEntityId", m.customerId)
      (m.customerId, m)
    }
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    def computeShardId(entityId: ShardRegion.EntityId): ShardRegion.ShardId =
      (math.abs(entityId.hashCode()) % numberOfShards).toString

    {
      case m: CustomerCmd => computeShardId(m.customerId.toString)
      case ShardRegion.StartEntity(id) => computeShardId(id)
    }
  }
}
