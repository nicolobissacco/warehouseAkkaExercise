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
    entityProps = WarehouseActor.props,
    settings = ClusterShardingSettings(system),
    extractEntityId = WarehouseActor.extractEntityId,
    extractShardId = WarehouseActor.extractShardId
  )

  override def afterAll: Unit = {
    shutdown()
  }

  "WarehouseActor" must {
    "perform commands" in {
      cluster ! Warehouse.Create("test")
      expectMsg(10 seconds, Done)

      cluster ! Warehouse.AddProduct("test", "prod1")
      expectMsg(10 seconds, Done)

      cluster ! Warehouse.RemoveProduct("test", "prod1")
      expectMsg(10 seconds, Done)
    }
  }

  "Add and Remove product commands" must {
    "be executed by the correct warehouse" in {
      cluster ! Warehouse.AddProduct("random", "prod1")
      expectMsg(10 seconds, "Wrong warehouse")

      cluster ! Warehouse.RemoveProduct("name", "prod1")
      expectMsg(10 seconds, "Wrong warehouse")
    }
  }

  "Remove product command" must {
    "be executed by the correct product" in {
      cluster ! Warehouse.Create("test")
      expectMsg(10 seconds, Done)

      cluster ! Warehouse.AddProduct("test", "prod1")
      expectMsg(10 seconds, Done)

      cluster ! Warehouse.RemoveProduct("test", "prod2")
      expectMsg(10 seconds, "No product found")
    }
  }

  "A create command" must {
    "do nothing if warehouse already exists" in {
      cluster ! Warehouse.Create("test")
      expectMsg(10 seconds, Done)

      cluster ! Warehouse.Create("test")
      expectMsg(10 seconds, Done)
    }
  }

  "A create command" must {
    "create another warehouse if the id is different" in {
      cluster ! Warehouse.Create("test")
      expectMsg(10 seconds, Done)

      cluster ! Warehouse.Create("test2")
      expectMsg(10 seconds, Done)
    }
  }

  /*"A create command" must {
    "generate an error if try to create another warehouse on the same actor" in {
      cluster ! Warehouse.Create("test")
      expectMsg(10 seconds, Done)

      cluster ! Warehouse.Create("test2")
      expectMsg(10 seconds, "Warehouse creation error: actor already init")
    }
  }*/

}
