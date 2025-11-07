package io.example.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import io.example.application.ParticipantSlotEntity.Event.Booked;
import io.example.application.ParticipantSlotEntity.Event.Canceled;
import io.example.application.ParticipantSlotEntity.Event.MarkedAvailable;
import io.example.application.ParticipantSlotEntity.Event.UnmarkedAvailable;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(id = "view-participant-slots")
public class ParticipantSlotsView extends View {

    private static Logger logger = LoggerFactory.getLogger(ParticipantSlotsView.class);

    @Consume.FromEventSourcedEntity(ParticipantSlotEntity.class)
    public static class ParticipantSlotsViewUpdater extends TableUpdater<SlotRow> {

        public Effect<SlotRow> onEvent(ParticipantSlotEntity.Event event) {
            // Supply your own implementation
            System.out.println("VIEW: ParticipantSlotsViewUpdater event: "+ event);
            return switch (event) {

                case ParticipantSlotEntity.Event.MarkedAvailable e -> {
                    var row = new SlotRow(
                            e.slotId(),
                            e.participantId(),
                            e.participantType().toString(),
                            "",
                            "AVAILABLE"
                    );
                    yield effects().updateRow(row);
                }

                case ParticipantSlotEntity.Event.UnmarkedAvailable e -> {
                    var current = rowState();
                    if (current == null) {
                        logger.warn("No existing row for slotId {} when processing UnmarkedAvailable — ignoring", e.slotId());
                        yield effects().ignore();
                    } else {
                        yield effects().updateRow(current.withStatus("UNAVAILABLE"));
                    }
                }

                case ParticipantSlotEntity.Event.Booked e -> {
                    var current = rowState();
                    if (current == null) {
                        logger.warn("No existing row for slotId {} when processing Booked — ignoring", e.slotId());
                        yield effects().ignore();
                    } else {
                        yield effects().updateRow(current.withBookingId(e.bookingId()).withStatus("BOOKED"));
                    }
                }

                case ParticipantSlotEntity.Event.Canceled e -> {
                    var current = rowState();
                    if (current == null) {
                        logger.warn("No existing row for slotId {} when processing Canceled — ignoring", e.slotId());
                        yield effects().ignore();
                    } else {
                        yield effects().updateRow(current.withStatus("CANCELLED"));
                    }
                }
            };
        }
    }

    public record SlotRow(
            String slotId,
            String participantId,
            String participantType,
            String bookingId,
            String status) {
        public SlotRow withStatus(String status) {
            return new SlotRow(slotId, participantId, participantType, bookingId, status);
        }
        public SlotRow withBookingId(String bookingId) {
            return new SlotRow(slotId, participantId, participantType, bookingId, status);
        }
    }

    public record ParticipantStatusInput(String participantId, String status) {
    }
    public record BookingStatusInput(String bookingId, String status) {
    }
    public record SlotList(List<SlotRow> slots) {
    }

     @Query("SELECT * AS slots FROM slots WHERE participantId = :participantId")
    public QueryEffect<SlotList> getSlotsByParticipant(String participantId) {
        return queryResult();
    }

    @Query("SELECT * AS slots FROM slots WHERE participantId = :participantId AND status = :status")
    public QueryEffect<SlotList> getSlotsByParticipantAndStatus(ParticipantStatusInput input) {
        return queryResult();
    }

    @Query("SELECT * AS slots FROM slots WHERE bookingId = :bookingId AND status = :status")
    public QueryEffect<SlotList> getsParticipantsByBookingIdAndStatus(BookingStatusInput input) {
        return queryResult();
    }
}
