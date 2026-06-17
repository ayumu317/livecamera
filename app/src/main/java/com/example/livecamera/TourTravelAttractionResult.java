package com.example.livecamera;

import com.google.gson.annotations.SerializedName;

public class TourTravelAttractionResult {

    @SerializedName("id")
    private int id;

    @SerializedName("plan_id")
    private int planId;

    @SerializedName("attraction_name")
    private String attractionName;

    @SerializedName("address")
    private String address;

    @SerializedName("longitude")
    private Double longitude;

    @SerializedName("latitude")
    private Double latitude;

    @SerializedName("description")
    private String description;

    @SerializedName("sort_order")
    private int sortOrder;

    public int getId() {
        return id;
    }

    public int getPlanId() {
        return planId;
    }

    public String getAttractionName() {
        return attractionName;
    }

    public String getAddress() {
        return address;
    }

    public Double getLongitude() {
        return longitude;
    }

    public Double getLatitude() {
        return latitude;
    }

    public String getDescription() {
        return description;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
