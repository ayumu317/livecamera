package com.example.livecamera;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class LocationSearchClient {

    // Emulator uses 10.0.2.2. For real devices, replace with your LAN IP like http://192.168.1.10:8080
    public static final String BASE_URL = "https://deft-nonseditious-lashawnda.ngrok-free.dev";

    private static final String SEARCH_PATH = "api/location/search";

    private final OkHttpClient okHttpClient;

    public interface Callback {
        void onSuccess(@NonNull LocationResult locationResult);

        void onNotFound();

        void onFailure(@NonNull Exception exception);
    }

    public static final class LocationResult {
        private final String name;
        private final String address;
        private final Double longitude;
        private final Double latitude;

        public LocationResult(
                @Nullable String name,
                @Nullable String address,
                @Nullable Double longitude,
                @Nullable Double latitude
        ) {
            this.name = name != null ? name.trim() : "";
            this.address = address != null ? address.trim() : "";
            this.longitude = longitude;
            this.latitude = latitude;
        }

        @NonNull
        public String getName() {
            return name;
        }

        @NonNull
        public String getAddress() {
            return address;
        }

        @Nullable
        public Double getLongitude() {
            return longitude;
        }

        @Nullable
        public Double getLatitude() {
            return latitude;
        }

        public boolean hasCoordinates() {
            return latitude != null && longitude != null;
        }
    }

    public LocationSearchClient() {
        okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    public void search(@Nullable String keyword, boolean isDomestic, @Nullable Callback callback) {
        if (callback == null) {
            return;
        }
        if (isBlank(keyword)) {
            callback.onFailure(new IllegalArgumentException("地点关键词为空，无法调用定位网关"));
            return;
        }

        HttpUrl baseHttpUrl = HttpUrl.parse(BASE_URL);
        if (baseHttpUrl == null) {
            callback.onFailure(new IllegalStateException("BASE_URL 配置不合法: " + BASE_URL));
            return;
        }

        HttpUrl requestUrl = baseHttpUrl.newBuilder()
                .addPathSegments(SEARCH_PATH)
                .addQueryParameter("keyword", keyword.trim())
                .addQueryParameter("isDomestic", String.valueOf(isDomestic))
                .build();

        Request request = new Request.Builder()
                .url(requestUrl)
                .get()
                .build();

        okHttpClient.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onFailure(new IOException("定位网关请求失败: " + safeMessage(e), e));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    String responseText = responseBody != null ? responseBody.string() : "";
                    if (response.code() == 404) {
                        callback.onNotFound();
                        return;
                    }
                    if (!response.isSuccessful()) {
                        callback.onFailure(new IOException("定位网关请求失败，HTTP " + response.code()));
                        return;
                    }
                    if (isBlank(responseText)) {
                        callback.onFailure(new IOException("定位网关返回为空"));
                        return;
                    }
                    callback.onSuccess(parseLocationResult(responseText));
                }
            }
        });
    }

    private LocationResult parseLocationResult(String responseText) {
        try {
            JsonObject jsonObject = JsonParser.parseString(responseText).getAsJsonObject();
            return new LocationResult(
                    getString(jsonObject, "name"),
                    getString(jsonObject, "address"),
                    getDouble(jsonObject, "longitude"),
                    getDouble(jsonObject, "latitude")
            );
        } catch (Exception e) {
            throw new IllegalStateException("定位网关响应解析失败", e);
        }
    }

    @NonNull
    private String getString(@NonNull JsonObject jsonObject, @NonNull String key) {
        if (!jsonObject.has(key) || jsonObject.get(key).isJsonNull()) {
            return "";
        }
        try {
            return jsonObject.get(key).getAsString();
        } catch (Exception ignored) {
            return "";
        }
    }

    @Nullable
    private Double getDouble(@NonNull JsonObject jsonObject, @NonNull String key) {
        if (!jsonObject.has(key) || jsonObject.get(key).isJsonNull()) {
            return null;
        }
        try {
            return jsonObject.get(key).getAsDouble();
        } catch (Exception ignored) {
            return null;
        }
    }

    @NonNull
    private String safeMessage(@NonNull Exception exception) {
        String message = exception.getMessage();
        return isBlank(message) ? "未知网络错误" : message;
    }

    private boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}
