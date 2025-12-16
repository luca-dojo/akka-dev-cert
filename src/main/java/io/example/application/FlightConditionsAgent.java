package io.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final GoogleWeatherSerivce googleWeatherSerivce;


    public FlightConditionsAgent(ComponentClient componentClient) {
        this.componentClient = componentClient;
        this.googleWeatherSerivce = new GoogleWeatherSerivce();
    }

    public record ConditionsReport(String timeSlotId, Boolean meetsRequirements, String justification) {
    }

    private static final String SYSTEM_MESSAGE = """
            You are a weather agent responsible for evaluating flight conditions on a given day.
            
            You have been given the date and time of the proposed flight session in the format YYYY-MM-DD-HH, it is up to
            you to determine if the weather is suitable enough for safe flying on the given day.
            
            You have the ability to getWeatherForecast which will provide you with an api response from a weather forecast service,
            you should find the correct forecast hour associated with the flight session, you can find the interval in
            the response and it will look like this:
             {
              "forecastHours": [
                {
                  "interval": {
                    "startTime": "yyyy-mm-ddThh:mm:ssZ",
                    "endTime": "yyyy-mm-ddThh:mm:ssZ"
            
            and then from the rest of the response you should concentrate on the following parameters below and their acceptable values:
                "temperature": {
                    "unit": "CELSIUS",
                    "degrees": x
                 },
                "wind": {
                    "speed": {
                      "unit": "KILOMETERS_PER_HOUR",
                      "value": y
                    },
                    "gust": {
                      "unit": "KILOMETERS_PER_HOUR",
                      "value": z
                    }
                  },
                  "visibility": {
                    "unit": "KILOMETERS",
                    "distance": w
                  },
              "isDaytime": boolean,
              "thunderstormProbability": int,
            
            Safe flying conditions:
                temperature degrees > 0
                wind speed value is below 30,
                wind gust value is below 45,
                visibility distance is above 10
                time of the flight should be during the day for the timeslot provided ie. isDaytime = True
                thunderstormProbability < 30
            
            The output of your response MUST be a json with this exact structure:
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

    public record AgentCommand(String timeSlotId, String location) {
    }

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
    @FunctionTool(description = "Queries the weather conditions using Google Maps Platform based on the location")
    private String getWeatherForecast(String timeSlotId, String location) {
        return googleWeatherSerivce.getGoogleWeather(timeSlotId, location);
    }
}




