package warehouse.actors.write

import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.sharding.ClusterSharding
import warehouse.actors.webSocket.{WsConnectionActor, WsManagerActor}

trait ActorSharding {
  implicit val system: ActorSystem

  def warehouseRegion: ActorRef = ClusterSharding(system).shardRegion(WarehouseActor.actorName)

  def supplierRegion: ActorRef = ClusterSharding(system).shardRegion(SupplierActor.actorName)

  def managerRegion: ActorRef = ClusterSharding(system).shardRegion(WsManagerActor.actorName)
}