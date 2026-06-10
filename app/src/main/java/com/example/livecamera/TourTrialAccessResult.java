package com.example.livecamera;

import com.google.gson.annotations.SerializedName;

public class TourTrialAccessResult {

    @SerializedName("feature")
    private String feature;

    @SerializedName("registered")
    private boolean registered;

    @SerializedName("allowed")
    private boolean allowed;

    @SerializedName("limit")
    private Integer limit;

    @SerializedName("used")
    private int used;

    @SerializedName("remaining")
    private Integer remaining;

    @SerializedName("reset_date")
    private String resetDate;

    public String getFeature() {
        return feature;
    }

    public boolean isRegistered() {
        return registered;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public Integer getLimit() {
        return limit;
    }

    public int getUsed() {
        return used;
    }

    public Integer getRemaining() {
        return remaining;
    }

    public String getResetDate() {
        return resetDate;
    }
}
