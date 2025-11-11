package io.example.application;

import akka.javasdk.testkit.TestKitSupport;
import io.example.api.FlightEndpoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

public class FlightConditionsAgentIntegrationTest extends TestKitSupport {

    @Test
    public void testFlightConditionsAgent() {

        // Bad call to agent due to nighttime flying
        var callToAgentBad = new FlightEndpoint.callToAgent("2026-11-11-23", "London");
        var sessionId1 = UUID.randomUUID().toString();
        FlightConditionsAgent.ConditionsReport reportBad = componentClient.forAgent()
                .inSession(sessionId1)
                .method(FlightConditionsAgent::weatherReport)
                .invoke(new FlightConditionsAgent.AgentCommand(callToAgentBad.timeSlotID(), callToAgentBad.location()));

        assertEquals(false, reportBad.meetsRequirements());

        // Good call to agent
        var callToAgentGood = new FlightEndpoint.callToAgent("2026-11-11-12", "London");
        var sessionId2 = UUID.randomUUID().toString();
        FlightConditionsAgent.ConditionsReport reportGood = componentClient.forAgent()
                .inSession(sessionId2)
                .method(FlightConditionsAgent::weatherReport)
                .invoke(new FlightConditionsAgent.AgentCommand(callToAgentGood.timeSlotID(), callToAgentGood.location()));

        assertEquals(true, reportGood.meetsRequirements());
    }
}
