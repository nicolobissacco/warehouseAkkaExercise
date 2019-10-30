package warehouse.actors.projector.export

import warehouse.domain.Supplier.SupplierEvt

class SupplierLogExporter(eventsFile: String, offsetFile: String) extends CommonLogExporter[SupplierEvt] {
  override val name: String = "supplier-export"
  override val eventsTag: String = "supplier-details"
  override val eventsFilePath: String = eventsFile
  override val offsetFilePath: String = offsetFile
}
