package warehouse.domain

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import warehouse.domain.Domain.{DomainCommand, DomainEntity, DomainEvent}

case class Customer(customerId: String) extends DomainEntity

object Customer {

  val emptyCustomer = Customer("")

  sealed trait CustomerCmd extends DomainCommand[Customer, CustomerEvt] {
    val customerId: String
  }

  sealed trait CustomerEvt extends DomainEvent[Customer] {
    val customerId: String
  }

  implicit val dCreate: Decoder[Create] = deriveDecoder[Create]
  implicit val eCreate: Encoder[Create] = deriveEncoder[Create]

  case class Create(customerId: String) extends CustomerCmd {
    override def applyTo(domainEntity: Customer): Either[String, Option[CustomerEvt]] = {
      println("CU CMD Create applyTo", domainEntity.customerId, customerId)
      domainEntity match {
        case Customer.emptyCustomer => Right(Some(Created(customerId)))
        case _ if domainEntity.customerId == customerId => Right(None)
        case _ => Left("Customer creation error: actor already init")
      }
    }
  }

  case class Created(customerId: String) extends CustomerEvt {
    override def applyTo(domainEntity: Customer): Customer = {
      println("CU EVT Created applyTo", domainEntity.customerId, customerId)
      domainEntity.copy(customerId = customerId)
    }
  }

  implicit val dGetCustomer: Decoder[GetCustomer] = deriveDecoder[GetCustomer]
  implicit val eGetCustomer: Encoder[GetCustomer] = deriveEncoder[GetCustomer]

  case class GetCustomer(customerId: String) extends CustomerCmd {
    override def applyTo(domainEntity: Customer): Either[String, Option[CustomerEvt]] = {
      println("CU CMD GetCustomer applyTo", domainEntity.customerId, customerId)
      domainEntity match {
        case _ if domainEntity.customerId == customerId => Right(Some(ObtainedCustomer(customerId)))
        case _ => Left("Customer get error: not found")
      }
    }
  }

  case class ObtainedCustomer(customerId: String) extends CustomerEvt {
    override def applyTo(domainEntity: Customer): Customer = {
      println("CU EVT ObtainedCustomer applyTo", domainEntity.customerId, customerId)
      domainEntity
    }
  }

}