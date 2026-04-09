package com.example.livecamera;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;

public class DoubaoVisionClient {

    private static final String DEBUG_TAG = "TOUR_DEBUG";
    private static final String PROMPT = "你现在是一个专业的二次元圣地巡礼与景点识别引擎。请仔细观察照片，判断它最可能对应的动漫取景地或现实景点。\n" +
            "要求：\n" +
            "1. 必须且只能输出合法的 JSON 字符串，不要有多余解释和 markdown 标记（如 ```json ）。\n" +
            "2. 大胆猜测并精准匹配！例如看到台场优先匹配《虹咲学园学园偶像同好会》；【极度重要】为了防止数据库搜索失败，你必须在 anime_names 数组中提供 3-5 个该地点的名称变体。顺序要求：[0]最简纯中文名(不带标点,如“虹咲学园学园偶像同好会”或“故宫”), [1]官方原名(日文或其他), [2]通用简称, [3]其他可能取景于此的候选动漫名。名称中绝对不要包含回车或多余空格。\n" +
            "3. 国内外场景区分：请判断该取景地或景点是否位于中国大陆。如果是，请务必将 is_domestic 字段设为 true；如果在日本或其他海外地区，请设为 false（注意必须是严格的布尔值，不能带引号）。国内景点时，请尽量把 location_name 输出为“城市+景点名”的形式，例如“北京 故宫”“上海 外滩”。\n" +
            "4. description 字段要按场景类型输出：如果 is_domestic=true，请输出适合游客阅读的简洁旅行科普介绍，可包含历史背景、看点或游览建议；如果 is_domestic=false，请继续输出动漫场景推测理由或剧情关联说明。\n" +
            "5. 如果画面完全无法识别任何风景要素，请在 description 中说明原因，并将 anime_names 置空。\n" +
            "6. JSON 结构必须严格如下：\n" +
            "{\n" +
            "  \"anime_names\": [\"中文名\", \"官方原名\", \"简称\", \"候选名\"],\n" +
            "  \"location_name\": \"现实中的地点名称（国内尽量返回城市+景点名）\",\n" +
            "  \"description\": \"简述场景剧情或推测理由\",\n" +
            "  \"is_domestic\": false\n" +
            "}";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient okHttpClient;
    private final Gson gson;

    public DoubaoVisionClient() {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(message ->
                Log.d(DEBUG_TAG, "OkHttp: " + message)
        );
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        gson = new Gson();
    }

    public interface Callback {
        void onSuccess(String responseBody);

        void onFailure(Exception e);
    }

    public void identifyLocation(String base64Image, double[] gpsLatLng, Callback callback) {
        if (callback == null) {
            return;
        }
        if (base64Image == null || base64Image.trim().isEmpty()) {
            callback.onFailure(new IllegalArgumentException("图片内容为空，无法识别"));
            return;
        }
        if (isBlank(BuildConfig.DOUBAO_BASE_URL)
                || isBlank(BuildConfig.DOUBAO_API_KEY)
                || isBlank(BuildConfig.DOUBAO_MODEL)) {
            callback.onFailure(new IllegalStateException("请先在 BuildConfig 中配置豆包接口参数"));
            return;
        }

        final String requestBodyString;
        try {
            requestBodyString = gson.toJson(buildRequestBody(base64Image, gpsLatLng));
            Log.d(DEBUG_TAG, "豆包请求体构建成功，JSON长度: " + requestBodyString.length());
        } catch (Exception e) {
            Log.e(DEBUG_TAG, "豆包请求体构建失败", e);
            callback.onFailure(new IllegalStateException("请求体构建失败", e));
            return;
        }

        Request request;
        try {
            String requestUrl = buildRequestUrl();
            Log.d(DEBUG_TAG, "豆包请求URL: " + requestUrl);
            request = new Request.Builder()
                    .url(requestUrl)
                    .addHeader("Authorization", "Bearer " + BuildConfig.DOUBAO_API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestBodyString, JSON_MEDIA_TYPE))
                    .build();
        } catch (IllegalArgumentException e) {
            Log.e(DEBUG_TAG, "豆包请求地址格式不正确", e);
            callback.onFailure(new IllegalArgumentException("豆包请求地址格式不正确", e));
            return;
        }

