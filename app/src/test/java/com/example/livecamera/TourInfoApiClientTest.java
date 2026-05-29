package com.example.livecamera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TourInfoApiClientTest {

    private MockWebServer server;
    private TourInfoApiClient client;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new TourInfoApiClient(server.url("/").toString());
    }

    @After
    public void tearDown() throws Exception {
        if (client != null) {
            client.cancelAll();
        }
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    public void matchThemeBuildsExpectedUrlAndParsesSuccessResponse() throws Exception {
        server.enqueue(jsonResponse("{\"success\":true,\"code\":200,\"message\":\"success\",\"data\":[{\"id\":1,\"theme_name\":\"Tokyo Anime Pilgrimage\",\"theme_type\":\"anime\",\"keywords\":\"tokyo\",\"description\":\"route\",\"cover_url\":\"cover.jpg\"}]}"));

        Result<List<TourThemeMatchResult>> result = awaitSuccess(callback -> client.matchTheme("Tokyo", callback));
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);

        assertNotNull(request);
        assertEquals("/api/app/theme/match?keyword=Tokyo", request.getPath());
        assertEquals(1, result.data.size());
        assertEquals("Tokyo Anime Pilgrimage", result.data.get(0).getThemeName());
    }

    @Test
    public void matchThemeRejectsBlankKeywordWithoutNetworkRequest() throws Exception {
        Exception error = this.<List<TourThemeMatchResult>>awaitFailure(callback -> client.matchTheme("  ", callback));

        assertTrue(error instanceof IllegalArgumentException);
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void recognitionAssistBuildsExpectedUrlAndParsesCandidates() throws Exception {
        server.enqueue(jsonResponse("{\"success\":true,\"code\":200,\"message\":\"success\",\"data\":{\"keyword\":\"Akihabara\",\"app_user_id\":\"android-local\",\"strategy\":\"rule_based_history_assist\",\"items\":[{\"candidate_source\":\"learned\",\"candidate_type\":\"learned_candidate\",\"learned_candidate_id\":8,\"location_id\":null,\"theme_name\":\"Tokyo Anime\",\"location_name\":\"Hidden Radio Hall\",\"address\":\"Akihabara\",\"city\":\"Tokyo\",\"country\":\"Japan\",\"score\":86.5,\"recommend_reason\":\"user correction\",\"user_confirmed_count\":2,\"user_correction_count\":1,\"global_confirmed_count\":3,\"global_correction_count\":2}]}}"));

        Result<TourRecognitionAssistResponse> result = awaitSuccess(
                callback -> client.getRecognitionAssist("Akihabara", "android-local", callback)
        );
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);

        assertNotNull(request);
        assertEquals("/api/app/recognition/assist?keyword=Akihabara&app_user_id=android-local", request.getPath());
        assertNotNull(result.data.getItems());
        assertEquals(1, result.data.getItems().size());
        TourRecognitionAssistCandidate candidate = result.data.getItems().get(0);
        assertEquals("learned", candidate.getCandidateSource());
        assertEquals("Hidden Radio Hall", candidate.getLocationName());
        assertEquals(86.5, candidate.getScore(), 0.001);
    }

    @Test
    public void createRecognitionRecordPostsJsonAndParsesRecord() throws Exception {
        server.enqueue(jsonResponse("{\"success\":true,\"code\":201,\"message\":\"created\",\"data\":{\"id\":12,\"app_user_id\":\"demo_user\",\"recognized_theme\":\"Tokyo\",\"recognized_location\":\"Akihabara\",\"status\":\"confirmed\"}}"));
        TourInfoApiClient.RecognitionRecordPayload payload = new TourInfoApiClient.RecognitionRecordPayload()
                .put("app_user_id", "demo_user")
                .put("recognized_theme", "Tokyo")
                .put("recognized_location", "Akihabara")
                .put("status", "confirmed");

        Result<TourRecognitionRecordResult> result = awaitSuccess(callback -> client.createRecognitionRecord(payload, callback));
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);

        assertNotNull(request);
        assertEquals("POST", request.getMethod());
        assertEquals("/api/app/recognition/record", request.getPath());
        assertTrue(request.getBody().readUtf8().contains("\"app_user_id\":\"demo_user\""));
        assertEquals(12, result.data.getId());
        assertEquals("confirmed", result.data.getStatus());
    }

    @Test
    public void businessFailureResponseTriggersFailureCallback() throws Exception {
        server.enqueue(jsonResponse("{\"success\":false,\"code\":400,\"message\":\"keyword required\",\"data\":null}"));

        Exception error = this.<List<TourThemeMatchResult>>awaitFailure(callback -> client.matchTheme("Tokyo", callback));

        assertTrue(error instanceof IllegalStateException);
        assertEquals("keyword required", error.getMessage());
    }

    @Test
    public void httpErrorResponseTriggersFailureCallback() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("server error"));

        Exception error = this.<TourRecognitionCostResult>awaitFailure(callback -> client.getRecognitionCost(1, callback));

        assertTrue(error.getMessage().contains("HTTP 500"));
    }

    @Test
    public void invalidRecognitionIdFailsBeforeNetworkRequest() throws Exception {
        Exception error = this.<TourRecognitionCostResult>awaitFailure(callback -> client.getRecognitionCost(0, callback));

        assertTrue(error instanceof IllegalArgumentException);
        assertEquals(0, server.getRequestCount());
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private <T> Result<T> awaitSuccess(ClientCall<T> call) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> data = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        call.invoke(new TourInfoApiClient.ApiCallback<T>() {
            @Override
            public void onSuccess(T value) {
                data.set(value);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception exception) {
                error.set(exception);
                latch.countDown();
            }
        });
        assertTrue("callback timed out", latch.await(5, TimeUnit.SECONDS));
        if (error.get() != null) {
            fail("expected success but got failure: " + error.get().getMessage());
        }
        return new Result<>(data.get());
    }

    private <T> Exception awaitFailure(ClientCall<T> call) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        call.invoke(new TourInfoApiClient.ApiCallback<T>() {
            @Override
            public void onSuccess(T value) {
                latch.countDown();
            }

            @Override
            public void onFailure(Exception exception) {
                error.set(exception);
                latch.countDown();
            }
        });
        assertTrue("callback timed out", latch.await(5, TimeUnit.SECONDS));
        assertNotNull("expected failure callback", error.get());
        return error.get();
    }

    private interface ClientCall<T> {
        void invoke(TourInfoApiClient.ApiCallback<T> callback);
    }

    private static final class Result<T> {
        private final T data;

        private Result(T data) {
            this.data = data;
        }
    }
}
