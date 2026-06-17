package com.example.livecamera;

import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

public class DoubaoVisionClient {

    private static final String TAG = "DoubaoVision";
    private static final String DEFAULT_RESPONSES_URL = "https://ark.cn-beijing.volces.com/api/v3/responses";
    private static final String DEFAULT_MODEL_ID = "doubao-seed-2-0-lite-260428";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;

    public DoubaoVisionClient() {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(message ->
                Log.d(TAG, "OkHttp: " + message)
        );
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);

        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor)
                .build();
    }

    public interface Callback {
        void onSuccess(String responseBody);

        default void onSuccess(RecognitionResponse response) {
            onSuccess(response == null ? "" : response.businessJson);
        }

        void onFailure(Exception e);
    }

    public static final class RecognitionResponse {
        public final String businessJson;
        public final UsageStats usageStats;

        RecognitionResponse(String businessJson, @Nullable UsageStats usageStats) {
            this.businessJson = businessJson;
            this.usageStats = usageStats;
        }
    }

    public static final class UsageStats {
        public final int inputTokens;
        public final int outputTokens;
        public final int totalTokens;
        public final int cachedInputTokens;

        UsageStats(int inputTokens, int outputTokens, int totalTokens, int cachedInputTokens) {
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.totalTokens = totalTokens;
            this.cachedInputTokens = cachedInputTokens;
        }

        public boolean hasUsage() {
            return inputTokens > 0 || outputTokens > 0 || totalTokens > 0;
        }
    }

    public static final class RecognitionResult {
        public final boolean hasDomesticDecision;
        public final boolean isDomestic;
        public final String locationName;
        public final List<String> animeNames;
        public final String description;
        public final double confidence;
        public final String reason;
        public final String rawJsonText;

        RecognitionResult(
                boolean hasDomesticDecision,
                boolean isDomestic,
                String locationName,
                List<String> animeNames,
                String description,
                double confidence,
                String reason,
                String rawJsonText
        ) {
            this.hasDomesticDecision = hasDomesticDecision;
            this.isDomestic = isDomestic;
            this.locationName = locationName;
            this.animeNames = animeNames;
            this.description = description;
            this.confidence = confidence;
            this.reason = reason;
            this.rawJsonText = rawJsonText;
        }
    }

    public void identifyLocation(String base64Image, @Nullable double[] gpsLatLng, Callback callback) {
        identifyLocation(base64Image, gpsLatLng, "auto", callback);
    }

    public void identifyLocation(String base64Image, @Nullable double[] gpsLatLng, String mode, Callback callback) {
        String normalizedMode = mode == null ? "auto" : mode.trim().toLowerCase(Locale.ROOT);
        String prompt;
        if ("anime".equals(normalizedMode)) {
            prompt = buildAnimeOnlyPrompt(gpsLatLng);
        } else if ("domestic".equals(normalizedMode)) {
            prompt = buildDomesticOnlyPrompt(gpsLatLng);
        } else {
            prompt = buildAutoPrompt(gpsLatLng);
        }
        Log.d(TAG, "identifyLocation mode=" + normalizedMode);
        sendRecognitionRequest(base64Image, prompt, callback);
    }

    public void recognizeDomesticTravel(String base64Image, @Nullable double[] gpsLatLng, Callback callback) {
        sendRecognitionRequest(base64Image, buildDomesticOnlyPrompt(gpsLatLng), callback);
    }

    public void recognizeAnimePilgrimage(String base64Image, @Nullable double[] gpsLatLng, Callback callback) {
        sendRecognitionRequest(base64Image, buildAnimeOnlyPrompt(gpsLatLng), callback);
    }

    public void identifyAnimeWithUserWork(
            String base64Image,
            @Nullable double[] gpsLatLng,
            String userAnimeName,
            Callback callback
    ) {
        identifyAnimeWithUserWork(base64Image, gpsLatLng, userAnimeName, null, callback);
    }

    public void identifyAnimeWithUserWork(
            String base64Image,
            @Nullable double[] gpsLatLng,
            String userAnimeName,
            @Nullable String userLocationHint,
            Callback callback
    ) {
        sendRecognitionRequest(base64Image, buildAnimeWithUserWorkPrompt(gpsLatLng, userAnimeName, userLocationHint), callback);
    }

    private void sendRecognitionRequest(String base64Image, String prompt, Callback callback) {
        if (callback == null) {
            return;
        }
        if (isBlank(base64Image)) {
            callback.onFailure(new IllegalArgumentException("图片内容为空，无法识别"));
            return;
        }

        String apiKey = firstNonBlank(BuildConfig.ARK_API_KEY, BuildConfig.DOUBAO_API_KEY);
        if (isBlank(apiKey)) {
            callback.onFailure(new IllegalStateException("请在 local.properties 中配置 ARK_API_KEY"));
            return;
        }

        String modelId = firstNonBlank(BuildConfig.DOUBAO_MODEL_ID, BuildConfig.DOUBAO_MODEL, DEFAULT_MODEL_ID);
        String requestUrl = firstNonBlank(BuildConfig.DOUBAO_RESPONSES_URL, DEFAULT_RESPONSES_URL);

        JsonObject imageContent = new JsonObject();
        imageContent.addProperty("type", "input_image");
        imageContent.addProperty("image_url", buildDataImageUrl(base64Image));

        JsonObject textContent = new JsonObject();
        textContent.addProperty("type", "input_text");
        textContent.addProperty("text", prompt);

        JsonArray content = new JsonArray();
        content.add(imageContent);
        content.add(textContent);

        JsonObject userInput = new JsonObject();
        userInput.addProperty("role", "user");
        userInput.add("content", content);

        JsonArray input = new JsonArray();
        input.add(userInput);

        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", modelId);
        requestJson.add("input", input);

        Log.d(TAG, "request model=" + modelId + ", url=" + requestUrl + ", imageBase64Length=" + base64Image.length());

        Request request = new Request.Builder()
                .url(requestUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestJson.toString(), JSON))
                .build();

        httpClient.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Doubao request failed", e);
                callback.onFailure(new IOException("豆包识别请求失败：" + safeMessage(e, "网络超时或异常"), e));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response closeableResponse = response) {
                    String responseBody = closeableResponse.body() != null
                            ? closeableResponse.body().string()
                            : "";
                    if (!closeableResponse.isSuccessful()) {
                        callback.onFailure(new IOException("豆包接口请求失败（HTTP "
                                + closeableResponse.code() + "）: " + responseBody));
                        return;
                    }

                    JSONObject root = new JSONObject(responseBody);
                    String rawText = extractTextFromResponses(root);
                    String cleanedJson = cleanJsonText(rawText);
                    Log.d(TAG, "rawText=" + rawText);
                    Log.d(TAG, "cleanedJson=" + cleanedJson);

                    if (isBlank(cleanedJson)) {
                        callback.onFailure(new IllegalStateException("豆包响应中没有可解析的 JSON"));
                        return;
                    }
                    callback.onSuccess(new RecognitionResponse(cleanedJson, extractUsageStats(root)));
                } catch (Exception e) {
                    callback.onFailure(e);
                }
            }
        });
    }

    public void cancelAll() {
        httpClient.dispatcher().cancelAll();
    }

    private String buildAnimeOnlyPrompt(@Nullable double[] gpsLatLng) {
        StringBuilder builder = new StringBuilder();
        builder.append("你是 LiveCamera-LBS 的动漫圣地巡礼识别助手。\n");
        builder.append("当前用户已选择“动漫巡礼模式”，所以你的首要任务不是旅游景点识别，而是判断图片可能对应的动漫取景地、海外巡礼场景、现实地点线索或可能关联作品。\n\n");
        builder.append("必须只返回 JSON，不要 Markdown，不要解释。\n\n");
        builder.append("返回结构：{\"anime_names\":[\"可能作品中文名\",\"官方原名\",\"简称\",\"其他候选作品\"],\"location_name\":\"可能的现实取景地或地点线索\",\"description\":\"简短说明为什么可能是这些作品或地点\",\"is_domestic\":false,\"confidence\":0.0,\"reason\":\"一句话判断依据\"}\n\n");
        builder.append("规则：\n");
        builder.append("1. 不要把普通建筑、展馆、街景直接当成国内旅游景点。\n");
        builder.append("2. 如果画面是展馆、学校、车站、街道、桥、海边、神社、商业街、演唱会场馆，要优先尝试识别为巡礼地点线索。\n");
        builder.append("3. location_name 可以输出地点线索，例如“东京国际展示场”“台场”“有明”“镰仓高校前”“秋叶原”“地点待确认”。\n");
        builder.append("4. anime_names 尽量返回 3-5 个可能作品或名称变体。\n");
        builder.append("5. 如果无法确认作品，也不要改判国内景点；保持 is_domestic=false，anime_names 可为空，并在 description 说明需要用户补充作品名。\n");
        builder.append("6. 只有当图片明显是中国境内景点，且完全不像动漫巡礼素材时，才允许 is_domestic=true，但 confidence 必须降低，并在 reason 中提示建议切换国内旅行模式。\n");
        builder.append("7. 不要输出旅游攻略式介绍。\n");
        builder.append("8. 不要只返回中国城市名或中国景点名。");
        appendGpsHint(builder, gpsLatLng);
        return builder.toString();
    }

    private String buildDomesticOnlyPrompt(@Nullable double[] gpsLatLng) {
        StringBuilder builder = new StringBuilder();
        builder.append("你是 LiveCamera-LBS 的国内旅行识别助手。\n");
        builder.append("当前用户已选择“国内旅行模式”，请识别中国境内景点、城市地标、建筑或自然风光。\n\n");
        builder.append("必须只返回 JSON，不要 Markdown，不要解释。\n\n");
        builder.append("返回结构：{\"anime_names\":[],\"location_name\":\"城市 + 景点名\",\"description\":\"面向游客的简短旅行介绍\",\"is_domestic\":true,\"confidence\":0.0,\"reason\":\"一句话判断依据\"}\n\n");
        builder.append("规则：\n");
        builder.append("1. location_name 不能只返回城市名，例如不能只返回“上海”，要返回“上海 东方明珠”或“东方明珠广播电视塔”。\n");
        builder.append("2. 不要输出动漫作品名。\n");
        builder.append("3. 如果图片不像中国境内景点，也可以返回低置信度结果，并提示建议切换动漫巡礼或自动判断模式。");
        appendGpsHint(builder, gpsLatLng);
        return builder.toString();
    }

    private String buildAutoPrompt(@Nullable double[] gpsLatLng) {
        StringBuilder builder = new StringBuilder();
        builder.append("你是 LiveCamera-LBS 的场景分流助手。\n");
        builder.append("请判断图片更适合“国内旅行”还是“动漫圣地巡礼”。\n\n");
        builder.append("必须只返回 JSON，不要 Markdown，不要解释。\n\n");
        builder.append("返回结构：{\"anime_names\":[\"可能作品中文名\",\"官方原名\",\"简称\",\"其他候选作品\"],\"location_name\":\"现实地点名称或地点线索\",\"description\":\"简短说明\",\"is_domestic\":false,\"confidence\":0.0,\"reason\":\"一句话判断依据\"}\n\n");
        builder.append("判断规则：\n");
        builder.append("1. 中国境内明确景点、城市地标、自然风光 → is_domestic=true。\n");
        builder.append("2. 动漫截图、海外街景、海外车站、学校、展馆、神社、商业街、海边、巡礼参考图 → is_domestic=false。\n");
        builder.append("3. 如果图像像真实建筑但无法确认是否中国境内，不要默认判国内；优先输出 is_domestic=false，并给出地点待确认。\n");
        builder.append("4. 国内时 location_name 必须是“城市 + 景点名”，不能只给城市。\n");
        builder.append("5. 海外/巡礼时 anime_names 尽量返回候选作品；无法确认时 anime_names 为空，但不要强行国内化。\n");
        builder.append("6. 不确定时 confidence 降低。");
        appendGpsHint(builder, gpsLatLng);
        return builder.toString();
    }

    private String buildAnimeWithUserWorkPrompt(@Nullable double[] gpsLatLng, String userAnimeName) {
        return buildAnimeWithUserWorkPrompt(gpsLatLng, userAnimeName, null);
    }

    private String buildAnimeWithUserWorkPrompt(@Nullable double[] gpsLatLng, String userAnimeName, @Nullable String userLocationHint) {
        String safeAnimeName = firstNonBlank(userAnimeName, "用户指定作品");
        String safeLocationHint = firstNonBlank(userLocationHint, "");
        StringBuilder builder = new StringBuilder();
        builder.append("你是 LiveCamera-LBS 的动漫圣地巡礼匹配助手。\n");
        builder.append("用户已经指定作品名为：「").append(safeAnimeName).append("」。\n");
        if (!isBlank(safeLocationHint)) {
            builder.append("用户同时提供了地点线索或正确地点为：「").append(safeLocationHint).append("」。\n");
            builder.append("请把该地点线索作为强约束，结合当前上传图片判断它在该作品中的巡礼对应关系；不要忽略用户提供的地点。\n");
        }
        builder.append("请不要再判断它是不是国内旅游景点。\n");
        builder.append("请基于“用户指定作品 + 当前上传图片”判断图片最可能对应该作品中的哪些现实巡礼地点、场景线索或取景地。\n\n");
        builder.append("必须只返回 JSON，不要 Markdown，不要解释。\n\n");
        builder.append("返回结构：{\"anime_names\":[\"用户指定作品名\",\"可能的官方原名\",\"简称\",\"相关名称变体\"],\"location_name\":\"最可能的现实地点线索，例如 东京台场 / 有明 / 镰仓高校前 / 地点待确认\",\"description\":\"说明图片中的哪些视觉元素与该作品或巡礼地点有关\",\"is_domestic\":false,\"confidence\":0.0,\"reason\":\"一句话判断依据\",\"visual_keywords\":[\"车站\",\"海边\",\"展馆\",\"桥\",\"学校\",\"街道\"],\"spot_search_keywords\":[\"作品名 + 地点线索\",\"地点线索\",\"作品名 + 场景关键词\"]}\n\n");
        builder.append("规则：\n");
        builder.append("1. 用户已经指定作品名，anime_names 第一项必须是用户输入的作品名：").append(safeAnimeName).append("。\n");
        builder.append("2. 不要把图片改判为国内旅行景点。\n");
        builder.append("3. 即使图片像真实建筑，也要优先解释为该作品下的巡礼地点线索。\n");
        builder.append("4. location_name 不确定时可以写“地点待确认”，但 visual_keywords 必须尽量从图片提取。\n");
        builder.append("5. spot_search_keywords 用于后续匹配 Anitabi / SerpApi 结果。\n");
        builder.append("6. 不要输出旅游攻略式介绍。\n");
        builder.append("7. 如果用户提供了地点线索，location_name 应优先输出该地点或更精确的同义地点名，spot_search_keywords 必须包含该地点线索。\n");
        builder.append("8. 不要返回 JSON 以外的内容。");
        appendGpsHint(builder, gpsLatLng);
        return builder.toString();
    }

    private void appendGpsHint(StringBuilder builder, @Nullable double[] gpsLatLng) {
        if (gpsLatLng != null && gpsLatLng.length >= 2) {
            builder.append("\n设备照片 GPS 可作为弱参考：纬度=");
            builder.append(gpsLatLng[0]);
            builder.append("，经度=");
            builder.append(gpsLatLng[1]);
            builder.append("。不要仅凭 GPS 下结论，仍以图片内容为主。");
        }
    }

    private String buildDataImageUrl(String base64Image) {
        String image = base64Image == null ? "" : base64Image.trim();
        if (image.startsWith("data:image/")) {
            return image;
        }
        return "data:image/jpeg;base64," + image;
    }

    @Nullable
    private String buildResponsesUrlFromLegacyBase() {
        if (isBlank(BuildConfig.DOUBAO_BASE_URL)) {
            return null;
        }
        String baseUrl = BuildConfig.DOUBAO_BASE_URL.trim();
        if (baseUrl.endsWith("/responses")) {
            return baseUrl;
        }
        if (baseUrl.endsWith("/")) {
            return baseUrl + "responses";
        }
        return baseUrl + "/responses";
    }

    private String extractTextFromResponses(JSONObject root) {
        String outputText = root.optString("output_text", "");
        if (!isBlank(outputText)) {
            return outputText;
        }

        String output = extractTextFromAny(root.opt("output"));
        if (!isBlank(output)) {
            return output;
        }

        JSONArray choices = root.optJSONArray("choices");
        if (choices != null) {
            for (int i = 0; i < choices.length(); i++) {
                JSONObject choice = choices.optJSONObject(i);
                if (choice == null) {
                    continue;
                }
                JSONObject message = choice.optJSONObject("message");
                if (message != null) {
                    String content = extractTextFromAny(message.opt("content"));
                    if (!isBlank(content)) {
                        return content;
                    }
                }
                String text = choice.optString("text", "");
                if (!isBlank(text)) {
                    return text;
                }
            }
        }

        return extractTextFromAny(root);
    }

    static UsageStats extractUsageStats(JSONObject root) {
        if (root == null) {
            return null;
        }
        JSONObject usage = root.optJSONObject("usage");
        if (usage == null) {
            return null;
        }
        int inputTokens = firstPositiveInt(
                usage.optInt("input_tokens", 0),
                usage.optInt("prompt_tokens", 0)
        );
        int outputTokens = firstPositiveInt(
                usage.optInt("output_tokens", 0),
                usage.optInt("completion_tokens", 0)
        );
        int totalTokens = firstPositiveInt(
                usage.optInt("total_tokens", 0),
                inputTokens + outputTokens
        );
        int cachedInputTokens = 0;
        JSONObject inputDetails = usage.optJSONObject("input_tokens_details");
        if (inputDetails == null) {
            inputDetails = usage.optJSONObject("prompt_tokens_details");
        }
        if (inputDetails != null) {
            cachedInputTokens = inputDetails.optInt("cached_tokens", 0);
        }
        UsageStats result = new UsageStats(inputTokens, outputTokens, totalTokens, cachedInputTokens);
        return result.hasUsage() ? result : null;
    }

    private static int firstPositiveInt(int... values) {
        if (values != null) {
            for (int value : values) {
                if (value > 0) {
                    return value;
                }
            }
        }
        return 0;
    }

    private String extractTextFromAny(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "";
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            String text = firstNonBlank(
                    object.optString("text", ""),
                    object.optString("output_text", ""),
                    object.optString("content", "")
            );
            if (!isBlank(text)) {
                return text;
            }
            String nestedContent = extractTextFromArray(object.optJSONArray("content"));
            if (!isBlank(nestedContent)) {
                return nestedContent;
            }
            return extractTextFromAny(object.opt("message"));
        }
        if (value instanceof JSONArray) {
            return extractTextFromArray((JSONArray) value);
        }
        return "";
    }

    private String extractTextFromArray(@Nullable JSONArray array) {
        if (array == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < array.length(); i++) {
            Object item = array.opt(i);
            String text = "";
            if (item instanceof JSONObject) {
                JSONObject object = (JSONObject) item;
                String type = object.optString("type", "");
                if ("output_text".equals(type) || "text".equals(type) || "message".equals(type)) {
                    text = firstNonBlank(object.optString("text", ""), object.optString("content", ""));
                }
                if (isBlank(text)) {
                    text = extractTextFromAny(object.opt("content"));
                }
            } else {
                text = extractTextFromAny(item);
            }
            if (!isBlank(text)) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(text.trim());
            }
        }
        return builder.toString();
    }

    private String cleanJsonText(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();

        for (int i = 0; i < 4; i++) {
            text = stripMarkdownFence(text).trim();

            if (text.startsWith("[")) {
                String extracted = "";
                try {
                    extracted = extractTextFromArray(new JSONArray(text));
                } catch (Exception ignored) {
                    // Fall through to substring extraction below.
                }
                if (!isBlank(extracted) && !extracted.trim().equals(text)) {
                    text = extracted.trim();
                    continue;
                }
            }

            if (text.startsWith("{")) {
                try {
                    JSONObject object = new JSONObject(text);
                    if (looksLikeBusinessJson(object)) {
                        return object.toString();
                    }
                    String extracted = firstNonBlank(
                            extractTextFromAny(object.opt("text")),
                            extractTextFromAny(object.opt("output_text")),
                            extractTextFromAny(object.opt("content")),
                            extractTextFromAny(object.opt("message"))
                    );
                    if (!isBlank(extracted) && !extracted.trim().equals(text)) {
                        text = extracted.trim();
                        continue;
                    }
                } catch (Exception ignored) {
                    // Fall through to substring extraction below.
                }
            }
            break;
        }

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1).trim();
            try {
                JSONObject object = new JSONObject(text);
                if (!looksLikeBusinessJson(object)) {
                    String nestedText = firstNonBlank(
                            extractTextFromAny(object.opt("text")),
                            extractTextFromAny(object.opt("output_text")),
                            extractTextFromAny(object.opt("content"))
                    );
                    if (!isBlank(nestedText)) {
                        return cleanJsonText(nestedText);
                    }
                }
                return object.toString();
            } catch (Exception ignored) {
                return text;
            }
        }
        return text.trim();
    }

    private String stripMarkdownFence(String text) {
        if (text == null) {
            return "";
        }
        String stripped = text.trim();
        if (stripped.startsWith("```")) {
            stripped = stripped.replaceFirst("^```json\\s*", "");
            stripped = stripped.replaceFirst("^```\\s*", "");
            stripped = stripped.replaceFirst("\\s*```$", "");
        }
        return stripped.trim();
    }

    private boolean looksLikeBusinessJson(JSONObject object) {
        return object != null
                && (object.has("is_domestic")
                || object.has("anime_names")
                || object.has("location_name")
                || object.has("description"));
    }

    private RecognitionResult parseRecognitionResult(String jsonText) throws Exception {
        JSONObject object = new JSONObject(cleanJsonText(jsonText));
        boolean hasDomesticDecision = object.has("is_domestic");
        boolean isDomestic = object.optBoolean("is_domestic", false);
        String locationName = object.optString("location_name", "");
        String description = object.optString("description", "");
        double confidence = object.has("confidence") ? object.optDouble("confidence", -1D) : -1D;
        String reason = object.optString("reason", "");
        List<String> animeNames = new ArrayList<>();
        JSONArray array = object.optJSONArray("anime_names");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                String name = array.optString(i, "").trim();
                if (!isBlank(name)) {
                    animeNames.add(name);
                }
            }
        }
        return new RecognitionResult(
                hasDomesticDecision,
                isDomestic,
                locationName,
                animeNames,
                description,
                confidence,
                reason,
                object.toString()
        );
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String safeMessage(Exception exception, String fallback) {
        String message = exception.getMessage();
        return isBlank(message) ? fallback : message;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
