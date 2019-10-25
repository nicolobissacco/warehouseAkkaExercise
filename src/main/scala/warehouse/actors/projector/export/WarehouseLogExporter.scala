package warehouse.actors.projector.export

import java.io.{File, FileWriter}
import java.util.UUID

import warehouse.actors.projector.ProjectionIndexer
import akka.persistence.query.TimeBasedUUID
import warehouse.domain.Warehouse.WarehouseEvt

import scala.io.Source
import scala.util.{Failure, Try}

class WarehouseLogExporter(eventsFilePath: String, offsetFilePath: String)
  extends ProjectionIndexer[WarehouseEvt] {

  override val name: String = "warehouse-export"

  override val eventsTag: String = "warehouse-details"

  override def readOffset(): Option[TimeBasedUUID] = {
    val offset = Source.fromFile(offsetFilePath)
    offset match {
      case defined if offset.nonEmpty => {
        val offsetString = defined.getLines().mkString
        Try(TimeBasedUUID(UUID.fromString(offsetString))) match {
          case uuid if uuid.isSuccess => {
            Some(uuid.get)
          }
          case Failure(_) => None
        }
      }
      case _ => {
        None
      }
    }
  }

  override def indexEvents(events: Seq[WarehouseEvt], offset: TimeBasedUUID): Either[Exception, TimeBasedUUID] = {
    val inserts = events.map { event =>
      println(">>>>>indexevents",offset.value.toString)
      project(event, offset)
    }
    val res: Either[Exception, TimeBasedUUID] = inserts.reduceLeft((x, y) => if (x.isLeft) x else y)
    res match {
      case Right(value) => writeOffset(offset)
      case Left(error) => ()
    }
    res
  }

  def writeOffset(offset: TimeBasedUUID): Either[Exception, TimeBasedUUID] = {
    val offsetFw = new FileWriter(offsetFilePath, false)
    try {
      offsetFw.write(s"${offset.value}")
      Right(offset)
    } catch {
      case e: Exception => Left(e)
    } finally {
      offsetFw.close()
    }
  }

  def project(event: WarehouseEvt, offset: TimeBasedUUID): Either[Exception, TimeBasedUUID] = {
    val file = new File(eventsFilePath)
    val eventsFw = new FileWriter(file, true)
    try {
      eventsFw.write(s"${event} ${offset.value}\n")
      Right(offset)
    } catch {
      case e: Exception => Left(e)
    } finally {
      eventsFw.close()
    }
  }
}
