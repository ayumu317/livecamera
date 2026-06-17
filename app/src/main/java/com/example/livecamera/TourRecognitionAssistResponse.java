package com.example.livecamera;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class TourRecognitionAssistResponse {

    @SerializedName("keyword")
    private String keyword;

    @SerializedName("app_user_id")
    private String appUserId;

    @SerializedName("strategy")
    private String strategy;

    @SerializedName("items")
    private List<TourRecognitionAssistCandidate> items;

    public String getKeyword() {
        return keyword;
    }

    public String getAppUserId() {
        return appUserId;
    }

    public String getStrategy() {
        return strategy;
    }

    public List<TourRecognitionAssistCandidate> getItems() {
        return items;
    }
}
