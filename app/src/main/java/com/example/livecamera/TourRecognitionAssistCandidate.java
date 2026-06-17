package com.example.livecamera;

import com.google.gson.annotations.SerializedName;

public class TourRecognitionAssistCandidate {

    @SerializedName("candidate_source")
    private String candidateSource;

    @SerializedName("candidate_type")
    private String candidateType;

    @SerializedName("learned_candidate_id")
    private Integer learnedCandidateId;

    @SerializedName("location_id")
    private Integer locationId;

    @SerializedName("theme_name")
    private String themeName;

    @SerializedName("location_name")
    private String locationName;

    @SerializedName("address")
    private String address;

    @SerializedName("city")
    private String city;

    @SerializedName("country")
    private String country;

    @SerializedName("score")
    private double score;

    @SerializedName("recommend_reason")
    private String recommendReason;

    @SerializedName("user_confirmed_count")
    private int userConfirmedCount;

    @SerializedName("user_correction_count")
    private int userCorrectionCount;

    @SerializedName("global_confirmed_count")
    private int globalConfirmedCount;

    @SerializedName("global_correction_count")
    private int globalCorrectionCount;

    public String getCandidateSource() {
        return candidateSource;
    }

    public String getCandidateType() {
        return candidateType;
    }

    public Integer getLearnedCandidateId() {
        return learnedCandidateId;
    }

    public Integer getLocationId() {
        return locationId;
    }

    public String getThemeName() {
        return themeName;
    }

    public String getLocationName() {
        return locationName;
    }

    public String getAddress() {
        return address;
    }

    public String getCity() {
        return city;
    }

    public String getCountry() {
        return country;
    }

    public double getScore() {
        return score;
    }

    public String getRecommendReason() {
        return recommendReason;
    }

    public int getUserConfirmedCount() {
        return userConfirmedCount;
    }

    public int getUserCorrectionCount() {
        return userCorrectionCount;
    }

    public int getGlobalConfirmedCount() {
        return globalConfirmedCount;
    }

    public int getGlobalCorrectionCount() {
        return globalCorrectionCount;
    }
}
