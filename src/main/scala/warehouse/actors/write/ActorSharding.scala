package warehouse.actors.write

import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.sharding.ClusterSharding

trait ActorSharding {
  /** Provides either an ActorSystem for spawning actors. */
  implicit val system: ActorSystem

  def warehouseRegion: ActorRef = ClusterSharding(system).shardRegion(WarehouseActor.actorName)
}