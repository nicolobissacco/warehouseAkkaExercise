package warehouse.domain

import warehouse.domain.Domain.{DomainCommand, DomainEntity, DomainEvent}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class Warehouse(warehouseId: String, products: List[String]) extends DomainEntity

object Warehouse {

  val emptyWarehouse = Warehouse("", List.empty[String])

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
      println("DOMAINNNNN", domainEntity.warehouseId, warehouseId)
      domainEntity match {
        case Warehouse.emptyWarehouse => Right(Some(Created(warehouseId)))
        case _ if domainEntity.warehouseId == warehouseId => Right(None)
        case _ => Left("Warehouse creation error: actor already init")
      }
    }
  }

  case class Created(warehouseId: String) extends WarehouseEvt {
    override def applyTo(domainEntity: Warehouse): Warehouse = {
      domainEntity.copy(warehouseId = warehouseId)
    }
  }

  implicit val dAddProduct: Decoder[AddProduct] = deriveDecoder[AddProduct]
  implicit val eAddProduct: Encoder[AddProduct] = deriveEncoder[AddProduct]

  case class AddProduct(warehouseId: String, product: String) extends WarehouseCmd {
    override def applyTo(domainEntity: Warehouse): Either[String, Option[WarehouseEvt]] = {
      if (warehouseId == domainEntity.warehouseId) {
        Right(Some(AddedProduct(warehouseId, product)))
      } else {
        Left("Wrong warehouse")
      }
    }
  }

  case class AddedProduct(warehouseId: String, product: String) extends WarehouseEvt {
    override def applyTo(domainEntity: Warehouse): Warehouse = {
      domainEntity.copy(products = domainEntity.products :+ product)
    }
  }

  implicit val dRemoveProduct: Decoder[RemoveProduct] = deriveDecoder[RemoveProduct]
  implicit val eRemoveProduct: Encoder[RemoveProduct] = deriveEncoder[RemoveProduct]

  case class RemoveProduct(warehouseId: String, product: String) extends WarehouseCmd {
    override def applyTo(domainEntity: Warehouse): Either[String, Option[WarehouseEvt]] = {
      if (warehouseId == domainEntity.warehouseId) {
        if (domainEntity.products.contains(product)) {
          Right(Option(RemovedProduct(warehouseId, product)))
        }
        else {
          Left("No product found")
        }
      } else {
        Left("Wrong warehouse")
      }
    }
  }

  case class RemovedProduct(warehouseId: String, product: String) extends WarehouseEvt {
    override def applyTo(domainEntity: Warehouse): Warehouse = {
      domainEntity.copy(products = domainEntity.products.filter(_ != product))
    }
  }

}