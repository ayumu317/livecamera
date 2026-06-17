package com.example.livecamera;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

public class TourTravelPlanOverviewResult {

    @SerializedName("plan")
    private TourTravelPlanResult plan;

    @SerializedName("attractions")
    private List<TourTravelAttractionResult> attractions;

    @SerializedName("routes")
    private List<Map<String, Object>> routes;

    @SerializedName("hotels")
    private List<Map<String, Object>> hotels;

    @SerializedName("weather_records")
    private List<Map<String, Object>> weatherRecords;

    public TourTravelPlanResult getPlan() {
        return plan;
    }

    public List<TourTravelAttractionResult> getAttractions() {
        return attractions;
    }

    public List<Map<String, Object>> getRoutes() {
        return routes;
    }

    public List<Map<String, Object>> getHotels() {
        return hotels;
    }

    public List<Map<String, Object>> getWeatherRecords() {
        return weatherRecords;
    }
}
