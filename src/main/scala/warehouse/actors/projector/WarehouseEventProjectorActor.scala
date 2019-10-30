package warehouse.actors.projector

import akka.actor.{ActorSystem, Props}
import warehouse.actors.projector.export.WarehouseLogExporter
import warehouse.actors.write.WarehouseActor
import warehouse.domain.Warehouse.WarehouseEvt

class WarehouseEventProjectorActor(indexer: WarehouseLogExporter) extends CommonEventProjectorActor[WarehouseEvt] {
  override implicit val system: ActorSystem = context.system
  override val indexerInside: WarehouseLogExporter = indexer
  override val detailsTag: String = WarehouseActor.detailsTag
}

object WarehouseEventProjectorActor {
  val name = "warehouse-event-projector-actor"

  def props(indexer: WarehouseLogExporter): Props = Props(new WarehouseEventProjectorActor(indexer))
}