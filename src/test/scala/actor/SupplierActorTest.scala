package actor

import akka.Done
import akka.actor.ActorSystem
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKit}
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, Inside, Matchers}
import warehouse.AppConfig
import warehouse.actors.write.{SupplierActor, WarehouseActor}
import warehouse.domain.{Supplier, Warehouse}

import scala.concurrent.duration._

class SupplierActorTest
  extends TestKit(ActorSystem(AppConfig.serviceName))
    with DefaultTimeout
    with ImplicitSender
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with Inside {

  val cluster = ClusterSharding(system).start(
    typeName = SupplierActor.actorName,
    entityProps = SupplierActor.props(),
    settings = ClusterShardingSettings(system),
    extractEntityId = SupplierActor.extractEntityId,
    extractShardId = SupplierActor.extractShardId
  )

  val clusterWa = ClusterSharding(system).start(
    typeName = WarehouseActor.actorName,
    entityProps = WarehouseActor.props(),
    settings = ClusterShardingSettings(system),
    extractEntityId = WarehouseActor.extractEntityId,
    extractShardId = WarehouseActor.extractShardId
  )

  override def afterAll: Unit = {
    shutdown()
  }

  "SupplierActor" must {
    "perform create commands" in {
      cluster ! Supplier.Create("test")
      expectMsg(10 seconds, Done)

      cluster ! Supplier.Create("test")
      expectMsg(10 seconds, Done)

      cluster ! Supplier.Create("test2")
      expectMsg(10 seconds, Done)
    }
  }

  "SupplierActor" must {
    "perform addProduct commands" in {
      clusterWa ! Warehouse.Create("wa")
      expectMsg(10 seconds, Done)

      cluster ! Supplier.Create("test3")
      expectMsg(10 seconds, Done)

      cluster ! Supplier.AddProduct("random", "wa", "prod1")
      expectMsg(10 seconds, "Supplier not found")

      cluster ! Supplier.AddProduct("test3", "wa", "prod1")
      expectMsg(10 seconds, Done)
    }
  }
}
