package io.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/*
 * The flight conditions agent is responsible for making a determination about the flight
 * conditions for a given day and time. You will need to clearly define the success criteria
 * for the report and instruct the agent (in the system prompt) about the schema of
 * the results it must return (the ConditionsReport).
 *
 * Also be sure to provide clear instructions on how and when tools should be invoked
 * in order to generate results.
 *
 * Flight conditions criteria don't need to be exhaustive, but you should supply the
 * criteria so that an agent does not need to make an external HTTP call to query
 * the condition limits.
 */

@Component(id = "flight-conditions-agent")
public class FlightConditionsAgent extends Agent {

    private final ComponentClient componentClient;

    public FlightConditionsAgent(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public record ConditionsReport(String timeSlotId, Boolean meetsRequirements, String justification) {
    }

    private static final String SYSTEM_MESSAGE = """
            You are a weather agent responsible for evaluating flight conditions on a given day.
            
            You have been given the date and time of the proposed flight session in the format YYYY-MM-DD-HH, it is up to
            you to determine if the weather is suitable enough for safe flying on the given day.
            
            You have the ability to getWeatherForecast which will provide you with an api response from a weather forecast service,
            from this response you should concentrate on:
                "temperature": >0,
                "wind_speed": <35,
                "visibility": >2,
                and
                "sunrise": "XX:XX AM/PM",
                "sunset": "XX:XX AM/PM",
                "weather_descriptions": [ "..." ],
            
            Safe flying conditions:
                wind_speed is below 35,
                safe visibility is above 2
                time of the flight should be during the day ie. between sunrise and sunset.
                temperature should be above 0
                weather_descriptions does not indicate any snowy conditions or heavy rain.
            
            The output of you response MUST be a json with this exact structure:
            {
                timeSlotId: "..."
                meetsRequirements: "..."
                justification: "..."
            }
            
            Where the value of timeSlotId should be the value of timeSlotId passed to you originally,
            meetsRequirements should be a boolean that represents whether the conditions are safe or not for flying,
            and justification should include a comprehensive string that is your reason as to why or why not it is safe to fly
            based on the conditions you have retrieved and should include the conditions that mean that it is not safe to fly.
            
            You should not reply with anything else since you life depends on it.
            """.stripIndent();

    public record AgentCommand(String timeSlotId, String location) {}

    public Effect<ConditionsReport> weatherReport(AgentCommand cmd) {
        var model = ModelProvider.fromConfig("gemini-flash");
        var userMessage = "Validate the conditions of the weather with your available tools and reply with the correct structure, " +
                "the timeSlotId is:" + cmd.timeSlotId + "The flight location is: " + cmd.location;

        return effects()
                .model(model)
                .systemMessage(SYSTEM_MESSAGE)
                .userMessage(userMessage)
                .responseAs(ConditionsReport.class)
                .thenReply();
    }

    /*
     * You can choose to hard code the weather conditions for specific days or you
     * can actually
     * communicate with an external weather API. You should be able to get both
     * suitable weather
     * conditions and poor weather conditions from this tool function for testing.
     */
    @FunctionTool(description = "Queries the weather conditions as they are forecasted based on the time slot ID of the training session booking")
    private String getWeatherForecast(String timeSlotId, String location) throws IOException, InterruptedException {

        String baseUrl = "https://api.weatherstack.com/current";
        String apiKey = System.getenv("WEATHERSTACK_API_KEY");
        String url = String.format("%s?access_key=%s&query=%s", baseUrl, apiKey, location);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }
}


