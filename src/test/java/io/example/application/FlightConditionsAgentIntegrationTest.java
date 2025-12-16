package io.example.application;

import akka.javasdk.testkit.TestKitSupport;
import io.example.api.FlightEndpoint;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public class FlightConditionsAgentIntegrationTest extends TestKitSupport {

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    String nextDay = LocalDateTime.now().plusDays(1).format(formatter);
    String afterTenDays = LocalDateTime.now().plusDays(11).format(formatter);

    @Test
    @Order(1)
    @EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".+")
    public void testGoodFlightConditions() {

        // Good call to agent as weather in London at midday should meet flying requirements
        var callToAgentBad = new FlightEndpoint.callToAgent(nextDay+"-12", "London");
        var sessionId1 = UUID.randomUUID().toString();
        FlightConditionsAgent.ConditionsReport reportGood = componentClient.forAgent()
                .inSession(sessionId1)
                .method(FlightConditionsAgent::weatherReport)
                .invoke(new FlightConditionsAgent.AgentCommand(callToAgentBad.timeSlotID(), callToAgentBad.location()));
        System.out.println(reportGood.justification());
        assertEquals(true, reportGood.meetsRequirements());
    }

    @Test
    @Order(2)
    @EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".+")
    public void testBadFlightConditions() {

        // Bad call to agent due to nighttime flying being prohibited in this example
        var callToAgentGood = new FlightEndpoint.callToAgent(nextDay+"-23", "London");
        var sessionId2 = UUID.randomUUID().toString();
        FlightConditionsAgent.ConditionsReport reportBad = componentClient.forAgent()
                .inSession(sessionId2)
                .method(FlightConditionsAgent::weatherReport)
                .invoke(new FlightConditionsAgent.AgentCommand(callToAgentGood.timeSlotID(), callToAgentGood.location()));

        assertEquals(false, reportBad.meetsRequirements());

        String reason = reportBad.justification().toLowerCase();
        assertTrue(
                reason.contains("night") || reason.contains("daytime") || reason.contains("time"),
                "Justification should mention time of day. Actual: " + reportBad.justification()
        );
    }

    @Test
    @Order(3)
    @EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".+")
    public void testOutOfForecastRange() {

        var callToAgentOutOfRange = new FlightEndpoint.callToAgent(afterTenDays + "-12", "London");
        var sessionId3 = UUID.randomUUID().toString();

        assertThrows(RuntimeException.class, () -> {
            componentClient.forAgent()
                    .inSession(sessionId3)
                    .method(FlightConditionsAgent::weatherReport)
                    .invoke(new FlightConditionsAgent.AgentCommand(
                            callToAgentOutOfRange.timeSlotID(),
                            callToAgentOutOfRange.location()
                    ));
        });
    }
}
