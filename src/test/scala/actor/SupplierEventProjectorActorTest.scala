package actor

import akka.actor.{ActorSystem, PoisonPill}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.persistence.inmemory.util.UUIDs
import akka.persistence.query.TimeBasedUUID
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKit}
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, Inside, Matchers}
import warehouse.AppConfig
import warehouse.actors.projector.SupplierEventProjectorActor
import warehouse.actors.projector.export.SupplierLogExporter
import warehouse.actors.write.{SupplierActor, WarehouseActor}
import warehouse.domain.Supplier.SupplierEvt
import warehouse.domain.{Supplier, Warehouse}

class SupplierEventProjectorActorTest
  extends TestKit(ActorSystem(AppConfig.serviceName))
    with DefaultTimeout
    with ImplicitSender
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with Inside {

  val dbFilePath = AppConfig.dbFilePathTest
  val offsetFilePath = AppConfig.offsetFilePathTest
  val projector = system.actorOf(
    ClusterSingletonManager.props(
      singletonProps = SupplierEventProjectorActor.props(new SupplierLogExporter(dbFilePath, offsetFilePath)),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system)
    ),
    SupplierEventProjectorActor.name
  )

  val cluster = ClusterSharding(system).start(
    typeName = SupplierActor.actorName,
    entityProps = SupplierActor.props,
    settings = ClusterShardingSettings(system),
    extractEntityId = SupplierActor.extractEntityId,
    extractShardId = SupplierActor.extractShardId
  )

  val clusterWa = ClusterSharding(system).start(
    typeName = WarehouseActor.actorName,
    entityProps = WarehouseActor.props,
    settings = ClusterShardingSettings(system),
    extractEntityId = WarehouseActor.extractEntityId,
    extractShardId = WarehouseActor.extractShardId
  )

  val timeBasedUUID = TimeBasedUUID(UUIDs.timeBased())

  override def afterAll: Unit = {
    shutdown()
  }

  "projection" when {
    "some events are received" should {
      "generate updates" in {
        Warehouse.Created("test")
        val event1_1 = Supplier.Created("sup1")
        val event2 = Supplier.AddedProduct("sup1", "test", "prod1")
        val event3 = Supplier.AddedProduct("sup1", "test", "prod2")
        val events: Seq[SupplierEvt] = Seq(event1_1, event2, event3)

        val indexer = new SupplierLogExporter(dbFilePath, offsetFilePath)
        val esRequests =
          indexer.indexEvents(events, timeBasedUUID)
        esRequests should be(Right(timeBasedUUID))
      }
    }
  }

  "projectior" when {
    "some events are received" should {
      "generate updates" in {
        val event = Supplier.Created("test")
        val indexer = new SupplierLogExporter(dbFilePath, offsetFilePath)
        val esRequests = indexer.project(event, timeBasedUUID)
        esRequests should be(Right(timeBasedUUID))
      }
    }
  }

  "offset writer" when {
    "some offsets are received" should {
      "generate updates" in {
        val indexer = new SupplierLogExporter(dbFilePath, offsetFilePath)
        val esRequests = indexer.writeOffset(timeBasedUUID)
        esRequests should be(Right(timeBasedUUID))
      }
    }
  }

  "offset reader" when {
    "some offset requests are received" should {
      "read latest indexed offset from file" in {
        val indexer = new SupplierLogExporter(dbFilePath, offsetFilePath)
        val esRequests = indexer.readOffset()
        esRequests should be(Some(timeBasedUUID))
      }
    }
  }
}
