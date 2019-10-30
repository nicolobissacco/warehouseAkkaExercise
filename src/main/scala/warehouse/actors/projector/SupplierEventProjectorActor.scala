package warehouse.actors.projector

import akka.actor.{ActorSystem, Props}
import warehouse.actors.projector.export.SupplierLogExporter
import warehouse.actors.write.SupplierActor
import warehouse.domain.Supplier.SupplierEvt

class SupplierEventProjectorActor(indexer: SupplierLogExporter) extends CommonEventProjectorActor[SupplierEvt] {
  override implicit val system: ActorSystem = context.system
  override val indexerInside: SupplierLogExporter = indexer
  override val detailsTag: String = SupplierActor.detailsTag
}

object SupplierEventProjectorActor {
  val name = "supplier-event-projector-actor"

  def props(indexer: SupplierLogExporter): Props = Props(new SupplierEventProjectorActor(indexer))
}