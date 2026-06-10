package com.example.livecamera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class TrialAccessManagerTest {

    @Test
    public void generatedDeviceIdIsStable() {
        MemoryStorage storage = new MemoryStorage();
        TrialAccessManager manager = new TrialAccessManager(storage, () -> "2026-06-10");

        String first = manager.getDeviceId();
        String second = manager.getDeviceId();

        assertFalse(first.isEmpty());
        assertEquals(first, second);
    }

    @Test
    public void recognitionQuotaConsumesUntilDailyLimit() {
        TrialAccessManager manager = new TrialAccessManager(new MemoryStorage(), () -> "2026-06-10");

        assertTrue(manager.hasRecognitionQuota());
        assertEquals(2, manager.markRecognitionConsumed().getRemaining());
        assertEquals(1, manager.markRecognitionConsumed().getRemaining());
        assertEquals(0, manager.markRecognitionConsumed().getRemaining());
        assertFalse(manager.hasRecognitionQuota());
        assertEquals(0, manager.markRecognitionConsumed().getRemaining());
    }

    @Test
    public void recognitionQuotaResetsOnNewDay() {
        MutableDateProvider dateProvider = new MutableDateProvider("2026-06-10");
        TrialAccessManager manager = new TrialAccessManager(new MemoryStorage(), dateProvider);

        manager.markRecognitionConsumed();
        manager.markRecognitionConsumed();
        assertEquals(1, manager.getRecognitionSnapshot().getRemaining());

        dateProvider.date = "2026-06-11";

        assertEquals(3, manager.getRecognitionSnapshot().getRemaining());
        assertTrue(manager.hasRecognitionQuota());
    }

    private static final class MutableDateProvider implements TrialAccessManager.DateProvider {
        private String date;

        private MutableDateProvider(String date) {
            this.date = date;
        }

        @Override
        public String today() {
            return date;
        }
    }

    private static final class MemoryStorage implements TrialAccessManager.Storage {
        private final Map<String, String> strings = new HashMap<>();
        private final Map<String, Integer> ints = new HashMap<>();

        @Override
        public String getString(String key) {
            String value = strings.get(key);
            return value == null ? "" : value;
        }

        @Override
        public int getInt(String key, int defaultValue) {
            Integer value = ints.get(key);
            return value == null ? defaultValue : value;
        }

        @Override
        public void putString(String key, String value) {
            strings.put(key, value == null ? "" : value);
        }

        @Override
        public void putInt(String key, int value) {
            ints.put(key, value);
        }
    }
}
