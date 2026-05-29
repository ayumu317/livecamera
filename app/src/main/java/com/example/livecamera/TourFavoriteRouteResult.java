package com.example.livecamera;

import com.google.gson.annotations.SerializedName;

public class TourFavoriteRouteResult {

    @SerializedName("id")
    private int id;

    @SerializedName("user_id")
    private Integer userId;

    @SerializedName("app_user_id")
    private String appUserId;

    @SerializedName("route_name")
    private String routeName;

    @SerializedName("location_ids")
    private String locationIds;

    @SerializedName("route_summary")
    private String routeSummary;

    @SerializedName("total_distance")
    private double totalDistance;

    @SerializedName("estimated_minutes")
    private int estimatedMinutes;

    public int getId() {
        return id;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getAppUserId() {
        return appUserId;
    }

    public String getRouteName() {
        return routeName;
    }

    public String getLocationIds() {
        return locationIds;
    }

    public String getRouteSummary() {
        return routeSummary;
    }

    public double getTotalDistance() {
        return totalDistance;
    }

    public int getEstimatedMinutes() {
        return estimatedMinutes;
    }
}
