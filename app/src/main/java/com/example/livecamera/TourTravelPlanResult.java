package com.example.livecamera;

import com.google.gson.annotations.SerializedName;

public class TourTravelPlanResult {

    @SerializedName("id")
    private int id;

    @SerializedName("plan_name")
    private String planName;

    @SerializedName("destination_name")
    private String destinationName;

    @SerializedName("destination_city")
    private String destinationCity;

    @SerializedName("destination_country")
    private String destinationCountry;

    @SerializedName("start_date")
    private String startDate;

    @SerializedName("end_date")
    private String endDate;

    @SerializedName("travel_days")
    private int travelDays;

    @SerializedName("budget_amount")
    private Double budgetAmount;

    @SerializedName("travel_status")
    private String travelStatus;

    @SerializedName("destination_summary")
    private String destinationSummary;

    @SerializedName("route_overview")
    private String routeOverview;

    @SerializedName("attraction_overview")
    private String attractionOverview;

    @SerializedName("hotel_overview")
    private String hotelOverview;

    @SerializedName("description")
    private String description;

    public int getId() {
        return id;
    }

    public String getPlanName() {
        return planName;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public String getDestinationCity() {
        return destinationCity;
    }

    public String getDestinationCountry() {
        return destinationCountry;
    }

    public String getStartDate() {
        return startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public int getTravelDays() {
        return travelDays;
    }

    public Double getBudgetAmount() {
        return budgetAmount;
    }

    public String getTravelStatus() {
        return travelStatus;
    }

    public String getDestinationSummary() {
        return destinationSummary;
    }

    public String getRouteOverview() {
        return routeOverview;
    }

    public String getAttractionOverview() {
        return attractionOverview;
    }

    public String getHotelOverview() {
        return hotelOverview;
    }

    public String getDescription() {
        return description;
    }
}