        okHttpClient.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(DEBUG_TAG, "豆包网络请求失败: " + safeMessage(e, "请检查网络后重试"), e);
                callback.onFailure(new IOException("网络请求失败，请检查网络后重试: "
                        + safeMessage(e, "未知网络错误"), e));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    String bodyString = responseBody != null ? responseBody.string() : "";
                    Log.d(DEBUG_TAG, "豆包响应状态码: " + response.code());
                    if (!response.isSuccessful()) {
                        Log.e(DEBUG_TAG, "豆包API报错: " + bodyString);
                        callback.onFailure(new IOException(buildHttpError(response.code(), bodyString)));
                        return;
                    }
                    if (bodyString.trim().isEmpty()) {
                        Log.e(DEBUG_TAG, "豆包接口返回为空");
                        callback.onFailure(new IOException("豆包接口返回为空"));
                        return;
                    }
                    Log.d(DEBUG_TAG, "豆包响应成功，Body长度: " + bodyString.length());
                    callback.onSuccess(bodyString);
                }
            }
        });
    }

    public void cancelAll() {
        okHttpClient.dispatcher().cancelAll();
    }

    private String buildRequestUrl() {
        String baseUrl = BuildConfig.DOUBAO_BASE_URL.trim();
        if (baseUrl.endsWith("/")) {
            return baseUrl + "chat/completions";
        }
        return baseUrl + "/chat/completions";
    }

    private JsonObject buildRequestBody(String base64Image, double[] gpsLatLng) {
        JsonObject root = new JsonObject();
        root.addProperty("model", BuildConfig.DOUBAO_MODEL);

        JsonObject imageUrlWrapper = new JsonObject();
        imageUrlWrapper.addProperty("url", "data:image/jpeg;base64," + base64Image);

        JsonObject imageContent = new JsonObject();
        imageContent.addProperty("type", "image_url");
        imageContent.add("image_url", imageUrlWrapper);

        JsonObject textContent = new JsonObject();
        textContent.addProperty("type", "text");
        textContent.addProperty("text", buildPrompt(gpsLatLng));

        JsonArray contentArray = new JsonArray();
        contentArray.add(imageContent);
        contentArray.add(textContent);

        JsonObject messageObject = new JsonObject();
        messageObject.addProperty("role", "user");
        messageObject.add("content", contentArray);

        JsonArray messages = new JsonArray();
        messages.add(messageObject);
        root.add("messages", messages);
        return root;
    }

    private String buildPrompt(double[] gpsLatLng) {
        if (gpsLatLng == null || gpsLatLng.length < 2) {
            return PROMPT;
        }
        return "【重要线索】这张照片的拍摄地 GPS 坐标约为：北纬 "
                + gpsLatLng[0]
                + "，东经 "
                + gpsLatLng[1]
                + "。请务必结合此绝对地理位置进行精准识别！\n"
                + PROMPT;
    }

    private String buildHttpError(int code, String bodyString) {
        String message = extractErrorMessage(bodyString);
        if (message.isEmpty()) {
            return "豆包接口请求失败（HTTP " + code + "）";
        }
        return "豆包接口请求失败（HTTP " + code + "）: " + message;
    }

    private String extractErrorMessage(String bodyString) {
        if (bodyString == null || bodyString.trim().isEmpty()) {
            return "";
        }
        try {
            JsonObject jsonObject = JsonParser.parseString(bodyString).getAsJsonObject();
            if (jsonObject.has("error") && jsonObject.get("error").isJsonObject()) {
                JsonObject errorObject = jsonObject.getAsJsonObject("error");
                if (errorObject.has("message")) {
                    return errorObject.get("message").getAsString();
                }
            }
            if (jsonObject.has("message") && !jsonObject.get("message").isJsonNull()) {
                return jsonObject.get("message").getAsString();
            }
            return "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private String safeMessage(Exception exception, String fallback) {
        String message = exception.getMessage();
        return isBlank(message) ? fallback : message;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
