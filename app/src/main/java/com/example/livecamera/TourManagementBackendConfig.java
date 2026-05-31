package com.example.livecamera;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import okhttp3.HttpUrl;

public final class TourManagementBackendConfig {

    public static final String DEFAULT_PUBLIC_BASE_URL = "https://backend-production-d4a53.up.railway.app";
    public static final String PREFS_NAME = "livecamera_settings";
    public static final String PREF_MANAGEMENT_BASE_URL_OVERRIDE = "management_base_url_override";

    private static final String LEGACY_DEVICE_TEST_HOST = "http://192.168.100.185:5000";
    private static final String LEGACY_LOCAL_DEFAULT_HOST = "http://10.18.117.136:5000";

    private TourManagementBackendConfig() {
    }

    public static TourInfoApiClient newClient(@NonNull Context context) {
        return new TourInfoApiClient(resolveBaseUrl(context));
    }

    public static String resolveBaseUrl(@NonNull Context context) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return resolveBaseUrl(
                preferences.getString(PREF_MANAGEMENT_BASE_URL_OVERRIDE, ""),
                BuildConfig.MANAGEMENT_BASE_URL
        );
    }

    public static void saveOverride(@NonNull Context context, @Nullable String value) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_MANAGEMENT_BASE_URL_OVERRIDE, normalizeBaseUrl(value))
                .apply();
    }

    public static void clearOverride(@NonNull Context context) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(PREF_MANAGEMENT_BASE_URL_OVERRIDE)
                .apply();
    }

    public static boolean hasOverride(@NonNull Context context) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return !isBlank(preferences.getString(PREF_MANAGEMENT_BASE_URL_OVERRIDE, ""));
    }

    static String resolveBaseUrl(@Nullable String overrideValue, @Nullable String buildDefaultValue) {
        String normalizedOverride = normalizeBaseUrl(overrideValue);
        if (isValidHttpUrl(normalizedOverride)) {
            return normalizedOverride;
        }
        String normalizedDefault = normalizeBaseUrl(buildDefaultValue);
        if (isValidHttpUrl(normalizedDefault) && !isLegacyDefault(normalizedDefault)) {
            return normalizedDefault;
        }
        return DEFAULT_PUBLIC_BASE_URL;
    }

    static String normalizeBaseUrl(@Nullable String value) {
        if (isBlank(value)) {
            return "";
        }
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    static boolean isValidHttpUrl(@Nullable String value) {
        if (isBlank(value)) {
            return false;
        }
        HttpUrl parsed = HttpUrl.parse(value);
        return parsed != null && ("http".equals(parsed.scheme()) || "https".equals(parsed.scheme()));
    }

    static String shortLabel(@Nullable String value) {
        String normalized = normalizeBaseUrl(value);
        HttpUrl parsed = HttpUrl.parse(normalized);
        if (parsed == null) {
            return DEFAULT_PUBLIC_BASE_URL;
        }
        return parsed.host();
    }

    private static boolean isLegacyDefault(@NonNull String value) {
        return LEGACY_DEVICE_TEST_HOST.equals(value) || LEGACY_LOCAL_DEFAULT_HOST.equals(value);
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}
