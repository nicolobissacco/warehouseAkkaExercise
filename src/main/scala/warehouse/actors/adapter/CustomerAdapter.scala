package warehouse.actors.adapter

import akka.persistence.journal.{EventAdapter, EventSeq, Tagged}
import warehouse.actors.write.CustomerActor
import warehouse.domain.Customer

class CustomerAdapter extends EventAdapter {
  override def manifest(event: Any): String = event.getClass.getSimpleName

  val tag = Set(CustomerActor.detailsTag)

  override def fromJournal(event: Any, manifest: String): EventSeq = event match {
    case event: Customer.Created => EventSeq(event)
  }

  override def toJournal(event: Any): Any = event match {
    case event: Customer.Created => Tagged(event, tag)
  }
}
