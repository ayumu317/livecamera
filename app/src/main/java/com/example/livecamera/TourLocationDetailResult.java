package com.example.livecamera;

import com.google.gson.annotations.SerializedName;

public class TourLocationDetailResult {

    @SerializedName("id")
    private int id;

    @SerializedName("theme_id")
    private Integer themeId;

    @SerializedName("location_name")
    private String locationName;

    @SerializedName("country")
    private String country;

    @SerializedName("city")
    private String city;

    @SerializedName("address")
    private String address;

    @SerializedName("longitude")
    private Double longitude;

    @SerializedName("latitude")
    private Double latitude;

    @SerializedName("location_type")
    private String locationType;

    @SerializedName("description")
    private String description;

    @SerializedName("reference_image_url")
    private String referenceImageUrl;

    @SerializedName("status")
    private String status;

    public int getId() {
        return id;
    }

    public Integer getThemeId() {
        return themeId;
    }

    public String getLocationName() {
        return locationName;
    }

    public String getCountry() {
        return country;
    }

    public String getCity() {
        return city;
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

    public String getLocationType() {
        return locationType;
    }

    public String getDescription() {
        return description;
    }

    public String getReferenceImageUrl() {
        return referenceImageUrl;
    }

    public String getStatus() {
        return status;
    }
}
