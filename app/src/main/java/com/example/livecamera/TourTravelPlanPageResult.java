package com.example.livecamera;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class TourTravelPlanPageResult {

    @SerializedName("items")
    private List<TourTravelPlanResult> items;

    @SerializedName("total")
    private int total;

    @SerializedName("page")
    private int page;

    @SerializedName("page_size")
    private int pageSize;

    public List<TourTravelPlanResult> getItems() {
        return items;
    }

    public int getTotal() {
        return total;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }
}
