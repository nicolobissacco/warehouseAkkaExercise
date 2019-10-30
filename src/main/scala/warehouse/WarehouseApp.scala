package warehouse

import akka.Done
import akka.actor.{ActorSystem, PoisonPill, Scheduler}
import akka.cluster.Cluster
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.dispatch.MessageDispatcher
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import warehouse.actors.projector.WarehouseEventProjectorActor
import warehouse.actors.projector.export.WarehouseLogExporter
import warehouse.actors.write.{ActorSharding, WarehouseActor}
import warehouse.domain.Warehouse
import warehouse.domain.Warehouse.WarehouseCmd

import scala.concurrent.Await
import scala.concurrent.duration._

object WarehouseApp extends HttpApp with ActorSharding with App {
  override implicit val system: ActorSystem = ActorSystem(AppConfig.serviceName, ConfigFactory.load())

  private implicit val scheduler: Scheduler = system.scheduler

  private lazy val cluster = Cluster(system)
  private implicit val timeout: Timeout = Timeout(AppConfig.askTimeout)

  val dbFilePath = AppConfig.dbFilePath
  val offsetFilePath = AppConfig.offsetFilePath

  private implicit val blockingDispatcher: MessageDispatcher =
    system.dispatchers.lookup(id = "warehouse-exercise-blocking-dispatcher")

  startSystem()

  if (AppConfig.akkaClusterBootstrapKubernetes) {
    // Akka Management hosts the HTTP routes used by bootstrap
    AkkaManagement(system).start()
    // Starting the bootstrap process needs to be done explicitly
    ClusterBootstrap(system).start()
  }

  cluster.registerOnMemberUp({
    println(s"Member up: ${cluster.selfAddress}")
  })

  cluster.registerOnMemberRemoved({
    println(s"Member removed: ${cluster.selfAddress}")
    cluster.leave(cluster.selfAddress)
  })

  private def startSystem(): Unit = {
    createClusterSingletonActors()
    // This will start the server until the return key is pressed
    createClusterShardingActors()
    startServer(AppConfig.serviceInterface, AppConfig.servicePort, system)

    stopSystem()
  }

  private def stopSystem(): Unit = {
    println(s"Terminating member: ${cluster.selfAddress}")
    system.terminate()
    Await.result(system.whenTerminated, 60.seconds)
  }

  private def createClusterShardingActors(): Unit = {
    ClusterSharding(system).start(
      typeName = WarehouseActor.actorName,
      entityProps = WarehouseActor.props(),
      settings = ClusterShardingSettings(system),
      extractEntityId = WarehouseActor.extractEntityId,
      extractShardId = WarehouseActor.extractShardId
    )
  }

  private def createClusterSingletonActors(): Unit = {
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = WarehouseEventProjectorActor.props(new WarehouseLogExporter(dbFilePath, offsetFilePath)),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system)
      ),
      WarehouseEventProjectorActor.name
    )
  }

  def routes: Route = concat(
    path("createWarehouse") {
      post {
        entity(as[Warehouse.Create])(forwardRequest)
      }
    },
    path("addProduct") {
      post {
        entity(as[Warehouse.AddProduct])(forwardRequest)
      }
    },
    path("removeProduct") {
      post {
        entity(as[Warehouse.RemoveProduct])(forwardRequest)
      }
    }
  )

  def forwardRequest[R <: WarehouseCmd]: R => Route =
    (request: R) => {
      onSuccess(warehouseRegion ? request) {
        case Done => complete(StatusCodes.OK -> s"$request")
        case e => complete(StatusCodes.BadRequest -> e.toString)
      }
    }
}
