package com.example.livecamera;

import com.google.gson.annotations.SerializedName;

public class TourRecognitionCostResult {

    @SerializedName("recognition_id")
    private int recognitionId;

    @SerializedName("ai_model_cost")
    private double aiModelCost;

    @SerializedName("map_service_cost")
    private double mapServiceCost;

    @SerializedName("other_api_cost")
    private double otherApiCost;

    @SerializedName("total_cost")
    private double totalCost;

    @SerializedName("currency")
    private String currency;

    public int getRecognitionId() {
        return recognitionId;
    }

    public double getAiModelCost() {
        return aiModelCost;
    }

    public double getMapServiceCost() {
        return mapServiceCost;
    }

    public double getOtherApiCost() {
        return otherApiCost;
    }

    public double getTotalCost() {
        return totalCost;
    }

    public String getCurrency() {
        return currency;
    }
}
