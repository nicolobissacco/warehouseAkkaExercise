package warehouse.actors.write

import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.sharding.ClusterSharding
import warehouse.actors.ManagerActor

trait ActorSharding {
  /** Provides either an ActorSystem for spawning actors. */
  implicit val system: ActorSystem

  def warehouseRegion: ActorRef = ClusterSharding(system).shardRegion(WarehouseActor.actorName)

  def supplierRegion: ActorRef = ClusterSharding(system).shardRegion(SupplierActor.actorName)

  def managerRegion: ActorRef = ClusterSharding(system).shardRegion(ManagerActor.actorName)
}