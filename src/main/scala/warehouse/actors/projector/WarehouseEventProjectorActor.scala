package warehouse.actors.projector

import warehouse.actors.ReadJournalStreamManagerActor
import warehouse.actors.projector.EventEnvelopeSeqHandler.EventEnvelopeSeq
import warehouse.actors.projector.WarehouseEventProjectorActor.ReadOffset
import warehouse.actors.projector.export.WarehouseLogExporter
import warehouse.actors.write.{ActorSharding, WarehouseActor}
import akka.NotUsed
import akka.actor.{ActorSystem, Props}
import akka.event.LoggingReceive
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.{NoOffset, Offset, TimeBasedUUID}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import warehouse.AppConfig
import warehouse.domain.Warehouse.WarehouseEvt

import scala.concurrent.ExecutionContext

class WarehouseEventProjectorActor(indexer: WarehouseLogExporter)
  extends ReadJournalStreamManagerActor[EventEnvelopeSeq]
    with EventEnvelopeSeqHandler[WarehouseEvt]
    with ActorSharding {

  override implicit val system: ActorSystem = context.system

  override implicit val mat: ActorMaterializer = ActorMaterializer()

  implicit val blockingDispatcher: ExecutionContext =
    context.system.dispatchers.lookup(id = "warehouse-exercise-blocking-dispatcher")

  override def preStart(): Unit = {
    context.system.scheduler.scheduleOnce(AppConfig.readDelay, self, ReadOffset)(system.dispatcher)
  }

  override protected def createSource(readJournal: CassandraReadJournal, offset: Offset): Source[EventEnvelopeSeq, NotUsed] = {
    readJournal
      .eventsByTag(WarehouseActor.detailsTag, offset)
      .groupedWithin(AppConfig.readBatchSize, AppConfig.readWindow)
      .map(EventEnvelopeSeq(_))
  }

  override def receive: Receive = LoggingReceive {
    case ReadOffset => {
      val x = indexer.readOffset() match {
        case None => NoOffset
        case Some(offset) => offset
      }

      context.become(eventStreamStarted(x))
      startStream(x)
      println("({}) Stream started! Offset: {}", indexer.name, x)
    }
    case unknown =>
      println("({}) Received unknown message in receiveCommand (sender: {} - message: {})",
        indexer.name,
        sender,
        unknown)
  }

  def eventStreamStarted(offset: Offset): Receive = LoggingReceive {
    ({
      case groupedEvents: EventEnvelopeSeq =>
        val offset2event: Seq[(TimeBasedUUID, WarehouseEvt)] = extractOffsetsAndEvents(groupedEvents)
        val eventStreamMaxOffset: TimeBasedUUID = getMaxOffset(offset2event)
        val events = offset2event map (_._2)
        val originalSender = sender()

        println("({}) Processing batch of {} events", indexer.name, events.size)
        indexer.indexEvents(events, eventStreamMaxOffset) match {
          case Right(_) =>
            println("({}) Indexing operation successfully completed! Offset: {}", indexer.name, eventStreamMaxOffset)
            context.become(eventStreamStarted(eventStreamMaxOffset))
            originalSender ! AckMessage

          case Left(exception) =>
            println(exception, "({}) Indexing operation failed", indexer.name)
            throw exception // effect: restart actor
        }
    }: Receive) orElse manageJournalStream(offset)
  }
}

object WarehouseEventProjectorActor {
  val name = "warehouse-event-projector-actor"

  def props(indexer: WarehouseLogExporter): Props = Props(new WarehouseEventProjectorActor(indexer))

  case object ReadOffset

}