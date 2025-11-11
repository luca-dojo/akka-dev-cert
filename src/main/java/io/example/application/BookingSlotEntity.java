package io.example.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.example.domain.BookingEvent;
import io.example.domain.Participant;
import io.example.domain.Timeslot;
import java.util.HashSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(id = "booking-slot")
public class BookingSlotEntity extends EventSourcedEntity<Timeslot, BookingEvent> {

    private final String entityId;
    private static final Logger logger = LoggerFactory.getLogger(BookingSlotEntity.class);

    public BookingSlotEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }

    public Effect<Done> markSlotAvailable(Command.MarkSlotAvailable cmd) {
        var event = new BookingEvent.ParticipantMarkedAvailable(entityId, cmd.participant.id(), cmd.participant.participantType());
        return effects()
                .persist(event)
                .thenReply(newState -> Done.getInstance());
    }

    public Effect<Done> unmarkSlotAvailable(Command.UnmarkSlotAvailable cmd) {
        var event = new BookingEvent.ParticipantUnmarkedAvailable(entityId, cmd.participant.id(), cmd.participant.participantType());
        return effects()
                .persist(event)
                .thenReply(newState -> Done.getInstance());
    }

    // NOTE: booking a slot should produce 3x
    // `ParticipantBooked` events
    public Effect<Done> bookSlot(Command.BookReservation cmd) {
        var studentEvent = new BookingEvent.ParticipantBooked(entityId, cmd.studentId, Participant.ParticipantType.STUDENT, cmd.bookingId);
        var aircraftEvent = new BookingEvent.ParticipantBooked(entityId, cmd.aircraftId, Participant.ParticipantType.AIRCRAFT, cmd.bookingId);
        var instructorEvent = new BookingEvent.ParticipantBooked(entityId, cmd.instructorId, Participant.ParticipantType.INSTRUCTOR, cmd.bookingId);
        return effects()
                .persistAll(List.of(studentEvent, aircraftEvent, instructorEvent))
                .thenReply(newState -> Done.getInstance());
    }

    // NOTE: canceling a booking should produce 3
    // `ParticipantCanceled` events
    public Effect<Done> cancelBooking(String bookingId) {

        var events = currentState().findBooking(bookingId).stream().map(booking -> new BookingEvent.ParticipantCanceled(
                entityId, booking.participant().id(), booking.participant().participantType(), bookingId
        )).toList();
        return effects()
                .persistAll(events)
                .thenReply(newState -> Done.getInstance());
    }

    public ReadOnlyEffect<Timeslot> getSlot() {
        return effects().reply(currentState());
    }

    @Override
    public Timeslot emptyState() {
        return new Timeslot(
                // NOTE: these are just estimates for capacity based on it being a sample
                HashSet.newHashSet(10), HashSet.newHashSet(10));
    }

    @Override
    public Timeslot applyEvent(BookingEvent event) {
        // Supply your own implementation to update state based
        // on the event
        return switch (event) {
            case BookingEvent.ParticipantUnmarkedAvailable e:
                yield currentState().unreserve(e);
            case BookingEvent.ParticipantMarkedAvailable e:
                yield currentState().reserve(e);
            case BookingEvent.ParticipantBooked e:
                yield currentState().book(e);
            case BookingEvent.ParticipantCanceled e:
                // not sure about this one
                yield currentState().cancelBooking(e.bookingId());
        };
    }

    public sealed interface Command {
        record MarkSlotAvailable(Participant participant) implements Command {
        }

        record UnmarkSlotAvailable(Participant participant) implements Command {
        }

        record BookReservation(
                String studentId, String aircraftId, String instructorId, String bookingId)
                implements Command {
        }
    }
}
