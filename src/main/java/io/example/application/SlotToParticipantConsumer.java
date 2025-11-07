package io.example.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import io.example.domain.BookingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// This class is responsible for consuming events from the booking
// slot entity and turning those into command calls on the
// participant slot entity
@Component(id = "booking-slot-consumer")
@Consume.FromEventSourcedEntity(BookingSlotEntity.class)
public class SlotToParticipantConsumer extends Consumer {

    private final ComponentClient client;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public SlotToParticipantConsumer(ComponentClient client) {
        this.client = client;
    }

    public Effect onEvent(BookingEvent event) {
        // Supply your own implementation
        var participantSlotId = participantSlotId(event);
        return switch (event) {
            case BookingEvent.ParticipantUnmarkedAvailable e:
                client.forEventSourcedEntity(participantSlotId)
                        .method(ParticipantSlotEntity::unmarkAvailable)
                        .invoke(new ParticipantSlotEntity.Commands.UnmarkAvailable(
                                e.slotId(),
                                e.participantId(),
                                e.participantType()
                        ));
                yield effects().done();
            case BookingEvent.ParticipantMarkedAvailable e:
                client.forEventSourcedEntity(participantSlotId)
                        .method(ParticipantSlotEntity::markAvailable)
                        .invoke(new ParticipantSlotEntity.Commands.MarkAvailable(
                                e.slotId(),
                                e.participantId(),
                                e.participantType()
                        ));
                yield effects().done();
            case BookingEvent.ParticipantBooked e:
                client.forEventSourcedEntity(participantSlotId)
                        .method(ParticipantSlotEntity::book)
                        .invoke(new ParticipantSlotEntity.Commands.Book(
                                e.slotId(),
                                e.participantId(),
                                e.participantType(),
                                e.bookingId()
                        ));
                yield effects().done();
                case BookingEvent.ParticipantCanceled e:
                    client.forEventSourcedEntity(participantSlotId)
                            .method(ParticipantSlotEntity::cancel)
                            .invoke(new ParticipantSlotEntity.Commands.Cancel(
                                    e.slotId(),
                                    e.participantId(),
                                    e.participantType(),
                                    e.bookingId()
                            ));
                    yield effects().done();
        };
    }

    // Participant slots are keyed by a derived key made up of
    // {slotId}-{participantId}
    // We don't need the participant type here because the participant IDs
    // should always be unique/UUIDs
    private String participantSlotId(BookingEvent event) {
        return switch (event) {
            case BookingEvent.ParticipantBooked evt -> evt.slotId() + "-" + evt.participantId();
            case BookingEvent.ParticipantUnmarkedAvailable evt ->
                evt.slotId() + "-" + evt.participantId();
            case BookingEvent.ParticipantMarkedAvailable evt -> evt.slotId() + "-" + evt.participantId();
            case BookingEvent.ParticipantCanceled evt -> evt.slotId() + "-" + evt.participantId();
        };
    }
}
