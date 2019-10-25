package warehouse.actors.adapter

import warehouse.actors.write.WarehouseActor
import akka.persistence.journal.{EventAdapter, EventSeq, Tagged}
import warehouse.domain.Warehouse

class WarehouseAdapter extends EventAdapter {
  override def manifest(event: Any): String = event.getClass.getSimpleName

  val tag = Set(WarehouseActor.detailsTag)

  override def fromJournal(event: Any, manifest: String): EventSeq = event match {
    case event: Warehouse.Created => EventSeq(event)
    case event: Warehouse.AddedProduct => EventSeq(event)
    case event: Warehouse.RemovedProduct => EventSeq(event)
  }

  override def toJournal(event: Any): Any = event match {
    case event: Warehouse.Created => Tagged(event, tag)
    case event: Warehouse.AddedProduct => Tagged(event, tag)
    case event: Warehouse.RemovedProduct => Tagged(event, tag)
  }
}
