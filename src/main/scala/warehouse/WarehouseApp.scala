package warehouse

import akka.Done
import akka.actor.{ActorSystem, PoisonPill, Scheduler}
import akka.cluster.Cluster
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.dispatch.MessageDispatcher
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import warehouse.actors.projector.export.{CustomerLogExporter, SupplierLogExporter, WarehouseLogExporter}
import warehouse.actors.projector.{CustomerEventProjectorActor, SupplierEventProjectorActor, WarehouseEventProjectorActor}
import warehouse.actors.write.{ActorSharding, CustomerActor, SupplierActor, WarehouseActor}
import warehouse.domain.Customer.CustomerCmd
import warehouse.domain.Supplier.SupplierCmd
import warehouse.domain.Warehouse.WarehouseCmd
import warehouse.domain.{Customer, Supplier, Warehouse}

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

    ClusterSharding(system).start(
      typeName = SupplierActor.actorName,
      entityProps = SupplierActor.props(),
      settings = ClusterShardingSettings(system),
      extractEntityId = SupplierActor.extractEntityId,
      extractShardId = SupplierActor.extractShardId
    )

    ClusterSharding(system).start(
      typeName = CustomerActor.actorName,
      entityProps = CustomerActor.props(),
      settings = ClusterShardingSettings(system),
      extractEntityId = CustomerActor.extractEntityId,
      extractShardId = CustomerActor.extractShardId
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

    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = SupplierEventProjectorActor.props(new SupplierLogExporter(dbFilePath, offsetFilePath)),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system)
      ),
      SupplierEventProjectorActor.name
    )

    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = CustomerEventProjectorActor.props(new CustomerLogExporter(dbFilePath, offsetFilePath)),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system)
      ),
      CustomerEventProjectorActor.name
    )
  }

  def routes: Route = concat(
    path("createWarehouse") {
      post {
        entity(as[Warehouse.Create])(warehouseRequest)
      }
    },
    path("getWarehouse") {
      post {
        entity(as[Warehouse.GetWarehouse])(warehouseRequest)
      }
    },

    path("createSupplier") {
      post {
        entity(as[Supplier.Create])(supplierRequest)
      }
    },
    /*path("addProduct") {
      post {
        entity(as[Warehouse.AddProduct])(warehouseRequest)
      }
    },*/
    path("getSupplier") {
      post {
        entity(as[Supplier.GetSupplier])(supplierRequest)
      }
    },

    path("createCustomer") {
      post {
        entity(as[Customer.Create])(customerRequest)
      }
    },
    /*path("removeProduct") {
      post {
        entity(as[Warehouse.RemoveProduct])(warehouseRequest)
      }
    },*/
    path("getCustomer") {
      post {
        entity(as[Customer.GetCustomer])(customerRequest)
      }
    }
  )

  def warehouseRequest[R <: WarehouseCmd]: R => Route = (request: R) => {
    onSuccess(warehouseRegion ? request) {
      case Done => complete(StatusCodes.OK -> s"$request")
      case e => complete(StatusCodes.BadRequest -> e.toString)
    }
  }

  def supplierRequest[R <: SupplierCmd]: R => Route = (request: R) => {
    onSuccess(supplierRegion ? request) {
      case Done => complete(StatusCodes.OK -> s"$request")
      case e => complete(StatusCodes.BadRequest -> e.toString)
    }
  }

  def customerRequest[R <: CustomerCmd]: R => Route = (request: R) => {
    onSuccess(customerRegion ? request) {
      case Done => complete(StatusCodes.OK -> s"$request")
      case e => complete(StatusCodes.BadRequest -> e.toString)
    }
  }
}
