package com.example.livecamera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.json.JSONObject;
import org.junit.Test;

public class DoubaoVisionClientTest {

    @Test
    public void extractUsageStatsReadsResponsesTokenFields() throws Exception {
        JSONObject root = new JSONObject("{\"usage\":{\"input_tokens\":1200,\"output_tokens\":360,\"total_tokens\":1560}}");

        DoubaoVisionClient.UsageStats usageStats = DoubaoVisionClient.extractUsageStats(root);

        assertEquals(1200, usageStats.inputTokens);
        assertEquals(360, usageStats.outputTokens);
    }

    @Test
    public void extractUsageStatsFallsBackToLegacyTokenFields() throws Exception {
        JSONObject root = new JSONObject("{\"usage\":{\"prompt_tokens\":900,\"completion_tokens\":120}}");

        DoubaoVisionClient.UsageStats usageStats = DoubaoVisionClient.extractUsageStats(root);

        assertEquals(900, usageStats.inputTokens);
        assertEquals(120, usageStats.outputTokens);
    }

    @Test
    public void extractUsageStatsReturnsNullWhenMissing() throws Exception {
        JSONObject root = new JSONObject("{\"id\":\"resp_1\"}");

        assertNull(DoubaoVisionClient.extractUsageStats(root));
    }
}
