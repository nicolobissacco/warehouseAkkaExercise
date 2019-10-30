package warehouse.domain

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import warehouse.domain.Domain.{DomainCommand, DomainEntity, DomainEvent}

case class Supplier(supplierId: String, warehouseId: String) extends DomainEntity

object Supplier {

  val emptySupplier = Supplier("", "")

  sealed trait SupplierCmd extends DomainCommand[Supplier, SupplierEvt] {
    val supplierId: String
  }

  sealed trait SupplierEvt extends DomainEvent[Supplier] {
    val supplierId: String
  }

  implicit val dCreate: Decoder[Create] = deriveDecoder[Create]
  implicit val eCreate: Encoder[Create] = deriveEncoder[Create]

  case class Create(supplierId: String, warehouseId: String) extends SupplierCmd {
    override def applyTo(domainEntity: Supplier): Either[String, Option[SupplierEvt]] = {
      println("SU CMD Create applyTo", domainEntity.supplierId, supplierId)
      domainEntity match {
        case Supplier.emptySupplier => Right(Some(Created(supplierId, warehouseId)))
        case _ if domainEntity.supplierId == supplierId => Right(None)
        case _ => Left("Supplier creation error: actor already init")
      }
    }
  }

  case class Created(supplierId: String, warehouseId: String) extends SupplierEvt {
    override def applyTo(domainEntity: Supplier): Supplier = {
      println("SU EVT Created applyTo", domainEntity.supplierId, supplierId)
      domainEntity.copy(supplierId = supplierId, warehouseId = warehouseId)
    }
  }

  implicit val dGetSupplier: Decoder[GetSupplier] = deriveDecoder[GetSupplier]
  implicit val eGetSupplier: Encoder[GetSupplier] = deriveEncoder[GetSupplier]

  case class GetSupplier(supplierId: String) extends SupplierCmd {
    override def applyTo(domainEntity: Supplier): Either[String, Option[SupplierEvt]] = {
      println("SU CMD GetSupplier applyTo", domainEntity.supplierId, supplierId)
      domainEntity match {
        case _ if domainEntity.supplierId == supplierId => Right(Some(ObtainedSupplier(supplierId)))
        case _ => Left("Supplier get error: not found")
      }
    }
  }

  case class ObtainedSupplier(supplierId: String) extends SupplierEvt {
    override def applyTo(domainEntity: Supplier): Supplier = {
      println("SU EVT ObtainedSupplier applyTo", domainEntity.supplierId, supplierId)
      domainEntity
    }
  }

  /*implicit val dAddProduct: Decoder[AddProduct] = deriveDecoder[AddProduct]
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
  }*/

}