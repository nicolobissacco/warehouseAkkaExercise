package warehouse.actors.projector.export

import warehouse.domain.Customer.CustomerEvt

class CustomerLogExporter(eventsFile: String, offsetFile: String) extends CommonLogExporter[CustomerEvt] {
  override val name: String = "customer-export"
  override val eventsTag: String = "customer-details"
  override val eventsFilePath: String = eventsFile
  override val offsetFilePath: String = offsetFile
}
