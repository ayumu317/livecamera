package com.example.livecamera;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class TourAuthSession {

    public static final String LOCAL_APP_USER_ID = "android-local";

    private static final String PREFS_NAME = "tour_auth_session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_EXPIRES_AT = "expires_at";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String KEY_ROLE = "role";

    private final Storage storage;

    public TourAuthSession(@NonNull Context context) {
        this(new SharedPreferencesStorage(context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)));
    }

    TourAuthSession(@NonNull Storage storage) {
        this.storage = storage;
    }

    public void save(@Nullable TourAuthResult authResult) {
        if (authResult == null || isBlank(authResult.getToken()) || authResult.getUser() == null
                || isBlank(authResult.getUser().getUsername())) {
            return;
        }
        TourAuthUser user = authResult.getUser();
        storage.putString(KEY_TOKEN, authResult.getToken());
        storage.putString(KEY_EXPIRES_AT, authResult.getExpiresAt());
        storage.putString(KEY_USER_ID, String.valueOf(user.getId()));
        storage.putString(KEY_USERNAME, user.getUsername());
        storage.putString(KEY_DISPLAY_NAME, firstNonBlank(
                user.getDisplayName(),
                user.getNickname(),
                user.getRealName(),
                user.getUsername()
        ));
        storage.putString(KEY_ROLE, user.getRole());
    }

    public void clear() {
        storage.clear();
    }

    public boolean isLoggedIn() {
        return hasUsableLogin();
    }

    public String getCurrentAppUserId() {
        if (!hasUsableLogin()) {
            return LOCAL_APP_USER_ID;
        }
        return firstNonBlank(storage.getString(KEY_USERNAME), LOCAL_APP_USER_ID);
    }

    public String getToken() {
        return storage.getString(KEY_TOKEN);
    }

    public String getDisplayName() {
        return firstNonBlank(storage.getString(KEY_DISPLAY_NAME), getCurrentAppUserId());
    }

    public String getRole() {
        return storage.getString(KEY_ROLE);
    }

    public String getExpiresAt() {
        return storage.getString(KEY_EXPIRES_AT);
    }

    private boolean hasUsableLogin() {
        return !isBlank(getToken()) && !isBlank(storage.getString(KEY_USERNAME)) && !isExpired();
    }

    private boolean isExpired() {
        String expiresAt = getExpiresAt();
        if (isBlank(expiresAt)) {
            return false;
        }
        Date expiresAtDate = parseDate(expiresAt);
        return expiresAtDate != null && expiresAtDate.getTime() <= System.currentTimeMillis();
    }

    @Nullable
    private Date parseDate(@NonNull String value) {
        String[] patterns = new String[] {
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss'Z'"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setTimeZone(TimeZone.getTimeZone("UTC"));
                return format.parse(value);
            } catch (Exception ignored) {
                // Try the next common ISO-8601 shape returned by the backend.
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values != null) {
            for (String value : values) {
                if (!isBlank(value)) {
                    return value.trim();
                }
            }
        }
        return "";
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    interface Storage {
        @Nullable
        String getString(@NonNull String key);

        void putString(@NonNull String key, @Nullable String value);

        void clear();
    }

    private static final class SharedPreferencesStorage implements Storage {
        private final SharedPreferences preferences;

        private SharedPreferencesStorage(@NonNull SharedPreferences preferences) {
            this.preferences = preferences;
        }

        @Override
        public String getString(@NonNull String key) {
            return preferences.getString(key, "");
        }

        @Override
        public void putString(@NonNull String key, @Nullable String value) {
            preferences.edit().putString(key, value == null ? "" : value).apply();
        }

        @Override
        public void clear() {
            preferences.edit().clear().apply();
        }
    }
}
