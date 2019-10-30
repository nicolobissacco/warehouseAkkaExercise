package warehouse.actors.projector.export

import warehouse.domain.Warehouse.WarehouseEvt

class WarehouseLogExporter(eventsFile: String, offsetFile: String) extends CommonLogExporter[WarehouseEvt] {
  override val name: String = "warehouse-export"
  override val eventsTag: String = "warehouse-details"
  override val eventsFilePath: String = eventsFile
  override val offsetFilePath: String = offsetFile
}
