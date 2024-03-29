package warehouse.actors.adapter

import akka.persistence.journal.{EventAdapter, EventSeq, Tagged}
import warehouse.actors.write.SupplierActor
import warehouse.domain.Supplier

class SupplierAdapter extends EventAdapter {
  override def manifest(event: Any): String = event.getClass.getSimpleName

  val tag = Set(SupplierActor.detailsTag)

  override def fromJournal(event: Any, manifest: String): EventSeq = event match {
    case event: Supplier.Created => EventSeq(event)
  }

  override def toJournal(event: Any): Any = event match {
    case event: Supplier.Created => Tagged(event, tag)
  }
}
