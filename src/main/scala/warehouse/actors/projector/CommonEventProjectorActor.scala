package warehouse.actors.projector

import akka.NotUsed
import akka.event.LoggingReceive
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.{NoOffset, Offset, TimeBasedUUID}
import akka.stream.scaladsl.Source
import warehouse.AppConfig
import warehouse.actors.ReadJournalStreamManagerActor
import warehouse.actors.projector.EventEnvelopeSeqHandler.EventEnvelopeSeq
import warehouse.actors.projector.export.CommonLogExporter
import warehouse.actors.write.ActorSharding

case object ReadOffset

trait CommonEventProjectorActor[T]
  extends ReadJournalStreamManagerActor[EventEnvelopeSeq]
    with EventEnvelopeSeqHandler[T]
    with ActorSharding {

  val indexerInside: CommonLogExporter[T]
  val detailsTag: String

  override def preStart(): Unit = {
    context.system.scheduler.scheduleOnce(AppConfig.readDelay, self, ReadOffset)(system.dispatcher)
  }

  override protected def createSource(readJournal: CassandraReadJournal, offset: Offset): Source[EventEnvelopeSeq, NotUsed] = {
    readJournal
      .eventsByTag(detailsTag, offset)
      .groupedWithin(AppConfig.readBatchSize, AppConfig.readWindow)
      .map(EventEnvelopeSeq)
  }

  override def receive: Receive = LoggingReceive {
    case ReadOffset => {
      val x = indexerInside.readOffset() match {
        case None => NoOffset
        case Some(offset) => offset
      }

      context.become(eventStreamStarted(x))
      startStream(x)
      println("({}) Stream started! Offset: {}", indexerInside.name, x)
    }
    case unknown =>
      println("({}) Received unknown message in receiveCommand (sender: {} - message: {})",
        indexerInside.name,
        sender,
        unknown)
  }

  def eventStreamStarted(offset: Offset): Receive = LoggingReceive {
    ({
      case groupedEvents: EventEnvelopeSeq =>
        val offset2event: Seq[(TimeBasedUUID, T)] = extractOffsetsAndEvents(groupedEvents)
        val eventStreamMaxOffset: TimeBasedUUID = getMaxOffset(offset2event)
        val events = offset2event map (_._2)
        val originalSender = sender()

        println("({}) Processing batch of {} events", indexerInside.name, events.size)
        indexerInside.indexEvents(events, eventStreamMaxOffset) match {
          case Right(_) =>
            println("({}) Indexing operation successfully completed! Offset: {}", indexerInside.name, eventStreamMaxOffset)
            context.become(eventStreamStarted(eventStreamMaxOffset))
            originalSender ! AckMessage

          case Left(exception) =>
            println(exception, "({}) Indexing operation failed", indexerInside.name)
            throw exception // effect: restart actor
        }
    }: Receive) orElse manageJournalStream(offset)
  }
}

