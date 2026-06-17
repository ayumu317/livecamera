package com.example.livecamera;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class TrialAccessManager {

    public static final String FEATURE_RECOGNITION = "recognition";
    public static final int DAILY_RECOGNITION_LIMIT = 3;

    private static final String PREFS_NAME = "trial_access";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_RECOGNITION_DATE = "recognition_date";
    private static final String KEY_RECOGNITION_USED = "recognition_used";

    private final Storage storage;
    private final DateProvider dateProvider;

    public TrialAccessManager(@NonNull Context context) {
        this(new SharedPreferencesStorage(context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)), new SystemDateProvider());
    }

    TrialAccessManager(@NonNull Storage storage, @NonNull DateProvider dateProvider) {
        this.storage = storage;
        this.dateProvider = dateProvider;
    }

    @NonNull
    public String getDeviceId() {
        String existing = storage.getString(KEY_DEVICE_ID);
        if (!isBlank(existing)) {
            return existing.trim();
        }
        String generated = UUID.randomUUID().toString();
        storage.putString(KEY_DEVICE_ID, generated);
        return generated;
    }

    @NonNull
    public TrialSnapshot getRecognitionSnapshot() {
        resetIfNeeded();
        int used = Math.max(0, storage.getInt(KEY_RECOGNITION_USED, 0));
        return new TrialSnapshot(
                DAILY_RECOGNITION_LIMIT,
                used,
                Math.max(0, DAILY_RECOGNITION_LIMIT - used),
                today()
        );
    }

    public boolean hasRecognitionQuota() {
        return getRecognitionSnapshot().getRemaining() > 0;
    }

    @NonNull
    public TrialSnapshot markRecognitionConsumed() {
        resetIfNeeded();
        int used = Math.max(0, storage.getInt(KEY_RECOGNITION_USED, 0));
        if (used < DAILY_RECOGNITION_LIMIT) {
            used += 1;
            storage.putInt(KEY_RECOGNITION_USED, used);
        }
        return getRecognitionSnapshot();
    }

    private void resetIfNeeded() {
        String currentDate = today();
        String storedDate = storage.getString(KEY_RECOGNITION_DATE);
        if (!currentDate.equals(storedDate)) {
            storage.putString(KEY_RECOGNITION_DATE, currentDate);
            storage.putInt(KEY_RECOGNITION_USED, 0);
        }
    }

    private String today() {
        return dateProvider.today();
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class TrialSnapshot {
        private final int limit;
        private final int used;
        private final int remaining;
        private final String date;

        TrialSnapshot(int limit, int used, int remaining, @NonNull String date) {
            this.limit = limit;
            this.used = used;
            this.remaining = remaining;
            this.date = date;
        }

        public int getLimit() {
            return limit;
        }

        public int getUsed() {
            return used;
        }

        public int getRemaining() {
            return remaining;
        }

        public String getDate() {
            return date;
        }
    }

    interface Storage {
        @Nullable
        String getString(@NonNull String key);

        int getInt(@NonNull String key, int defaultValue);

        void putString(@NonNull String key, @Nullable String value);

        void putInt(@NonNull String key, int value);
    }

    interface DateProvider {
        @NonNull
        String today();
    }

    private static final class SystemDateProvider implements DateProvider {
        @Override
        public String today() {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        }
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
        public int getInt(@NonNull String key, int defaultValue) {
            return preferences.getInt(key, defaultValue);
        }

        @Override
        public void putString(@NonNull String key, @Nullable String value) {
            preferences.edit().putString(key, value == null ? "" : value).apply();
        }

        @Override
        public void putInt(@NonNull String key, int value) {
            preferences.edit().putInt(key, value).apply();
        }
    }
}
