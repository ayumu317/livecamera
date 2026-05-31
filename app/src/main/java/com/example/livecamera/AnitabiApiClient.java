package com.example.livecamera;

import android.util.Log;

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
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class AnitabiApiClient {

    private static final String TAG = "AnitabiApiClient";
    private static final String ANITABI_BASE_URL = "https://api.anitabi.cn/";
    private static final String BANGUMI_SEARCH_BASE_URL = "https://api.bgm.tv/search/subject/";
    private static final String BANGUMI_V0_SEARCH_BASE_URL = "https://api.bgm.tv/v0/search/subjects";
    private static final String BANGUMI_SUBJECT_BASE_URL = "https://api.bgm.tv/v0/subjects/";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
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

    public void getBangumiSubjectInfo(int subjectId, ApiCallback<BangumiSubjectInfo> callback) {
        if (callback == null) {
            return;
        }
        if (subjectId <= 0) {
            callback.onFailure(new IllegalArgumentException("subjectId 蹇呴』澶т簬 0"));
            return;
        }

        HttpUrl baseUrl = HttpUrl.parse(BANGUMI_SUBJECT_BASE_URL);
        if (baseUrl == null) {
            callback.onFailure(new IOException("Bangumi subject 鍦板潃閰嶇疆閿欒"));
            return;
        }

        HttpUrl url = baseUrl.newBuilder()
                .addPathSegment(String.valueOf(subjectId))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "LiveCamera-LBS/1.0 (Android)")
                .get()
                .build();

        executeRequest(request, BangumiSubjectInfo.class, new ResponseParser<BangumiSubjectInfo>() {
            @Override
            public BangumiSubjectInfo parse(String responseBody) throws Exception {
                BangumiSubjectInfo response = gson.fromJson(responseBody, BangumiSubjectInfo.class);
                if (response == null) {
                    throw new IOException("Bangumi subject 杩斿洖涓虹┖");
                }
                return response;
            }
        }, callback);
    }

    public void searchSubjectIdByName(String keyword, ApiCallback<Integer> callback) {
        searchSubjectIdByName(keyword, true, callback);
    }

    public void searchBangumiSubjectIdByName(String keyword, ApiCallback<Integer> callback) {
        searchSubjectIdByName(keyword, false, new ApiCallback<Integer>() {
            @Override
            public void onSuccess(Integer data) {
                callback.onSuccess(data);
            }

            @Override
            public void onFailure(Exception e) {
                Log.d(TAG, "Bangumi legacy search unavailable: keyword=" + normalizeKeyword(keyword)
                        + ", reason=" + safeMessage(e, "unknown")
                        + "; trying v0");
                searchBangumiV0SubjectIdByName(keyword, callback);
            }
        });
    }

    private void searchSubjectIdByName(String keyword, boolean requireAnitabiAvailable, ApiCallback<Integer> callback) {
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
                Integer subjectId = requireAnitabiAvailable
                        ? findFirstAnitabiAvailableSubjectId(response.getList())
                        : findBestBangumiSubjectId(response.getList(), normalizedKeyword);
                if (subjectId == null || subjectId <= 0) {
                    throw new IOException(requireAnitabiAvailable
                            ? "Bangumi 已找到作品，但 Anitabi 暂未收录对应巡礼条目"
                            : "未找到可用 Bangumi subjectId");
                }
                return subjectId;
            }
        }, callback);
    }

    private void searchBangumiV0SubjectIdByName(String keyword, ApiCallback<Integer> callback) {
        if (callback == null) {
            return;
        }
        String normalizedKeyword = normalizeKeyword(keyword);
        if (isBlank(normalizedKeyword)) {
            callback.onFailure(new IllegalArgumentException("搜索关键词不能为空"));
            return;
        }
        HttpUrl baseUrl = HttpUrl.parse(BANGUMI_V0_SEARCH_BASE_URL);
        if (baseUrl == null) {
            callback.onFailure(new IOException("Bangumi v0 搜索地址配置错误"));
            return;
        }
        HttpUrl url = baseUrl.newBuilder()
                .addQueryParameter("limit", "20")
                .addQueryParameter("offset", "0")
                .build();

        JsonObject filterObject = new JsonObject();
        com.google.gson.JsonArray typeArray = new com.google.gson.JsonArray();
        typeArray.add(2);
        filterObject.add("type", typeArray);

        JsonObject requestObject = new JsonObject();
        requestObject.addProperty("keyword", normalizedKeyword);
        requestObject.add("filter", filterObject);

        RequestBody body = RequestBody.create(requestObject.toString(), JSON_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "LiveCamera-LBS/1.0 (Android)")
                .post(body)
                .build();

        executeRequest(request, BangumiV0SearchResponse.class, new ResponseParser<Integer>() {
            @Override
            public Integer parse(String responseBody) throws Exception {
                BangumiV0SearchResponse response = gson.fromJson(responseBody, BangumiV0SearchResponse.class);
                if (response == null || response.getData() == null || response.getData().isEmpty()) {
                    throw new IOException("Bangumi v0 未找到对应 subjectId");
                }
                Integer subjectId = findBestBangumiSubjectId(response.getData(), normalizedKeyword);
                if (subjectId == null || subjectId <= 0) {
                    throw new IOException("Bangumi v0 未找到可用 subjectId");
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

    private Integer findBestBangumiSubjectId(List<BangumiSearchItem> items, String keyword) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        int maxCandidates = Math.min(items.size(), 20);
        int bestSubjectId = 0;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < maxCandidates; i++) {
            BangumiSearchItem item = items.get(i);
            int subjectId = item != null ? parseIntSafely(item.getId()) : 0;
            if (subjectId <= 0) {
                continue;
            }
            int score = scoreBangumiSearchItem(item, keyword) - i;
            Log.d(TAG, "Bangumi search candidate: keyword=" + keyword
                    + ", subjectId=" + subjectId
                    + ", score=" + score
                    + ", name=" + item.getName()
                    + ", name_cn=" + item.getNameCn()
                    + ", date=" + item.getDate()
                    + ", platform=" + item.getPlatform()
                    + ", eps=" + item.getEps());
            if (score > bestScore) {
                bestScore = score;
                bestSubjectId = subjectId;
            }
        }
        Log.d(TAG, "Bangumi search selected: keyword=" + keyword
                + ", subjectId=" + bestSubjectId
                + ", score=" + bestScore);
        return bestSubjectId > 0 ? bestSubjectId : null;
    }

    private int scoreBangumiSearchItem(BangumiSearchItem item, String keyword) {
        if (item == null) {
            return 0;
        }
        String normalizedKeyword = normalizeTitleForMatch(keyword);
        String normalizedName = normalizeTitleForMatch(item.getName());
        String normalizedNameCn = normalizeTitleForMatch(item.getNameCn());
        int score = 0;
        score = Math.max(score, scoreTitleMatch(normalizedKeyword, normalizedName));
        score = Math.max(score, scoreTitleMatch(normalizedKeyword, normalizedNameCn));
        String normalizedCandidate = normalizedName + normalizedNameCn;
        boolean keywordIsNijigasaki = isNijigasakiTitle(normalizedKeyword);
        boolean candidateIsNijigasaki = isNijigasakiTitle(normalizedCandidate);
        if (keywordIsNijigasaki && candidateIsNijigasaki) {
            score += 35;
        } else if (keywordIsNijigasaki) {
            score -= 80;
        }
        boolean keywordHasSequelMarker = hasSequelOrMovieMarker(normalizedKeyword);
        boolean candidateHasSequelMarker = hasSequelOrMovieMarker(normalizedCandidate);
        if (!keywordHasSequelMarker && candidateHasSequelMarker) {
            score -= 35;
        }
        if (!keywordHasSequelMarker && isTvSeriesItem(item)) {
            score += 20;
        }
        if (!keywordHasSequelMarker && item.getEps() != null && item.getEps() >= 10) {
            score += 10;
        }
        if (!keywordHasSequelMarker && item.getEps() != null && item.getEps() <= 1
                && !isBlank(item.getPlatform()) && !"TV".equalsIgnoreCase(item.getPlatform())) {
            score -= 15;
        }
        if (!isBlank(item.getNameCn())) {
            score += 5;
        }
        return score;
    }

    private int scoreTitleMatch(String keyword, String candidate) {
        if (isBlank(keyword) || isBlank(candidate)) {
            return 0;
        }
        if (candidate.equals(keyword)) {
            return 100;
        }
        if (candidate.startsWith(keyword)) {
            return 82;
        }
        if (keyword.startsWith(candidate)) {
            return 70;
        }
        if (candidate.contains(keyword)) {
            return 60;
        }
        if (keyword.contains(candidate)) {
            return 45;
        }
        int score = 0;
        if ((keyword.contains("虹咲") || keyword.contains("虹ヶ咲")) && (candidate.contains("虹咲") || candidate.contains("虹ヶ咲"))) {
            score += 30;
        }
        if (keyword.contains("lovelive") && candidate.contains("lovelive")) {
            score += 20;
        }
        if (keyword.contains("love") && candidate.contains("love")) {
            score += 10;
        }
        return score;
    }

    private String normalizeTitleForMatch(String value) {
        if (value == null) {
            return "";
        }
        return value
                .toLowerCase()
                .replace("學園", "学园")
                .replace("校园", "学园")
                .replace("同好會", "同好会")
                .replace("！", "!")
                .replace("　", "")
                .replaceAll("[\\s\\p{Punct}《》「」『』【】（）()\\[\\]·・]+", "")
                .trim();
    }

    private boolean isNijigasakiTitle(String normalizedTitle) {
        if (isBlank(normalizedTitle)) {
            return false;
        }
        return normalizedTitle.contains("虹咲")
                || normalizedTitle.contains("虹ヶ咲")
                || normalizedTitle.contains("nijigasaki");
    }

    private boolean hasSequelOrMovieMarker(String normalizedTitle) {
        if (isBlank(normalizedTitle)) {
            return false;
        }
        return normalizedTitle.contains("2期")
                || normalizedTitle.contains("第二季")
                || normalizedTitle.contains("season2")
                || normalizedTitle.contains("nextsky")
                || normalizedTitle.contains("完结篇")
                || normalizedTitle.contains("完結編")
                || normalizedTitle.contains("劇場版")
                || normalizedTitle.contains("剧场版")
                || normalizedTitle.contains("电影")
                || normalizedTitle.contains("movie");
    }

    private boolean isTvSeriesItem(BangumiSearchItem item) {
        return item != null && !isBlank(item.getPlatform()) && "TV".equalsIgnoreCase(item.getPlatform());
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

        private String subjectName;
        private String subjectNameCn;
        private String subjectSummary;
        private String subjectDate;
        private Integer subjectEps;
        private String subjectPlatform;

        public void applySubjectInfo(BangumiSubjectInfo subjectInfo) {
            if (subjectInfo == null) {
                return;
            }
            this.subjectName = subjectInfo.getName();
            this.subjectNameCn = subjectInfo.getNameCn();
            this.subjectSummary = subjectInfo.getSummary();
            this.subjectDate = subjectInfo.getDate();
            this.subjectEps = subjectInfo.getEps();
            this.subjectPlatform = subjectInfo.getPlatform();
            if (isBlankValue(this.cover)) {
                this.cover = subjectInfo.getBestImageUrl();
            }
        }

        public String getSubjectName() {
            return subjectName;
        }

        public String getSubjectNameCn() {
            return subjectNameCn;
        }

        public String getSubjectSummary() {
            return subjectSummary;
        }

        public String getSubjectDate() {
            return subjectDate;
        }

        public Integer getSubjectEps() {
            return subjectEps;
        }

        public String getSubjectPlatform() {
            return subjectPlatform;
        }

        private static boolean isBlankValue(String value) {
            return value == null || value.trim().isEmpty();
        }
    }

    public static class BangumiSubjectInfo {
        @SerializedName("name")
        private String name;

        @SerializedName("name_cn")
        private String nameCn;

        @SerializedName("summary")
        private String summary;

        @SerializedName("date")
        private String date;

        @SerializedName("eps")
        private Integer eps;

        @SerializedName("platform")
        private String platform;

        @SerializedName("images")
        private BangumiImageInfo images;

        public String getName() {
            return name;
        }

        public String getNameCn() {
            return nameCn;
        }

        public String getSummary() {
            return summary;
        }

        public String getDate() {
            return date;
        }

        public Integer getEps() {
            return eps;
        }

        public String getPlatform() {
            return platform;
        }

        public String getBestImageUrl() {
            if (images == null) {
                return "";
            }
            return images.getBestImageUrl();
        }
    }

    public static class BangumiImageInfo {
        @SerializedName("large")
        private String large;

        @SerializedName("common")
        private String common;

        @SerializedName("medium")
        private String medium;

        @SerializedName("grid")
        private String grid;

        @SerializedName("small")
        private String small;

        public String getBestImageUrl() {
            return chooseFirstNonBlank(large, common, medium, grid, small);
        }

        private static String chooseFirstNonBlank(String... values) {
            if (values == null) {
                return "";
            }
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            }
            return "";
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

    public static class BangumiV0SearchResponse {
        @SerializedName("data")
        private List<BangumiSearchItem> data;

        public List<BangumiSearchItem> getData() {
            return data;
        }
    }

    public static class BangumiSearchItem {
        @SerializedName("id")
        private String id;

        @SerializedName("name")
        private String name;

        @SerializedName("name_cn")
        private String nameCn;

        @SerializedName("date")
        private String date;

        @SerializedName("platform")
        private String platform;

        @SerializedName("eps")
        private Integer eps;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public String getNameCn() {
            return nameCn;
        }

        public String getDate() {
            return date;
        }

        public String getPlatform() {
            return platform;
        }

        public Integer getEps() {
            return eps;
        }
    }
}
