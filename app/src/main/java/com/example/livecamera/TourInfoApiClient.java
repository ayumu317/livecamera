package com.example.livecamera;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TourInfoApiClient {

    private static final String TAG = "TourInfoApiClient";
    private static final String DEFAULT_BASE_URL = BuildConfig.MANAGEMENT_BASE_URL;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String baseUrl;

    public TourInfoApiClient() {
        this(DEFAULT_BASE_URL);
    }

    public TourInfoApiClient(@Nullable String baseUrl) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.gson = new Gson();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .writeTimeout(3, TimeUnit.SECONDS)
                .build();
    }

    public interface ApiCallback<T> {
        void onSuccess(T data);

        void onFailure(@NonNull Exception exception);
    }

    public static final class RecognitionRecordPayload {
        private final Map<String, Object> values = new LinkedHashMap<>();

        public RecognitionRecordPayload put(String key, @Nullable Object value) {
            values.put(key, value);
            return this;
        }
    }

    public static final class CorrectionPayload {
        private final Map<String, Object> values = new LinkedHashMap<>();

        public CorrectionPayload put(String key, @Nullable Object value) {
            values.put(key, value);
            return this;
        }
    }

    public static final class RouteFavoritePayload {
        private final Map<String, Object> values = new LinkedHashMap<>();

        public RouteFavoritePayload put(String key, @Nullable Object value) {
            values.put(key, value);
            return this;
        }
    }

    public static final class TravelPlacePayload {
        private final Map<String, Object> values = new LinkedHashMap<>();

        public TravelPlacePayload put(String key, @Nullable Object value) {
            values.put(key, value);
            return this;
        }
    }

    public void login(
            @Nullable String username,
            @Nullable String password,
            @Nullable ApiCallback<TourAuthResult> callback
    ) {
        if (isBlank(username) || isBlank(password)) {
            notifyFailure(callback, new IllegalArgumentException("username or password is empty"));
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", username.trim());
        payload.put("password", password);
        HttpUrl url = requireBaseUrl()
                .addPathSegments("api/auth/login")
                .build();
        executePost(url, payload, TourAuthResult.class, callback);
    }

    public void register(
            @Nullable String username,
            @Nullable String password,
            @Nullable String confirmPassword,
            @Nullable ApiCallback<TourAuthResult> callback
    ) {
        if (isBlank(username) || isBlank(password)) {
            notifyFailure(callback, new IllegalArgumentException("username or password is empty"));
            return;
        }
        if (!isBlank(confirmPassword) && !password.equals(confirmPassword)) {
            notifyFailure(callback, new IllegalArgumentException("password confirmation does not match"));
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", username.trim());
        payload.put("password", password);
        payload.put("nickname", username.trim());
        HttpUrl url = requireBaseUrl()
                .addPathSegments("api/auth/register")
                .build();
        executePost(url, payload, TourAuthResult.class, callback);
    }

    public void me(@Nullable String token, @Nullable ApiCallback<TourAuthResult> callback) {
        if (isBlank(token)) {
            notifyFailure(callback, new IllegalArgumentException("token is empty"));
            return;
        }
        HttpUrl url = requireBaseUrl()
                .addPathSegments("api/auth/me")
                .build();
        executeGet(url, TourAuthResult.class, token, callback);
    }

    public void matchTheme(@Nullable String keyword, @Nullable ApiCallback<List<TourThemeMatchResult>> callback) {
        matchTheme(keyword, null, callback);
    }

    public void matchTheme(
            @Nullable String keyword,
            @Nullable String token,
            @Nullable ApiCallback<List<TourThemeMatchResult>> callback
    ) {
        if (isBlank(keyword)) {
            notifyFailure(callback, new IllegalArgumentException("keyword is empty"));
            return;
        }
        HttpUrl url = requireBaseUrl()
                .addPathSegments("api/app/theme/match")
                .addQueryParameter("keyword", keyword.trim())
                .build();
        Type type = new TypeToken<List<TourThemeMatchResult>>() { }.getType();
        executeGet(url, type, token, callback);
    }

    public void getRecognitionAssist(
            @Nullable String keyword,
            @Nullable String appUserId,
            @Nullable ApiCallback<TourRecognitionAssistResponse> callback
    ) {
        getRecognitionAssist(keyword, appUserId, null, callback);
    }

    public void getRecognitionAssist(
            @Nullable String keyword,
            @Nullable String appUserId,
            @Nullable String token,
            @Nullable ApiCallback<TourRecognitionAssistResponse> callback
    ) {
        if (isBlank(keyword)) {
            notifyFailure(callback, new IllegalArgumentException("keyword is empty"));
            return;
        }
        HttpUrl.Builder builder = requireBaseUrl()
                .addPathSegments("api/app/recognition/assist")
                .addQueryParameter("keyword", keyword.trim());
        if (!isBlank(appUserId)) {
            builder.addQueryParameter("app_user_id", appUserId.trim());
        }
        executeGet(builder.build(), TourRecognitionAssistResponse.class, token, callback);
    }

    public void createRecognitionRecord(
            @NonNull RecognitionRecordPayload payload,
            @Nullable ApiCallback<TourRecognitionRecordResult> callback
    ) {
        createRecognitionRecord(payload, null, callback);
    }

    public void createRecognitionRecord(
            @NonNull RecognitionRecordPayload payload,
            @Nullable String token,
            @Nullable ApiCallback<TourRecognitionRecordResult> callback
    ) {
        HttpUrl url = requireBaseUrl()
                .addPathSegments("api/app/recognition/record")
                .build();
        Type type = TourRecognitionRecordResult.class;
        executePost(url, payload.values, type, token, callback);
    }

    public void submitCorrection(@NonNull CorrectionPayload payload, @Nullable ApiCallback<Void> callback) {
        submitCorrection(payload, null, callback);
    }

    public void submitCorrection(
            @NonNull CorrectionPayload payload,
            @Nullable String token,
            @Nullable ApiCallback<Void> callback
    ) {
        HttpUrl url = requireBaseUrl()
                .addPathSegments("api/app/correction/submit")
                .build();
        executePost(url, payload.values, Void.class, token, callback);
    }

    public void getRecognitionCost(int recognitionId, @Nullable ApiCallback<TourRecognitionCostResult> callback) {
        getRecognitionCost(recognitionId, null, callback);
    }

    public void getRecognitionCost(
            int recognitionId,
            @Nullable String token,
            @Nullable ApiCallback<TourRecognitionCostResult> callback
    ) {
        if (recognitionId <= 0) {
            notifyFailure(callback, new IllegalArgumentException("recognitionId is invalid"));
            return;
        }
        HttpUrl url = requireBaseUrl()
                .addPathSegments("api/app/recognition/cost/" + recognitionId)
                .build();
        executeGet(url, TourRecognitionCostResult.class, token, callback);
    }

    public void getLocationDetail(
            int locationId,
            @Nullable ApiCallback<TourLocationDetailResult> callback
    ) {
        getLocationDetail(locationId, null, callback);
    }

    public void getLocationDetail(
            int locationId,
            @Nullable String token,
            @Nullable ApiCallback<TourLocationDetailResult> callback
    ) {
        if (locationId <= 0) {
            notifyFailure(callback, new IllegalArgumentException("locationId is invalid"));
            return;
        }
        HttpUrl url = requireBaseUrl()
                .addPathSegments("api/app/location/detail/" + locationId)
                .build();
        executeGet(url, TourLocationDetailResult.class, token, callback);
    }

    public void favoriteRoute(
            @NonNull RouteFavoritePayload payload,
            @Nullable ApiCallback<TourFavoriteRouteResult> callback
    ) {
        favoriteRoute(payload, null, callback);
    }

    public void favoriteRoute(
            @NonNull RouteFavoritePayload payload,
            @Nullable String token,
            @Nullable ApiCallback<TourFavoriteRouteResult> callback
    ) {
        HttpUrl url = requireBaseUrl()
                .addPathSegments("api/app/route/favorite")
                .build();
        executePost(url, payload.values, TourFavoriteRouteResult.class, token, callback);
    }

    public void listTravelPlans(
            int page,
            int pageSize,
            @Nullable String token,
            @Nullable ApiCallback<TourTravelPlanPageResult> callback
    ) {
        if (isBlank(token)) {
            notifyFailure(callback, new IllegalArgumentException("token is empty"));
            return;
        }
        HttpUrl url = requireBaseUrl()
                .addPathSegments("api/admin/travel-plans")
                .addQueryParameter("page", String.valueOf(Math.max(page, 1)))
                .addQueryParameter("page_size", String.valueOf(Math.max(pageSize, 1)))
                .build();
        executeGet(url, TourTravelPlanPageResult.class, token, callback);
    }

    public void getTravelPlanOverview(
            int planId,
            @Nullable String token,
            @Nullable ApiCallback<TourTravelPlanOverviewResult> callback
    ) {
        if (planId <= 0) {
            notifyFailure(callback, new IllegalArgumentException("planId is invalid"));
            return;
        }
        if (isBlank(token)) {
            notifyFailure(callback, new IllegalArgumentException("token is empty"));
            return;
        }
        HttpUrl url = requireBaseUrl()
                .addPathSegments("api/admin/travel-plans/" + planId + "/overview")
                .build();
        executeGet(url, TourTravelPlanOverviewResult.class, token, callback);
    }

    public void addPlaceToTravelPlan(
            int planId,
            @NonNull TravelPlacePayload payload,
            @Nullable String token,
            @Nullable ApiCallback<TourTravelAttractionResult> callback
    ) {
        if (planId <= 0) {
            notifyFailure(callback, new IllegalArgumentException("planId is invalid"));
            return;
        }
        if (isBlank(token)) {
            notifyFailure(callback, new IllegalArgumentException("token is empty"));
            return;
        }
        HttpUrl url = requireBaseUrl()
                .addPathSegments("api/admin/travel-plans/" + planId + "/attractions/from-place")
                .build();
        executePost(url, payload.values, TourTravelAttractionResult.class, token, callback);
    }

    public void cancelAll() {
        httpClient.dispatcher().cancelAll();
    }

    private <T> void executeGet(
            @NonNull HttpUrl url,
            @NonNull Type dataType,
            @Nullable ApiCallback<T> callback
    ) {
        executeGet(url, dataType, null, callback);
    }

    private <T> void executeGet(
            @NonNull HttpUrl url,
            @NonNull Type dataType,
            @Nullable String token,
            @Nullable ApiCallback<T> callback
    ) {
        Request request = addAuthHeader(new Request.Builder().url(url).get(), token).build();
        enqueue(request, dataType, callback);
    }

    private <T> void executePost(
            @NonNull HttpUrl url,
            @NonNull Map<String, Object> payload,
            @NonNull Type dataType,
            @Nullable ApiCallback<T> callback
    ) {
        executePost(url, payload, dataType, null, callback);
    }

    private <T> void executePost(
            @NonNull HttpUrl url,
            @NonNull Map<String, Object> payload,
            @NonNull Type dataType,
            @Nullable String token,
            @Nullable ApiCallback<T> callback
    ) {
        RequestBody requestBody = RequestBody.create(gson.toJson(payload), JSON);
        Request request = addAuthHeader(new Request.Builder().url(url).post(requestBody), token).build();
        enqueue(request, dataType, callback);
    }

    private Request.Builder addAuthHeader(@NonNull Request.Builder builder, @Nullable String token) {
        if (!isBlank(token)) {
            builder.header("Authorization", "Bearer " + token.trim());
        }
        return builder;
    }

    private <T> void enqueue(
            @NonNull Request request,
            @NonNull Type dataType,
            @Nullable ApiCallback<T> callback
    ) {
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                notifyFailure(callback, e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response closeable = response) {
                    if (!closeable.isSuccessful()) {
                        notifyFailure(callback, new IOException("HTTP " + closeable.code()));
                        return;
                    }
                    String body = closeable.body() != null ? closeable.body().string() : "";
                    notifySuccess(callback, parseData(body, dataType));
                } catch (Exception e) {
                    notifyFailure(callback, e);
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T parseData(@NonNull String responseBody, @NonNull Type dataType) {
        JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
        boolean success = !root.has("success") || root.get("success").getAsBoolean();
        if (!success) {
            String message = root.has("message") ? root.get("message").getAsString() : "management backend error";
            throw new IllegalStateException(message);
        }
        if (dataType == Void.class) {
            return null;
        }
        JsonElement data = root.get("data");
        if (data == null || data.isJsonNull()) {
            return null;
        }
        return (T) gson.fromJson(data, dataType);
    }

    private HttpUrl.Builder requireBaseUrl() {
        HttpUrl parsed = HttpUrl.parse(baseUrl);
        if (parsed == null) {
            throw new IllegalStateException("Invalid management backend base url: " + baseUrl);
        }
        return parsed.newBuilder();
    }

    private String normalizeBaseUrl(@Nullable String value) {
        if (isBlank(value)) {
            return DEFAULT_BASE_URL;
        }
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    private <T> void notifySuccess(@Nullable ApiCallback<T> callback, @Nullable T data) {
        if (callback != null) {
            callback.onSuccess(data);
        }
    }

    private void notifyFailure(@Nullable ApiCallback<?> callback, @NonNull Exception exception) {
        Log.d(TAG, "optional management backend request failed: " + exception.getMessage(), exception);
        if (callback != null) {
            callback.onFailure(exception);
        }
    }
}
