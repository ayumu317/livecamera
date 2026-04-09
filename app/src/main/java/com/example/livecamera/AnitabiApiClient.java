package com.example.livecamera;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class AnitabiApiClient {

    private static final String ANITABI_BASE_URL = "https://api.anitabi.cn/";
    private static final String BANGUMI_SEARCH_BASE_URL = "https://api.bgm.tv/search/subject/";
    private static final Pattern LABEL_PREFIX_PATTERN =
            Pattern.compile("^(动漫名称|动画名称|作品名称|番剧名称)\\s*[：:]\\s*");

    private final OkHttpClient okHttpClient;
    private final Gson gson;

    public AnitabiApiClient() {
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    public interface ApiCallback<T> {
        void onSuccess(T data);

        void onFailure(Exception e);
    }

    public void getBangumiLite(int subjectId, ApiCallback<BangumiLiteResponse> callback) {
        if (callback == null) {
            return;
        }
        if (subjectId <= 0) {
            callback.onFailure(new IllegalArgumentException("subjectId 必须大于 0"));
            return;
        }

        HttpUrl url = HttpUrl.parse(ANITABI_BASE_URL)
                .newBuilder()
                .addPathSegment("bangumi")
                .addPathSegment(String.valueOf(subjectId))
                .addPathSegment("lite")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        executeRequest(request, BangumiLiteResponse.class, new ResponseParser<BangumiLiteResponse>() {
            @Override
            public BangumiLiteResponse parse(String responseBody) throws Exception {
                BangumiLiteResponse response = gson.fromJson(responseBody, BangumiLiteResponse.class);
                if (response == null) {
                    throw new IOException("Anitabi 返回的作品轻量信息为空");
                }
                if (parseIntSafely(response.getId()) <= 0
                        || (isBlank(response.getCn()) && isBlank(response.getTitle()))) {
                    throw new IOException("Anitabi 返回的作品轻量信息缺少关键字段");
                }
                return response;
            }
        }, callback);
    }

    public void getPointsDetail(int subjectId, boolean haveImage, ApiCallback<List<PointDetail>> callback) {
        if (callback == null) {
            return;
        }
        if (subjectId <= 0) {
            callback.onFailure(new IllegalArgumentException("subjectId 必须大于 0"));
            return;
        }

        HttpUrl.Builder urlBuilder = HttpUrl.parse(ANITABI_BASE_URL)
                .newBuilder()
                .addPathSegment("bangumi")
                .addPathSegment(String.valueOf(subjectId))
                .addPathSegment("points")
                .addPathSegment("detail");
        if (haveImage) {
            urlBuilder.addQueryParameter("haveImage", "true");
        }

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .get()
                .build();

        Type responseType = new TypeToken<List<PointDetail>>() {}.getType();
        executeRequest(request, responseType, new ResponseParser<List<PointDetail>>() {
            @Override
            public List<PointDetail> parse(String responseBody) throws Exception {
                List<PointDetail> pointDetails = gson.fromJson(responseBody, responseType);
                if (pointDetails == null || pointDetails.isEmpty()) {
                    throw new IOException("Anitabi 未返回地标详情");
                }
                return pointDetails;
            }
        }, callback);
    }

    public void searchSubjectIdByName(String keyword, ApiCallback<Integer> callback) {
        if (callback == null) {
            return;
        }

        String normalizedKeyword = normalizeKeyword(keyword);
        if (isBlank(normalizedKeyword)) {
            callback.onFailure(new IllegalArgumentException("搜索关键词不能为空"));
            return;
        }

        HttpUrl baseUrl = HttpUrl.parse(BANGUMI_SEARCH_BASE_URL);
        if (baseUrl == null) {
            callback.onFailure(new IOException("Bangumi 搜索地址配置错误"));
            return;
        }

        HttpUrl url = baseUrl.newBuilder()
                .addPathSegment(normalizedKeyword)
                .addQueryParameter("type", "2")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        executeRequest(request, BangumiSearchResponse.class, new ResponseParser<Integer>() {
            @Override
            public Integer parse(String responseBody) throws Exception {
                BangumiSearchResponse response = gson.fromJson(responseBody, BangumiSearchResponse.class);
                if (response == null || response.getList() == null || response.getList().isEmpty()) {
                    throw new IOException("未找到对应 Bangumi subjectId");
                }
                Integer subjectId = findFirstAnitabiAvailableSubjectId(response.getList());
                if (subjectId == null || subjectId <= 0) {
                    throw new IOException("Bangumi 已找到作品，但 Anitabi 暂未收录对应巡礼条目");
                }
                return subjectId;
            }
        }, callback);
    }

    public static String getHighResImageUrl(String originUrl) {
        if (originUrl == null || originUrl.isEmpty()) {
            return originUrl;
        }
        if (!originUrl.contains("?plan=h160")) {
            return originUrl;
        }
        return originUrl.replace("?plan=h160", "?plan=h360");
    }

    private <T> void executeRequest(
            Request request,
            Type type,
            ResponseParser<T> parser,
            ApiCallback<T> callback
    ) {
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onFailure(new IOException("网络请求失败: " + safeMessage(e, "请检查网络后重试"), e));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    String body = responseBody != null ? responseBody.string() : "";
                    if (!response.isSuccessful()) {
                        callback.onFailure(new IOException(buildHttpErrorMessage(response.code(), body)));
                        return;
                    }
                    if (body.trim().isEmpty()) {
                        callback.onFailure(new IOException("接口返回为空"));
                        return;
                    }

                    try {
                        T data = parser.parse(body);
                        if (data == null) {
                            callback.onFailure(new IOException("接口解析结果为空"));
                            return;
                        }
                        callback.onSuccess(data);
                    } catch (Exception e) {
                        callback.onFailure(asException(type, e));
                    }
                }
            }
        });
    }

    private Exception asException(Type type, Exception exception) {
        if (exception instanceof IOException) {
            return exception;
        }
        String typeName = type != null ? type.getTypeName() : "unknown";
        return new IOException("解析 " + typeName + " 失败: " + safeMessage(exception, "数据格式错误"), exception);
    }

    private String buildHttpErrorMessage(int code, String body) {
        String remoteMessage = extractRemoteMessage(body);
        if (isBlank(remoteMessage)) {
            return "接口请求失败（HTTP " + code + "）";
        }
        return "接口请求失败（HTTP " + code + "）: " + remoteMessage;
    }

    private String extractRemoteMessage(String body) {
        if (isBlank(body)) {
            return "";
        }
        try {
            JsonObject jsonObject = JsonParser.parseString(body).getAsJsonObject();
            if (jsonObject.has("message") && !jsonObject.get("message").isJsonNull()) {
                return jsonObject.get("message").getAsString();
            }
            if (jsonObject.has("error") && jsonObject.get("error").isJsonObject()) {
                JsonObject errorObject = jsonObject.getAsJsonObject("error");
                if (errorObject.has("message") && !errorObject.get("message").isJsonNull()) {
                    return errorObject.get("message").getAsString();
                }
            }
        } catch (Exception ignored) {
            return "";
        }
        return "";
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return "";
        }
        String normalized = keyword.trim();
        normalized = LABEL_PREFIX_PATTERN.matcher(normalized).replaceFirst("");
        if (normalized.startsWith("《") && normalized.endsWith("》") && normalized.length() > 2) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized;
    }

    private int parseIntSafely(String value) {
        if (isBlank(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String safeMessage(Exception exception, String fallback) {
        String message = exception.getMessage();
        return isBlank(message) ? fallback : message;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Integer findFirstAnitabiAvailableSubjectId(List<BangumiSearchItem> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }

        int maxCandidates = Math.min(items.size(), 10);
        for (int i = 0; i < maxCandidates; i++) {
            BangumiSearchItem item = items.get(i);
            int subjectId = item != null ? parseIntSafely(item.getId()) : 0;
            if (subjectId <= 0) {
                continue;
            }
            if (isAnitabiSubjectAvailable(subjectId)) {
                return subjectId;
            }
        }
        return null;
    }

    private boolean isAnitabiSubjectAvailable(int subjectId) {
        HttpUrl url = HttpUrl.parse(ANITABI_BASE_URL)
                .newBuilder()
                .addPathSegment("bangumi")
                .addPathSegment(String.valueOf(subjectId))
                .addPathSegment("lite")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return false;
            }
            ResponseBody body = response.body();
            return body != null && !isBlank(body.string());
        } catch (Exception ignored) {
            return false;
        }
    }

    private interface ResponseParser<T> {
        T parse(String responseBody) throws Exception;
    }

    public static class BangumiLiteResponse {
        @SerializedName("id")
        private String id;

        @SerializedName("cn")
        private String cn;

        @SerializedName("title")
        private String title;

        @SerializedName("city")
        private String city;

        @SerializedName("cover")
        private String cover;

        @SerializedName("color")
        private String color;

        @SerializedName("geo")
        private List<String> geo;

        @SerializedName("zoom")
        private String zoom;

        @SerializedName("modified")
        private String modified;

        @SerializedName("litePoints")
        private List<LitePoint> litePoints;

        @SerializedName("pointsLength")
        private String pointsLength;

        @SerializedName("imagesLength")
        private String imagesLength;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getCn() {
            return cn;
        }

        public void setCn(String cn) {
            this.cn = cn;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getCover() {
            return cover;
        }

        public void setCover(String cover) {
            this.cover = cover;
        }

        public String getColor() {
            return color;
        }

        public void setColor(String color) {
            this.color = color;
        }

        public List<String> getGeo() {
            return geo;
        }

        public void setGeo(List<String> geo) {
            this.geo = geo;
        }

        public String getZoom() {
            return zoom;
        }

        public void setZoom(String zoom) {
            this.zoom = zoom;
        }

        public String getModified() {
            return modified;
        }

        public void setModified(String modified) {
            this.modified = modified;
        }

        public List<LitePoint> getLitePoints() {
            return litePoints;
        }

        public void setLitePoints(List<LitePoint> litePoints) {
            this.litePoints = litePoints;
        }

        public String getPointsLength() {
            return pointsLength;
        }

        public void setPointsLength(String pointsLength) {
            this.pointsLength = pointsLength;
        }

        public String getImagesLength() {
            return imagesLength;
        }

        public void setImagesLength(String imagesLength) {
            this.imagesLength = imagesLength;
        }
    }

    public static class LitePoint {
        @SerializedName("id")
        private String id;

        @SerializedName("cn")
        private String cn;

        @SerializedName("name")
        private String name;

        @SerializedName("image")
        private String image;

        @SerializedName("ep")
        private String ep;

        @SerializedName("s")
        private String s;

        @SerializedName("geo")
        private List<String> geo;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getCn() {
            return cn;
        }

        public void setCn(String cn) {
            this.cn = cn;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getImage() {
            return image;
        }

        public void setImage(String image) {
            this.image = image;
        }

        public String getEp() {
            return ep;
        }

        public void setEp(String ep) {
            this.ep = ep;
        }

        public String getS() {
            return s;
        }

        public void setS(String s) {
            this.s = s;
        }

        public List<String> getGeo() {
            return geo;
        }

        public void setGeo(List<String> geo) {
            this.geo = geo;
        }
    }

    public static class PointDetail {
        @SerializedName("id")
        private String id;

        @SerializedName("name")
        private String name;

        @SerializedName("image")
        private String image;

        @SerializedName("ep")
        private String ep;

        @SerializedName("s")
        private String s;

        @SerializedName("geo")
        private List<String> geo;

        @SerializedName("origin")
        private String origin;

        @SerializedName("originURL")
        private String originURL;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getImage() {
            return image;
        }

        public void setImage(String image) {
            this.image = image;
        }

        public String getEp() {
            return ep;
        }

        public void setEp(String ep) {
            this.ep = ep;
        }

        public String getS() {
            return s;
        }

        public void setS(String s) {
            this.s = s;
        }

        public List<String> getGeo() {
            return geo;
        }

        public void setGeo(List<String> geo) {
            this.geo = geo;
        }

        public String getOrigin() {
            return origin;
        }

        public void setOrigin(String origin) {
            this.origin = origin;
        }

        public String getOriginURL() {
            return originURL;
        }

        public void setOriginURL(String originURL) {
            this.originURL = originURL;
        }
    }

    public static class BangumiSearchResponse {
        @SerializedName("list")
        private List<BangumiSearchItem> list;

        public List<BangumiSearchItem> getList() {
            return list;
        }

        public void setList(List<BangumiSearchItem> list) {
            this.list = list;
        }
    }

    public static class BangumiSearchItem {
        @SerializedName("id")
        private String id;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }
}
