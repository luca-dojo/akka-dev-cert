package io.example.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoogleWeatherService {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(GoogleWeatherService.class);
    private final HttpClient httpClient;
    private final String apiKey;

    public GoogleWeatherService(){
        this.httpClient = HttpClient.newHttpClient();
        this.apiKey = System.getenv("GOOGLE_API_KEY");
    }

    public record LatLong(double latitude, double longitude) {}

    public String getGoogleWeather(String timeSlotId, String location) {
        // example timeSlotId = 2025-12-26-12
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH");
        LocalDateTime targetTime = LocalDateTime.parse(timeSlotId, formatter);

        long hoursUntilTarget = ChronoUnit.HOURS.between(LocalDateTime.now(ZoneId.of("UTC")), targetTime);

        if (hoursUntilTarget < 0 || hoursUntilTarget > 240) {
            throw new IllegalArgumentException("Target time must be within the next 240 hours (10 days).");
        }

        var geocode = getLongLat(location);
        String baseUrl = String.format(
                "https://weather.googleapis.com/v1/forecast/hours:lookup?location.latitude=%f&location.longitude=%f&key=%s",
                geocode.latitude, geocode.longitude, apiKey
        );

        int targetPageIndex = (int) (hoursUntilTarget / 24);

        String currentToken = null;

        try {
            for (int i = 0; i <= targetPageIndex; i++) {

                String requestUrl = baseUrl;
                if (currentToken != null) {
                    requestUrl += "&page_token=" + URLEncoder.encode(currentToken, StandardCharsets.UTF_8);
                }

                HttpRequest weatherRequest = HttpRequest.newBuilder()
                        .uri(URI.create(requestUrl))
                        .GET()
                        .build();

                HttpResponse<String> weatherResponse = httpClient.send(weatherRequest, HttpResponse.BodyHandlers.ofString());

                if (weatherResponse.statusCode() != 200) {
                    throw new RuntimeException("Weather API failed: " + weatherResponse.body());
                }

                String responseBody = weatherResponse.body();

                // If we are at the target index, return the body
                if (i == targetPageIndex) {
                    log.info("Target Date Time found on Page {} of Google Weather API Response", targetPageIndex);
                    return responseBody;
                }

                JsonNode root = mapper.readTree(responseBody);
                if (root.has("nextPageToken")) {
                    currentToken = root.get("nextPageToken").asText();
                } else {
                    // If we run out of tokens before reaching the target page, the date is out of range
                    throw new RuntimeException("Target time " + timeSlotId + " is beyond the available forecast pages.");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("API Service Failure: ", e);
        }
        throw new RuntimeException("Unexpected execution state.");
    }

    private LatLong getLongLat(String location) {
        String geoUrl = String.format(
                "https://maps.googleapis.com/maps/api/geocode/json?address=%s&key=%s",
                URLEncoder.encode(location, StandardCharsets.UTF_8),
                apiKey
        );
        try {
            HttpRequest geoRequest = HttpRequest.newBuilder().uri(URI.create(geoUrl)).GET().build();
            HttpResponse<String> geoResponse = httpClient.send(geoRequest, HttpResponse.BodyHandlers.ofString());

            String geoBody = geoResponse.body();

            if (geoBody.contains("\"status\" : \"REQUEST_DENIED\"") || geoBody.contains("\"status\": \"REQUEST_DENIED\"")) {
                throw new RuntimeException("Google Geocoding API Access Denied. Check API Key enablement and Billing. Response: " + geoBody);
            }
            if (geoBody.contains("\"status\" : \"ZERO_RESULTS\"") || geoBody.contains("\"status\": \"ZERO_RESULTS\"")) {
                throw new RuntimeException("Google could not find location: " + location);
            }
            double latitude;
            double longitude;
            latitude = extractCoordinate(geoBody, "lat");
            longitude = extractCoordinate(geoBody, "lng");

            return new LatLong(latitude, longitude);

        } catch (Exception e) {
            throw new RuntimeException("API Service Failure: ", e);
        }
    }

    // Helper to parse lat/lng from Geocoding JSON without an external library
    private double extractCoordinate(String jsonBody, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*([\\d.-]+)");
        Matcher matcher = pattern.matcher(jsonBody);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        throw new IllegalArgumentException("Could not parse coordinates from Geocoding response. Check API Key or Location.");
    }
}

