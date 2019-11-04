package actor

import akka.Done
import akka.actor.ActorSystem
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKit}
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, Inside, Matchers}
import warehouse.AppConfig
import warehouse.actors.write.WarehouseActor
import warehouse.domain.Warehouse

import scala.concurrent.duration._

class WarehouseActorTest
  extends TestKit(ActorSystem(AppConfig.serviceName))
    with DefaultTimeout
    with ImplicitSender
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with Inside {

  val cluster = ClusterSharding(system).start(
    typeName = WarehouseActor.actorName,
    entityProps = WarehouseActor.props(),
    settings = ClusterShardingSettings(system),
    extractEntityId = WarehouseActor.extractEntityId,
    extractShardId = WarehouseActor.extractShardId
  )

  override def afterAll: Unit = {
    shutdown()
  }

  "WarehouseActor" must {
    "perform create commands" in {
      cluster ! Warehouse.Create("test")
      expectMsg(10 seconds, Done)

      cluster ! Warehouse.Create("test")
      expectMsg(10 seconds, Done)

      cluster ! Warehouse.Create("test2")
      expectMsg(10 seconds, Done)
    }
  }

  "WarehouseActor" must {
    "perform addProduct commands" in {
      cluster ! Warehouse.Create("test3")
      expectMsg(10 seconds, Done)

      cluster ! Warehouse.AddProduct("test3", "sup1", "prod1")
      expectMsg(10 seconds, Done)

      cluster ! Warehouse.AddProduct("test3", "sup2", "prod1")
      expectMsg(10 seconds, Done)

      cluster ! Warehouse.AddProduct("random", "sup1", "prod1")
      expectMsg(10 seconds, "Wrong warehouse")

      cluster ! Warehouse.AddProduct("test3", "sup1", "prod1")
      expectMsg(10 seconds, "Product already in warehouse for supplier")
    }
  }

  "WarehouseActor" must {
    "perform removeProduct commands" in {
      cluster ! Warehouse.Create("test4")
      expectMsg(10 seconds, Done)

      cluster ! Warehouse.AddProduct("test4", "sup1", "prod1")
      expectMsg(10 seconds, Done)

      cluster ! Warehouse.AddProduct("test4", "sup2", "prod1")
      expectMsg(10 seconds, Done)

      cluster ! Warehouse.RemoveProduct("test4", "sup1", "prod1")
      expectMsg(10 seconds, Done)

      cluster ! Warehouse.RemoveProduct("random", "sup1", "prod1")
      expectMsg(10 seconds, "Wrong warehouse")

      cluster ! Warehouse.RemoveProduct("test4", "sup1", "prod3")
      expectMsg(10 seconds, "No product found")

      cluster ! Warehouse.RemoveProduct("test4", "sup1", "prod1")
      expectMsg(10 seconds, "No product found for supplier")
    }
  }
}
