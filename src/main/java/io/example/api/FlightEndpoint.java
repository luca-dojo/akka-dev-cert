package io.example.api;

import java.util.Collections;
import java.util.List;

import io.example.application.BookingSlotEntity;
import io.example.application.ParticipantSlotEntity;
import io.example.application.ParticipantSlotsView;
import io.example.domain.Participant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpException;
import akka.javasdk.http.HttpResponses;
import io.example.application.ParticipantSlotsView.SlotList;
import io.example.domain.Participant.ParticipantType;
import io.example.domain.Timeslot;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/flight")
public class FlightEndpoint extends AbstractHttpEndpoint {
    private final Logger log = LoggerFactory.getLogger(FlightEndpoint.class);

    private final ComponentClient componentClient;

    public FlightEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    // Creates a new booking. All three identified participants will
    // be considered booked for the given timeslot, if they are all
    // "available" at the time of booking.
    @Post("/bookings/{slotId}")
    public HttpResponse createBooking(String slotId, BookingRequest request) {
        // String studentId, String aircraftId, String instructorId, String bookingId
        // Implementation here

        // Make sure to get a flight conditions report from the AI agent and use that
        // to decide if the booking can be created

        var areParticipantsAvailable = areParticipantsStatus(request.studentId, request.aircraftId, request.instructorId, "AVAILABLE");
        if (areParticipantsAvailable.studentState && areParticipantsAvailable.aircraftState && areParticipantsAvailable.instructorState) {
            log.info("Creating booking for slot {}: {}", slotId, request);
            componentClient
                    .forEventSourcedEntity(slotId)
                    .method(BookingSlotEntity::bookSlot)
                    .invoke(new BookingSlotEntity.Command.BookReservation(
                            request.studentId,
                            request.aircraftId,
                            request.instructorId,
                            request.bookingId
                    ));
            return HttpResponses.created();
        } else {
            StringBuilder unavailable = new StringBuilder("ERROR: Cannot book timeslot as following participants are unavailable: ");
            if (!areParticipantsAvailable.studentState) unavailable.append("Student ");
            if (!areParticipantsAvailable.aircraftState) unavailable.append("Aircraft ");
            if (!areParticipantsAvailable.instructorState) unavailable.append("Instructor ");

            String message = unavailable.toString().trim().replaceAll(" +", " ");
            log.warn("Booking creation failed for slot {}: {}", slotId, message);
            return HttpResponses.badRequest(message);
        }
    }

    // Cancels an existing booking. Note that both the slot
    // ID and the booking ID are required.
    @Delete("/bookings/{slotId}/{bookingId}")
    public HttpResponse cancelBooking(String slotId, String bookingId) {
        log.info("Canceling booking id {}", bookingId);

        try {
            // get participants by booking id
            var bookedParticipants = componentClient
                    .forView()
                    .method(ParticipantSlotsView::getsParticipantsByBookingIdAndStatus)
                    .invoke(new ParticipantSlotsView.BookingStatusInput(bookingId, "BOOKED"));

            if (bookedParticipants.slots().size() >= 3) {
                // Add booking cancellation code
                componentClient
                        .forEventSourcedEntity(slotId)
                        .method(BookingSlotEntity::cancelBooking)
                        .invoke(bookingId);
                return HttpResponses.ok();
            } else {
                log.warn("Booking creation failed for slot: {}", bookingId);
                return HttpResponses.badRequest("No Booking with id: "+bookingId);
            }
        } catch (Exception e) {
            return HttpResponses.badRequest("Booking could not be cancelled");
        }
    }

    // Retrieves all slots in which a given participant has the supplied status.
    // Used to retrieve bookings and slots in which the participant is available
    @Get("/slots/{participantId}/{status}")
    public SlotList slotsByStatus(String participantId, String status) {

        // Add view query
        return componentClient
                .forView()
                .method(ParticipantSlotsView::getSlotsByParticipantAndStatus)
                .invoke(new ParticipantSlotsView.ParticipantStatusInput(participantId, status.toUpperCase()));
    }

