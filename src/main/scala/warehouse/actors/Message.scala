package warehouse.actors

import akka.actor.ActorRef

object Message {

  sealed trait ProductMessage

  case class ProductMessageDone(actor: ActorRef) extends ProductMessage

  case class ProductMessageError(actor: ActorRef, msg: String) extends ProductMessage

}
