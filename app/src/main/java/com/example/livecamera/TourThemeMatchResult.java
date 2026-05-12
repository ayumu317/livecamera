package com.example.livecamera;

import com.google.gson.annotations.SerializedName;

public class TourThemeMatchResult {

    @SerializedName("id")
    private int id;

    @SerializedName("theme_name")
    private String themeName;

    @SerializedName("theme_type")
    private String themeType;

    @SerializedName("keywords")
    private String keywords;

    @SerializedName("description")
    private String description;

    @SerializedName("cover_url")
    private String coverUrl;

    public int getId() {
        return id;
    }

    public String getThemeName() {
        return themeName;
    }

    public String getThemeType() {
        return themeType;
    }

    public String getKeywords() {
        return keywords;
    }

    public String getDescription() {
        return description;
    }

    public String getCoverUrl() {
        return coverUrl;
    }
}
