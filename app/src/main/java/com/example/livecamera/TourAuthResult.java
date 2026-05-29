package com.example.livecamera;

import com.google.gson.annotations.SerializedName;

public class TourAuthResult {

    @SerializedName("token")
    private String token;

    @SerializedName("expires_in")
    private long expiresIn;

    @SerializedName("expires_at")
    private String expiresAt;

    @SerializedName("user")
    private TourAuthUser user;

    public String getToken() {
        return token;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public TourAuthUser getUser() {
        return user;
    }
}
