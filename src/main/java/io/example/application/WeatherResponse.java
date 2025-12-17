package io.example.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// This annotation tells Jackson to ignore "forecastHours", "timeZone", etc.
// if you haven't defined fields for them.
@JsonIgnoreProperties(ignoreUnknown = true)
public class WeatherResponse {

    @JsonProperty("nextPageToken")
    private String nextPageToken;

    // Getters and Setters
    public String getNextPageToken() {
        return nextPageToken;
    }

    public void setNextPageToken(String nextPageToken) {
        this.nextPageToken = nextPageToken;
    }
}