    // Returns the internal availability state for a given slot
    @Get("/availability/{slotId}")
    public Timeslot getSlot(String slotId) {

        // Add entity state request
        try {
            return componentClient
                    .forEventSourcedEntity(slotId)
                    .method(BookingSlotEntity::getSlot)
                    .invoke();
        } catch (Exception e) {
            log.warn("No timeslot with id: {}", slotId);
            return new Timeslot(Collections.emptySet(),
                    Collections.emptySet());
        }
    }

    // Indicates that the supplied participant is available for booking
    // within the indicated time slot
    @Post("/availability/{slotId}")
    public HttpResponse markAvailable(String slotId, AvailabilityRequest request) {
        ParticipantType participantType;

        try {
            participantType = ParticipantType.valueOf(request.participantType().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Bad participant type {}", request.participantType());
            throw HttpException.badRequest("invalid participant type");
        }

        // checking that a participant is not already booked on the chosen slot
        var participantBookedSlots = componentClient
                .forView()
                .method(ParticipantSlotsView::getSlotsByParticipantAndStatus)
                .invoke(new ParticipantSlotsView.ParticipantStatusInput(request.participantId, "BOOKED"));

        if (participantBookedSlots.slots().isEmpty()) {
            log.info("Marking timeslot available for entity {}", slotId);

            // Add entity client to mark slot available
            componentClient
                    .forEventSourcedEntity(slotId)
                    .method(BookingSlotEntity::markSlotAvailable)
                    .invoke(new BookingSlotEntity.Command.MarkSlotAvailable(new Participant(request.participantId, participantType)));

            return HttpResponses.ok();
        } else  {
            throw HttpException.badRequest("Participant: "+request.participantId+" is already booked on to this time slot");
        }
    }

    // Unmarks a slot as available for the given participant.
    @Delete("/availability/{slotId}")
    public HttpResponse unmarkAvailable(String slotId, AvailabilityRequest request) {
        ParticipantType participantType;
        try {
            participantType = ParticipantType.valueOf(request.participantType().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Bad participant type {}", request.participantType());
            throw HttpException.badRequest("invalid participant type");
        }
        // checking that a participant is not already booked on the chosen slot
        var participantBookedSlots = componentClient
                .forView()
                .method(ParticipantSlotsView::getSlotsByParticipantAndStatus)
                .invoke(new ParticipantSlotsView.ParticipantStatusInput(request.participantId, "BOOKED"));

        if (participantBookedSlots.slots().isEmpty()) {

            // Add code to unmark slot as available
            componentClient
                    .forEventSourcedEntity(slotId)
                    .method(BookingSlotEntity::unmarkSlotAvailable)
                    .invoke(new BookingSlotEntity.Command.UnmarkSlotAvailable(new Participant(request.participantId, participantType)));

            return HttpResponses.ok();
        } else {
            throw HttpException.badRequest("Participant: "+request.participantId+" is already booked on to this time slot, cannot mark unavailable.");
        }
    }

    // Public API representation of a booking request
    public record BookingRequest(
            String studentId, String aircraftId, String instructorId, String bookingId) {
    }

    // Public API representation of an availability mark/unmark request
    public record AvailabilityRequest(String participantId, String participantType) {
    }

    // Public record to store state of participant status
    public record ParticipantIsState(Boolean studentState, Boolean aircraftState, Boolean instructorState) {}
    public ParticipantIsState areParticipantsStatus(String studentId, String aircraftId, String instructorId, String status) {
        var studentSlotRow = componentClient.forView()
                .method(ParticipantSlotsView::getSlotsByParticipant)
                .invoke(studentId);
        var aircraftSlotRow = componentClient.forView()
                .method(ParticipantSlotsView::getSlotsByParticipant)
                .invoke(aircraftId);
        var instructorSlotRow = componentClient.forView()
                .method(ParticipantSlotsView::getSlotsByParticipant)
                .invoke(instructorId);

        return new ParticipantIsState(studentSlotRow.slots()
                .stream()
                .anyMatch(slotRow -> status.equals(slotRow.status())),
                aircraftSlotRow.slots()
                .stream()
                .anyMatch(slotRow -> status.equals(slotRow.status())),
                instructorSlotRow.slots()
                .stream()
                .anyMatch(slotRow -> status.equals(slotRow.status())));
    }
}
