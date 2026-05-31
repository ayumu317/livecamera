package com.example.livecamera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class TourAuthSessionTest {

    @Test
    public void emptySessionFallsBackToLocalAppUserId() {
        TourAuthSession session = new TourAuthSession(new MemoryStorage());

        assertFalse(session.isLoggedIn());
        assertEquals(TourAuthSession.LOCAL_APP_USER_ID, session.getCurrentAppUserId());
    }

    @Test
    public void savePersistsUserIdentityAndToken() {
        TourAuthSession session = new TourAuthSession(new MemoryStorage());
        TourAuthResult authResult = new Gson().fromJson(
                "{\"token\":\"token-123\",\"expires_at\":\"2099-05-29T12:00:00Z\",\"user\":{\"id\":7,\"username\":\"traveler\",\"role\":\"user\",\"display_name\":\"旅行者\"}}",
                TourAuthResult.class
        );

        session.save(authResult);

        assertTrue(session.isLoggedIn());
        assertEquals("traveler", session.getCurrentAppUserId());
        assertEquals("token-123", session.getToken());
        assertEquals("旅行者", session.getDisplayName());
        assertEquals("user", session.getRole());
    }

    @Test
    public void clearRemovesLoginState() {
        TourAuthSession session = new TourAuthSession(new MemoryStorage());
        TourAuthResult authResult = new Gson().fromJson(
                "{\"token\":\"token-123\",\"user\":{\"id\":7,\"username\":\"traveler\",\"role\":\"user\"}}",
                TourAuthResult.class
        );

        session.save(authResult);
        session.clear();

        assertFalse(session.isLoggedIn());
        assertEquals(TourAuthSession.LOCAL_APP_USER_ID, session.getCurrentAppUserId());
        assertEquals("", session.getToken());
    }

    @Test
    public void updateUserPreservesTokenAndRefreshesDisplayName() {
        TourAuthSession session = new TourAuthSession(new MemoryStorage());
        Gson gson = new Gson();
        TourAuthResult authResult = gson.fromJson(
                "{\"token\":\"token-123\",\"user\":{\"id\":7,\"username\":\"traveler\",\"role\":\"user\",\"display_name\":\"Traveler\"}}",
                TourAuthResult.class
        );
        TourAuthResult profileResult = gson.fromJson(
                "{\"user\":{\"id\":7,\"username\":\"traveler\",\"role\":\"user\",\"display_name\":\"New Name\"}}",
                TourAuthResult.class
        );

        session.save(authResult);
        session.updateUser(profileResult.getUser());

        assertTrue(session.isLoggedIn());
        assertEquals("token-123", session.getToken());
        assertEquals("New Name", session.getDisplayName());
    }

    @Test
    public void expiredSessionFallsBackToLocalAppUserId() {
        TourAuthSession session = new TourAuthSession(new MemoryStorage());
        TourAuthResult authResult = new Gson().fromJson(
                "{\"token\":\"token-123\",\"expires_at\":\"2000-01-01T00:00:00Z\",\"user\":{\"id\":7,\"username\":\"traveler\",\"role\":\"user\"}}",
                TourAuthResult.class
        );

        session.save(authResult);

        assertFalse(session.isLoggedIn());
        assertEquals(TourAuthSession.LOCAL_APP_USER_ID, session.getCurrentAppUserId());
    }

    private static final class MemoryStorage implements TourAuthSession.Storage {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public String getString(String key) {
            String value = values.get(key);
            return value == null ? "" : value;
        }

        @Override
        public void putString(String key, String value) {
            values.put(key, value == null ? "" : value);
        }

        @Override
        public void clear() {
            values.clear();
        }
    }
}
