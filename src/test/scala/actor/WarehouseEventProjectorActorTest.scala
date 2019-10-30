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
import warehouse.actors.projector.WarehouseEventProjectorActor
import warehouse.actors.projector.export.WarehouseLogExporter
import warehouse.actors.write.WarehouseActor
import warehouse.domain.Warehouse
import warehouse.domain.Warehouse.WarehouseEvt

class WarehouseEventProjectorActorTest
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
      singletonProps = WarehouseEventProjectorActor.props(new WarehouseLogExporter(dbFilePath, offsetFilePath)),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system)
    ),
    WarehouseEventProjectorActor.name
  )

  val cluster = ClusterSharding(system).start(
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
        val event1 = Warehouse.Created("test")
        val event2 = Warehouse.AddedProduct("test", "prod1")
        val event3 = Warehouse.AddedProduct("test", "prod2")
        val event4 = Warehouse.RemovedProduct("test", "prod2")
        val events: Seq[WarehouseEvt] = Seq(event1, event2, event3, event4)

        val indexer = new WarehouseLogExporter(dbFilePath, offsetFilePath)
        val esRequests =
          indexer.indexEvents(events, timeBasedUUID)
        esRequests should be(Right(timeBasedUUID))
      }
    }
  }

  "projector" when {
    "some events are received" should {
      "generate updates" in {
        val event = Warehouse.Created("test")
        val indexer = new WarehouseLogExporter(dbFilePath, offsetFilePath)
        val esRequests = indexer.project(event, timeBasedUUID)
        esRequests should be(Right(timeBasedUUID))
      }
    }
  }

  "offset writer" when {
    "some offsets are received" should {
      "generate updates" in {
        val indexer = new WarehouseLogExporter(dbFilePath, offsetFilePath)
        val esRequests = indexer.writeOffset(timeBasedUUID)
        esRequests should be(Right(timeBasedUUID))
      }
    }
  }

  "offset reader" when {
    "some offset requests are received" should {
      "read latest indexed offset from file" in {
        val indexer = new WarehouseLogExporter(dbFilePath, offsetFilePath)
        val esRequests = indexer.readOffset()
        esRequests should be(Some(timeBasedUUID))
      }
    }
  }
}
