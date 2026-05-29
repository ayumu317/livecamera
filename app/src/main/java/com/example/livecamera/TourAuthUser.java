package com.example.livecamera;

import com.google.gson.annotations.SerializedName;

public class TourAuthUser {

    @SerializedName("id")
    private int id;

    @SerializedName("username")
    private String username;

    @SerializedName("role")
    private String role;

    @SerializedName("real_name")
    private String realName;

    @SerializedName("nickname")
    private String nickname;

    @SerializedName("display_name")
    private String displayName;

    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public String getRealName() {
        return realName;
    }

    public String getNickname() {
        return nickname;
    }

    public String getDisplayName() {
        return displayName;
    }
}
