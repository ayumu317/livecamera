package com.example.livecamera;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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

public class SerpApiClient {

    private static final String DEBUG_TAG = "TOUR_DEBUG";
    private static final String SERP_API_BASE_URL = "https://serpapi.com/search.json";
    private static final String HARDCODED_SERP_API_KEY = "b1708ea9c1c5b592f10e4fa7661788b72c27d4fab8a590244596d1daf454e5c8";

    private final OkHttpClient okHttpClient;

    public SerpApiClient() {
        okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public interface Callback {
        void onSuccess(String imageUrl);

        void onFailure(Exception e);
    }

    public void fetchFallbackImage(String animeName, String locationName, Callback callback) {
        if (callback == null) {
            return;
        }
        if (isBlank(HARDCODED_SERP_API_KEY)
                || "请在这里直接填入你的真实 SerpApi Key".equals(HARDCODED_SERP_API_KEY)) {
            callback.onFailure(new IllegalStateException("请先在 SerpApiClient 中硬编码填入真实的 SerpApi Key"));
            return;
        }

        HttpUrl baseUrl = HttpUrl.parse(SERP_API_BASE_URL);
        if (baseUrl == null) {
            callback.onFailure(new IllegalStateException("SerpApi 地址配置错误"));
            return;
        }

        String query = buildQuery(animeName, locationName);
        HttpUrl url = baseUrl.newBuilder()
                .addQueryParameter("engine", "google_images")
                .addQueryParameter("q", query)
                .addQueryParameter("api_key", HARDCODED_SERP_API_KEY)
                .build();

        Log.d(DEBUG_TAG, "SerpApi 搜图关键词: " + query);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        okHttpClient.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(DEBUG_TAG, "SerpApi 搜图请求失败", e);
                callback.onFailure(new IOException("全网搜图失败: " + safeMessage(e, "网络异常"), e));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    String body = responseBody != null ? responseBody.string() : "";
                    if (!response.isSuccessful()) {
                        callback.onFailure(new IOException("SerpApi 请求失败（HTTP " + response.code() + "）"));
                        return;
                    }
                    if (body.trim().isEmpty()) {
                        callback.onFailure(new IOException("SerpApi 返回为空"));
                        return;
                    }
                    String imageUrl = parseImageUrl(body);
                    if (isBlank(imageUrl)) {
                        callback.onFailure(new IOException("全网未搜到可用参考图"));
                        return;
                    }
                    callback.onSuccess(imageUrl);
                }
            }
        });
    }

    private String parseImageUrl(String body) {
        JsonObject rootObject = JsonParser.parseString(body).getAsJsonObject();
        if (!rootObject.has("images_results") || !rootObject.get("images_results").isJsonArray()) {
            return "";
        }
        JsonArray imagesResults = rootObject.getAsJsonArray("images_results");
        if (imagesResults.size() == 0) {
            return "";
        }
        JsonElement firstItem = imagesResults.get(0);
        if (firstItem == null || !firstItem.isJsonObject()) {
            return "";
        }
        JsonObject imageObject = firstItem.getAsJsonObject();
        String original = getString(imageObject, "original");
        if (!isBlank(original)) {
            return original;
        }
        return getString(imageObject, "thumbnail");
    }

    private String buildQuery(String animeName, String locationName) {
        String safeAnimeName = animeName == null ? "" : animeName.trim();
        String safeLocationName = locationName == null ? "" : locationName.trim();
        return (safeAnimeName + " " + safeLocationName + " 聖地巡礼").trim();
    }

    private String getString(JsonObject jsonObject, String key) {
        if (jsonObject == null || key == null || !jsonObject.has(key) || jsonObject.get(key).isJsonNull()) {
            return "";
        }
        try {
            return jsonObject.get(key).getAsString().trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String safeMessage(Exception exception, String fallback) {
        String message = exception.getMessage();
        return isBlank(message) ? fallback : message;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
