package warehouse.actors.projector

import akka.actor.{ActorSystem, Props}
import warehouse.actors.projector.export.CustomerLogExporter
import warehouse.actors.write.CustomerActor
import warehouse.domain.Customer.CustomerEvt

class CustomerEventProjectorActor(indexer: CustomerLogExporter) extends CommonEventProjectorActor[CustomerEvt] {
  override implicit val system: ActorSystem = context.system
  override val indexerInside: CustomerLogExporter = indexer
  override val detailsTag: String = CustomerActor.detailsTag
}

object CustomerEventProjectorActor {
  val name = "customer-event-projector-actor"

  def props(indexer: CustomerLogExporter): Props = Props(new CustomerEventProjectorActor(indexer))
}