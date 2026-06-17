package com.example.livecamera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TourManagementBackendConfigTest {

    @Test
    public void blankValuesResolveToPublicRailwayBackend() {
        assertEquals(
                TourManagementBackendConfig.DEFAULT_PUBLIC_BASE_URL,
                TourManagementBackendConfig.resolveBaseUrl("", "")
        );
    }

    @Test
    public void legacyLocalDefaultsResolveToPublicRailwayBackend() {
        assertEquals(
                TourManagementBackendConfig.DEFAULT_PUBLIC_BASE_URL,
                TourManagementBackendConfig.resolveBaseUrl("", "http://192.168.100.185:5000")
        );
        assertEquals(
                TourManagementBackendConfig.DEFAULT_PUBLIC_BASE_URL,
                TourManagementBackendConfig.resolveBaseUrl("", "http://10.18.117.136:5000")
        );
    }

    @Test
    public void validOverrideWinsOverBuildDefault() {
        assertEquals(
                "http://127.0.0.1:5000",
                TourManagementBackendConfig.resolveBaseUrl("http://127.0.0.1:5000/", TourManagementBackendConfig.DEFAULT_PUBLIC_BASE_URL)
        );
    }

    @Test
    public void invalidOverrideFallsBackToBuildDefault() {
        assertEquals(
                "https://example.com",
                TourManagementBackendConfig.resolveBaseUrl("not-a-url", "https://example.com/")
        );
    }

    @Test
    public void validatesOnlyHttpUrls() {
        assertTrue(TourManagementBackendConfig.isValidHttpUrl("https://backend.example.com"));
        assertTrue(TourManagementBackendConfig.isValidHttpUrl("http://127.0.0.1:5000"));
        assertFalse(TourManagementBackendConfig.isValidHttpUrl("ftp://backend.example.com"));
        assertFalse(TourManagementBackendConfig.isValidHttpUrl("backend.example.com"));
    }
}
