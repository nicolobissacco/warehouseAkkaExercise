package warehouse.domain

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import warehouse.domain.Domain.{DomainCommand, DomainEntity, DomainEvent}

case class Supplier(supplierId: String) extends DomainEntity

object Supplier {

  val emptySupplier = Supplier("")

  sealed trait SupplierCmd extends DomainCommand[Supplier, SupplierEvt] {
    val supplierId: String
  }

  sealed trait SupplierEvt extends DomainEvent[Supplier] {
    val supplierId: String
  }

  implicit val dCreate: Decoder[Create] = deriveDecoder[Create]
  implicit val eCreate: Encoder[Create] = deriveEncoder[Create]

  case class Create(supplierId: String) extends SupplierCmd {
    override def applyTo(domainEntity: Supplier): Either[String, Option[SupplierEvt]] = {
      println("SU CMD Create applyTo", domainEntity.supplierId, supplierId)
      domainEntity match {
        case Supplier.emptySupplier => Right(Some(Created(supplierId)))
        case _ if domainEntity.supplierId == supplierId => Right(None)
        case _ => Left("Supplier creation error: actor already init")
      }
    }
  }

  case class Created(supplierId: String) extends SupplierEvt {
    override def applyTo(domainEntity: Supplier): Supplier = {
      println("SU EVT Created applyTo", domainEntity.supplierId, supplierId)
      domainEntity.copy(supplierId = supplierId)
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
}