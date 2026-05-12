package com.example.livecamera;

import com.google.gson.annotations.SerializedName;

public class TourRecognitionRecordResult {

    @SerializedName("id")
    private int id;

    @SerializedName("app_user_id")
    private String appUserId;

    @SerializedName("recognized_theme")
    private String recognizedTheme;

    @SerializedName("recognized_location")
    private String recognizedLocation;

    @SerializedName("status")
    private String status;

    public int getId() {
        return id;
    }

    public String getAppUserId() {
        return appUserId;
    }

    public String getRecognizedTheme() {
        return recognizedTheme;
    }

    public String getRecognizedLocation() {
        return recognizedLocation;
    }

    public String getStatus() {
        return status;
    }
}
