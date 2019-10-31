package warehouse.domain

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import warehouse.domain.Domain.{DomainCommand, DomainEntity, DomainEvent}

case class Warehouse(warehouseId: String, products: List[Product]) extends DomainEntity

object Warehouse {

  val emptyWarehouse = Warehouse("", List.empty[Product])

  sealed trait WarehouseCmd extends DomainCommand[Warehouse, WarehouseEvt] {
    val warehouseId: String
  }

  sealed trait WarehouseEvt extends DomainEvent[Warehouse] {
    val warehouseId: String
  }

  implicit val dCreate: Decoder[Create] = deriveDecoder[Create]
  implicit val eCreate: Encoder[Create] = deriveEncoder[Create]

  case class Create(warehouseId: String) extends WarehouseCmd {
    override def applyTo(domainEntity: Warehouse): Either[String, Option[WarehouseEvt]] = {
      println("WA CMD create applyTo", domainEntity.warehouseId, warehouseId)
      domainEntity match {
        case Warehouse.emptyWarehouse => Right(Some(Created(warehouseId)))
        case _ if domainEntity.warehouseId == warehouseId => Right(None)
        case _ => Left("Warehouse creation error: actor already init")
      }
    }
  }

  case class Created(warehouseId: String) extends WarehouseEvt {
    override def applyTo(domainEntity: Warehouse): Warehouse = {
      println("WA EVT Created applyTo", domainEntity.warehouseId, warehouseId)
      domainEntity.copy(warehouseId = warehouseId)
    }
  }

  implicit val dAddProduct: Decoder[AddProduct] = deriveDecoder[AddProduct]
  implicit val eAddProduct: Encoder[AddProduct] = deriveEncoder[AddProduct]

  case class AddProduct(warehouseId: String, supplierId: String, productId: String) extends WarehouseCmd {
    override def applyTo(domainEntity: Warehouse): Either[String, Option[WarehouseEvt]] = {
      println("WA CMD AddProduct applyTo", domainEntity.warehouseId, warehouseId)
      if (warehouseId == domainEntity.warehouseId) {
        if (domainEntity.products.exists(_.productId == productId)) {
          val findElem = domainEntity.products.find(x => x.productId == productId && x.supplierId == supplierId)
          findElem match {
            case Some(_: Product) => Left("Product already in warehouse for supplier")
            case _ => Right(Some(AddedProduct(warehouseId, supplierId, productId)))
          }
        }
        else {
          Right(Some(AddedProduct(warehouseId, supplierId, productId)))
        }
      } else {
        Left("Wrong warehouse")
      }
    }
  }

  case class AddedProduct(warehouseId: String, supplierId: String, productId: String) extends WarehouseEvt {
    override def applyTo(domainEntity: Warehouse): Warehouse = {
      println("WA EVT AddedProduct applyTo", domainEntity.warehouseId, warehouseId)
      domainEntity.copy(products = domainEntity.products :+ Product(productId, supplierId))
    }
  }

  implicit val dRemoveProduct: Decoder[RemoveProduct] = deriveDecoder[RemoveProduct]
  implicit val eRemoveProduct: Encoder[RemoveProduct] = deriveEncoder[RemoveProduct]

  case class RemoveProduct(warehouseId: String, supplierId: String, productId: String) extends WarehouseCmd {
    override def applyTo(domainEntity: Warehouse): Either[String, Option[WarehouseEvt]] = {
      println("WA CMD RemoveProduct applyTo", domainEntity.warehouseId, warehouseId)
      if (warehouseId == domainEntity.warehouseId) {
        if (domainEntity.products.exists(_.productId == productId)) {
          val prodOfSupl = domainEntity.products.find(x => x.productId == productId && x.supplierId == supplierId)
          prodOfSupl match {
            case Some(_: Product) => Right(Option(RemovedProduct(warehouseId, supplierId, productId)))
            case _ => Left("No product found for supplier")
          }
        }
        else {
          Left("No product found")
        }
      } else {
        Left("Wrong warehouse")
      }
    }
  }

  case class RemovedProduct(warehouseId: String, supplierId: String, productId: String) extends WarehouseEvt {
    override def applyTo(domainEntity: Warehouse): Warehouse = {
      println("WA EVT RemovedProduct applyTo", domainEntity.warehouseId, warehouseId)
      domainEntity.copy(products = domainEntity.products.filter(x => !(x.productId == productId && x.supplierId == supplierId)))
    }
  }

  implicit val dGetWarehouse: Decoder[GetWarehouse] = deriveDecoder[GetWarehouse]
  implicit val eGetWarehouse: Encoder[GetWarehouse] = deriveEncoder[GetWarehouse]

  case class GetWarehouse(warehouseId: String) extends WarehouseCmd {
    override def applyTo(domainEntity: Warehouse): Either[String, Option[WarehouseEvt]] = {
      println("WA CMD GetWarehouse applyTo", domainEntity.warehouseId, warehouseId)
      domainEntity match {
        case _ if domainEntity.warehouseId == warehouseId => Right(Some(ObtainedWarehouse(warehouseId)))
        case _ => Left("Warehouse get error: not found")
      }
    }
  }

  case class ObtainedWarehouse(warehouseId: String) extends WarehouseEvt {
    override def applyTo(domainEntity: Warehouse): Warehouse = {
      println("WA EVT ObtainedWarehouse applyTo", domainEntity.warehouseId, warehouseId)
      domainEntity
    }
  }

}