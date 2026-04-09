package com.example.livecamera;

import android.Manifest;
import android.content.Intent;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.exifinterface.media.ExifInterface;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.imageview.ShapeableImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String DEBUG_TAG = "TOUR_DEBUG";
    private static final String STATE_SELECTED_IMAGE_URI = "state_selected_image_uri";
    private static final String STATE_PENDING_CAMERA_URI = "state_pending_camera_uri";
    private static final int MAX_IMAGE_EDGE = 1024;
    private static final int JPEG_QUALITY = 80;
    private static final String DEFAULT_RESULT_HINT = "请选择一张实景照片，然后点击“开始识别”。";
    private static final String DEFAULT_DESC_HINT = "等待识别结果";

    private ShapeableImageView ivScenePreview;
    private ShapeableImageView ivResultReference;
    private TextView tvPreviewPlaceholderHint;
    private LinearLayout layoutActionButtons;
    private MaterialButton btnOpenCamera;
    private MaterialButton btnOpenGallery;
    private MaterialButton btnStartMatch;
    private MaterialButton btnDiary;
    private MaterialButton btnSaveRecord;
    private MaterialButton btnNextOption;
    private MaterialButton btnNavigateSpot;
    private MaterialCardView cardResult;
    private LinearLayout layoutOverseasContent;
    private LinearLayout layoutDomesticContent;
    private TextView tvResultSummary;
    private TextView tvAnimeTitle;
    private TextView tvLocationName;
    private TextView tvReferenceLabel;
    private TextView tvCommentaryLabel;
    private TextView tvDomesticAddress;
    private TextView tvDomesticIntro;
    private Chip chipResultState;
    private Chip chipConfidence;
    private ProgressBar pbLoading;
    private TextView tvDesc;
    private View previewScrimView;
    private View scanlineView;

    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private DoubaoVisionClient doubaoVisionClient;
    private AnitabiApiClient anitabiApiClient;
    private SerpApiClient serpApiClient;
    private TencentLocationHelper tencentLocationHelper;
    private LocationSearchClient locationSearchClient;

    private Uri selectedImageUri;
    private Uri pendingCameraImageUri;
    private File pendingCameraFile;
    private ObjectAnimator scanlineAnimator;
    private boolean hasLoadedPreviewImage;
    private ParsedResult lastParsedResult;
    private String currentAnimeName;
    private String currentLocation;
    private String currentDesc;
    private String currentLocalUri;
    private String currentReferenceUrl;
    private boolean hasSavedCurrentRecord;
    private List<String> currentCandidateNames;
    private int currentCandidateIndex = 0;
    private String currentCandidateLocation;
    private String currentCandidateDesc;
    private Set<Integer> currentTriedSubjectIds;
    private int activeSearchGeneration = 0;
    private int pendingLocationPermissionSearchGeneration = -1;
    private DeviceLocationSnapshot currentDeviceLocation;
    private LocationNavigationTarget currentNavigationTarget;
    private ResultMode currentResultMode = ResultMode.NONE;

    private enum ResultMode {
        NONE,
        OVERSEAS,
        DOMESTIC
    }

    private static final class ParsedResult {
        final List<String> animeNames;
        final String animeTitle;
        final String locationName;
        final String summary;
        final boolean isDomestic;

        ParsedResult(
                List<String> animeNames,
                String animeTitle,
                String locationName,
                String summary,
                boolean isDomestic
        ) {
            this.animeNames = animeNames != null ? new ArrayList<>(animeNames) : new ArrayList<>();
            this.animeTitle = animeTitle;
            this.locationName = locationName;
            this.summary = summary;
            this.isDomestic = isDomestic;
        }
    }

    private static final class DeviceLocationSnapshot {
        final Double latitude;
        final Double longitude;
        final String address;

        DeviceLocationSnapshot(@Nullable Double latitude, @Nullable Double longitude, @Nullable String address) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.address = address;
        }

        boolean hasCoordinates() {
            return latitude != null && longitude != null;
        }
    }

    private static final class LocationNavigationTarget {
        final String displayName;
        final String address;
        final Double latitude;
        final Double longitude;

        LocationNavigationTarget(
                @Nullable String displayName,
                @Nullable String address,
                @Nullable Double latitude,
                @Nullable Double longitude
        ) {
            this.displayName = displayName;
            this.address = address;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        boolean hasCoordinates() {
            return latitude != null && longitude != null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        applyWindowInsets();
        bindViews();
        initActivityResultLaunchers();
        doubaoVisionClient = new DoubaoVisionClient();
        anitabiApiClient = new AnitabiApiClient();
        serpApiClient = new SerpApiClient();
        tencentLocationHelper = new TencentLocationHelper(this);
        locationSearchClient = new LocationSearchClient();
        initViewState();
        initListeners();
        restoreState(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (selectedImageUri != null) {
            outState.putString(STATE_SELECTED_IMAGE_URI, selectedImageUri.toString());
        }
        if (pendingCameraImageUri != null) {
            outState.putString(STATE_PENDING_CAMERA_URI, pendingCameraImageUri.toString());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopScanlineIndicator();
        backgroundExecutor.shutdownNow();
        if (doubaoVisionClient != null) {
            doubaoVisionClient.cancelAll();
        }
        if (tencentLocationHelper != null) {
            tencentLocationHelper.stop();
        }
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void bindViews() {
        ivScenePreview = findViewById(R.id.iv_preview);
        ivResultReference = findViewById(R.id.iv_result_reference);
        tvPreviewPlaceholderHint = findViewById(R.id.tvPreviewPlaceholderHint);
        layoutActionButtons = findViewById(R.id.layoutActionButtons);
        btnOpenCamera = findViewById(R.id.btn_camera);
        btnOpenGallery = findViewById(R.id.btn_gallery);
        btnStartMatch = findViewById(R.id.btn_identify);
        btnDiary = findOptionalViewByName("btn_diary");
        btnSaveRecord = findOptionalViewByName("btnSaveRecord");
        btnNextOption = findOptionalViewByName("btnNextOption");
        btnNavigateSpot = findOptionalViewByName("btnNavigateSpot");
        cardResult = findViewById(R.id.cardResult);
        layoutOverseasContent = findOptionalViewByName("layoutOverseasContent");
        layoutDomesticContent = findOptionalViewByName("layoutDomesticContent");
        tvResultSummary = findViewById(R.id.tv_result);
        tvAnimeTitle = findViewById(R.id.tvAnimeTitle);
        tvLocationName = findViewById(R.id.tvLocationName);
        tvReferenceLabel = findViewById(R.id.tvReferenceLabel);
        tvCommentaryLabel = findOptionalViewByName("tvCommentaryLabel");
        tvDomesticAddress = findOptionalViewByName("tvDomesticAddress");
        tvDomesticIntro = findOptionalViewByName("tvDomesticIntro");
        chipResultState = findViewById(R.id.chipResultState);
        chipConfidence = findViewById(R.id.chipConfidence);
        pbLoading = findOptionalViewByName("pb_loading");
        tvDesc = findOptionalViewByName("tv_desc");
        scanlineView = findOptionalViewByName("v_scanline");
        previewScrimView = resolvePreviewScrimView();
    }

    private void initViewState() {
        clearResultDisplay();
        updateLoadingState(false);
        updatePreviewUi(false);
        lastParsedResult = null;
        clearCurrentResultSnapshot();
        clearCurrentCandidateState();
        btnStartMatch.setEnabled(selectedImageUri != null);
        if (pbLoading != null) {
            pbLoading.setVisibility(View.GONE);
        }
        if (scanlineView != null) {
            scanlineView.setVisibility(View.GONE);
        }
        tvResultSummary.setText(DEFAULT_RESULT_HINT);
        if (tvDesc != null) {
            tvDesc.setText(DEFAULT_DESC_HINT);
        }
    }

    private void restoreState(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            return;
        }
        String selectedUri = savedInstanceState.getString(STATE_SELECTED_IMAGE_URI);
        if (selectedUri != null && !selectedUri.isEmpty()) {
            selectedImageUri = Uri.parse(selectedUri);
            logUriInfo("Restoring selected image", selectedImageUri);
            renderSelectedImage(selectedImageUri);
        }
        String pendingUri = savedInstanceState.getString(STATE_PENDING_CAMERA_URI);
        if (pendingUri != null && !pendingUri.isEmpty()) {
            pendingCameraImageUri = Uri.parse(pendingUri);
            pendingCameraFile = new File(getCacheDir(), "images/" + extractFileNameFromUri(pendingCameraImageUri));
        }
    }

    private void initListeners() {
        btnOpenGallery.setOnClickListener(view -> galleryLauncher.launch("image/*"));
        btnOpenCamera.setOnClickListener(view -> openCamera());
        btnStartMatch.setOnClickListener(view -> startIdentifyFlow());
        if (btnDiary != null) {
            btnDiary.setOnClickListener(view -> openPilgrimDiary());
        }
        if (btnSaveRecord != null) {
            btnSaveRecord.setOnClickListener(view -> saveCurrentRecord());
        }
        if (btnNavigateSpot != null) {
            btnNavigateSpot.setOnClickListener(view -> openMap(getPreferredLocationDisplayName(
                    lastParsedResult != null ? lastParsedResult.locationName : currentLocation
            )));
        }
        if (btnNextOption != null) {
            btnNextOption.setOnClickListener(v -> {
                if (currentCandidateNames != null && currentCandidateIndex + 1 < currentCandidateNames.size()) {
                    currentCandidateIndex++;
                    showProcessingPlaceholder();
                    updateLoadingState(true);
                    int searchGeneration = beginNewSearchGeneration();
                    trySearchNextCandidate(searchGeneration);
                } else {
                    Toast.makeText(
                            this,
                            "大模型没有更多备选结果了，请尝试换一张角度更清晰的照片",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });
        }
        ivScenePreview.setOnClickListener(view -> {
            if (!hasLoadedPreviewImage) {
                return;
            }
            resetUI();
            showToast("请重新选择图片");
        });
    }

    private void initActivityResultLaunchers() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::handleGalleryResult
        );

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                this::handleCameraResult
        );

        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                this::handleLocationPermissionResult
        );
    }

    private void handleGalleryResult(Uri uri) {
        if (uri == null) {
            Log.i(TAG, "Gallery selection cancelled");
            showToast("已取消选择图片");
            updateLoadingState(false);
            return;
        }
        pendingCameraImageUri = null;
        pendingCameraFile = null;
        selectedImageUri = uri;
        logUriInfo("Gallery image selected", uri);
        resetForNewImageSelection();
        renderSelectedImage(uri);
    }

    private void handleCameraResult(Boolean success) {
        Uri cameraUri = pendingCameraImageUri;
        if (Boolean.TRUE.equals(success) && cameraUri != null) {
            selectedImageUri = cameraUri;
            logUriInfo("Camera image captured", cameraUri);
            pendingCameraImageUri = null;
            pendingCameraFile = null;
            resetForNewImageSelection();
            renderSelectedImage(cameraUri);
            return;
        }
        Log.i(TAG, "Camera capture cancelled or failed");
        deletePendingCameraFile();
        showToast("拍照已取消或失败");
        updateLoadingState(false);
    }

    private void openCamera() {
        try {
            pendingCameraImageUri = createTempPhotoUri();
            cameraLauncher.launch(pendingCameraImageUri);
        } catch (Exception e) {
            Log.e(TAG, "Unable to launch camera", e);
            deletePendingCameraFile();
            showToast("当前相机功能暂不可用，请先使用相册选图");
        }
    }

    private void openPilgrimDiary() {
        startActivity(new Intent(this, PilgrimDiaryActivity.class));
    }

    private Uri createTempPhotoUri() throws IOException {
        File imageDir = new File(getCacheDir(), "images");
        if (!imageDir.exists() && !imageDir.mkdirs()) {
            throw new IOException("创建缓存目录失败");
        }
        pendingCameraFile = File.createTempFile("camera_", ".jpg", imageDir);
        return FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                pendingCameraFile
        );
    }

    private void deletePendingCameraFile() {
        if (pendingCameraFile == null) {
            pendingCameraImageUri = null;
            return;
        }
        try {
            if (pendingCameraFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                pendingCameraFile.delete();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to delete temp camera file", e);
        } finally {
            pendingCameraFile = null;
            pendingCameraImageUri = null;
        }
    }

    private void resetForNewImageSelection() {
        beginNewSearchGeneration();
        clearLocationRoutingState();
        clearResultDisplay();
        updateLoadingState(false);
        updatePreviewUi(false);
        clearCurrentCandidateState();
        btnStartMatch.setEnabled(true);
        tvResultSummary.setText(DEFAULT_RESULT_HINT);
        if (tvDesc != null) {
            tvDesc.setText(DEFAULT_DESC_HINT);
        }
    }

    private void renderSelectedImage(Uri imageUri) {
        if (imageUri == null) {
            Log.w(TAG, "renderSelectedImage called with null uri");
            updatePreviewUi(false);
            btnStartMatch.setEnabled(false);
            return;
        }

        Log.d(TAG, "renderSelectedImage called, uri=" + imageUri);
        Glide.with(this).clear(ivScenePreview);
        ivScenePreview.setImageDrawable(null);
        updatePreviewUi(false);

        if (!canOpenImageUri(imageUri)) {
            Log.e(TAG, "Selected image uri is not readable: " + imageUri);
            selectedImageUri = null;
            btnStartMatch.setEnabled(false);
            showToast("图片读取失败，请重新选择图片");
            return;
        }

        Glide.with(this)
                .load(imageUri)
                .centerCrop()
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(
                            @Nullable GlideException e,
                            Object model,
                            Target<Drawable> target,
                            boolean isFirstResource
                    ) {
                        Log.e(TAG, "Failed to render selected image preview, model=" + model, e);
                        runSafelyOnUiThread(() -> {
                            updatePreviewUi(false);
                            btnStartMatch.setEnabled(false);
                            showToast("图片预览加载失败，请重新选择");
                        });
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(
                            Drawable resource,
                            Object model,
                            Target<Drawable> target,
                            com.bumptech.glide.load.DataSource dataSource,
                            boolean isFirstResource
                    ) {
                        Log.d(TAG, "Selected image preview rendered successfully, model=" + model
                                + ", dataSource=" + dataSource);
                        runSafelyOnUiThread(() -> {
                            updatePreviewUi(true);
                            btnStartMatch.setEnabled(true);
                        });
                        return false;
                    }
                })
                .into(ivScenePreview);
    }

    private void startIdentifyFlow() {
        if (selectedImageUri == null) {
            renderError("请先从相册选择或拍摄一张图片", null);
            return;
        }

        showProcessingPlaceholder();
        updateLoadingState(true);
        lastParsedResult = null;
        clearCurrentResultSnapshot();
        clearCurrentCandidateState();
        clearLocationRoutingState();
        int searchGeneration = beginNewSearchGeneration();
        Uri imageUri = selectedImageUri;
        backgroundExecutor.execute(() -> {
            final String base64Image;
            final double[] gpsLatLng = getGpsFromUri(imageUri);
            try {
                base64Image = compressImageToBase64(imageUri);
                Log.d(DEBUG_TAG, "图片转Base64成功，长度: " + base64Image.length());
            } catch (Exception e) {
                Log.e(TAG, "Failed to convert image to Base64", e);
                renderError("图片处理失败，请换一张图片后重试", e);
                return;
            }

            doubaoVisionClient.identifyLocation(base64Image, gpsLatLng, new DoubaoVisionClient.Callback() {
                @Override
                public void onSuccess(String responseBody) {
                    handleDoubaoSuccess(responseBody, searchGeneration);
                }

                @Override
                public void onFailure(Exception e) {
                    if (isStaleSearch(searchGeneration)) {
                        return;
                    }
                    String errorMessage = e != null && e.getMessage() != null
                            ? e.getMessage()
                            : "未知错误";
                    Log.e(DEBUG_TAG, "Doubao identify failed: " + errorMessage, e);
                    runSafelyOnUiThread(() -> Toast.makeText(
                            MainActivity.this,
                            "网络或API错误: " + errorMessage,
                            Toast.LENGTH_LONG
                    ).show());
                    renderError("网络或API错误: " + errorMessage, e, false);
                }
            });
        });
    }

    private void handleDoubaoSuccess(String responseBody, int searchGeneration) {
        final ParsedResult parsedResult;
        try {
            parsedResult = parseAssistantReply(responseBody);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Failed to parse Doubao response", e);
            renderError("未能从识别结果中提取有效的作品信息", e);
            return;
        }
        runSafelyOnUiThread(() -> {
            if (isStaleSearch(searchGeneration)) {
                return;
            }
            lastParsedResult = parsedResult;
            cardResult.setVisibility(View.VISIBLE);
            if (parsedResult.isDomestic) {
                switchResultMode(ResultMode.DOMESTIC);
                startSingleDeviceLocation(searchGeneration);
                tvAnimeTitle.setText(chooseFirstNonBlank(parsedResult.locationName, "国内景点待确认"));
                tvLocationName.setVisibility(View.GONE);
            } else {
                switchResultMode(ResultMode.OVERSEAS);
                tvAnimeTitle.setText(parsedResult.animeTitle);
                tvLocationName.setVisibility(View.VISIBLE);
                tvLocationName.setText(chooseFirstNonBlank(parsedResult.locationName, "现实地点待进一步确认"));
                bindLocationMapEntry(parsedResult.locationName);
            }
            requestLocationGateway(parsedResult, searchGeneration);
        });
    }

    private void handleLocationPermissionResult(Map<String, Boolean> permissionResult) {
        int searchGeneration = pendingLocationPermissionSearchGeneration;
        pendingLocationPermissionSearchGeneration = -1;
        if (searchGeneration < 0 || isStaleSearch(searchGeneration)) {
            return;
        }
        boolean granted = false;
        if (permissionResult != null) {
            for (Boolean value : permissionResult.values()) {
                if (Boolean.TRUE.equals(value)) {
                    granted = true;
                    break;
                }
            }
        }
        if (granted) {
            requestCurrentDeviceLocation(searchGeneration);
            return;
        }
        Log.w(DEBUG_TAG, "定位权限被拒绝，本次仅使用识别地点和后端定位结果");
    }

    private void startSingleDeviceLocation(int searchGeneration) {
        if (tencentLocationHelper == null || isStaleSearch(searchGeneration)) {
            return;
        }
        if (hasLocationPermission()) {
            requestCurrentDeviceLocation(searchGeneration);
            return;
        }
        pendingLocationPermissionSearchGeneration = searchGeneration;
        locationPermissionLauncher.launch(new String[] {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    private void requestCurrentDeviceLocation(int searchGeneration) {
        if (tencentLocationHelper == null || isStaleSearch(searchGeneration)) {
            return;
        }
        tencentLocationHelper.startSingleLocation(new TencentLocationHelper.LocationCallback() {
            @Override
            public void onSuccess(double latitude, double longitude, @Nullable String address) {
                if (isStaleSearch(searchGeneration)) {
                    return;
                }
                currentDeviceLocation = new DeviceLocationSnapshot(latitude, longitude, address);
                Log.d(DEBUG_TAG, "腾讯定位成功: lat=" + latitude + ", lng=" + longitude + ", address=" + address);
            }

            @Override
            public void onFailure(@NonNull String reason) {
                if (isStaleSearch(searchGeneration)) {
                    return;
                }
                Log.w(DEBUG_TAG, "腾讯定位失败: " + reason);
            }
        });
    }

    private void requestLocationGateway(ParsedResult parsedResult, int searchGeneration) {
        if (isStaleSearch(searchGeneration)) {
            return;
        }
        if (locationSearchClient == null || isBlank(parsedResult.locationName)) {
            handleGatewayFallback(parsedResult, searchGeneration, null);
            return;
        }

        locationSearchClient.search(parsedResult.locationName, parsedResult.isDomestic, new LocationSearchClient.Callback() {
            @Override
            public void onSuccess(@NonNull LocationSearchClient.LocationResult locationResult) {
                runSafelyOnUiThread(() -> {
                    if (isStaleSearch(searchGeneration)) {
                        return;
                    }
                    applyGatewayLocationResult(parsedResult, locationResult);
                    if (parsedResult.isDomestic) {
                        renderDomesticTravelResult(parsedResult, true);
                    } else {
                        continueCandidateFlow(parsedResult, searchGeneration);
                    }
                });
            }

            @Override
            public void onNotFound() {
                runSafelyOnUiThread(() -> {
                    if (isStaleSearch(searchGeneration)) {
                        return;
                    }
                    Log.w(DEBUG_TAG, "定位网关未命中: " + parsedResult.locationName);
                    handleGatewayFallback(parsedResult, searchGeneration, null);
                });
            }

            @Override
            public void onFailure(@NonNull Exception exception) {
                runSafelyOnUiThread(() -> {
                    if (isStaleSearch(searchGeneration)) {
                        return;
                    }
                    Log.e(DEBUG_TAG, "定位网关请求失败", exception);
                    handleGatewayFallback(parsedResult, searchGeneration, exception);
                });
            }
        });
    }

    private void handleGatewayFallback(
            ParsedResult parsedResult,
            int searchGeneration,
            @Nullable Exception exception
    ) {
        if (parsedResult.isDomestic) {
            if (exception != null) {
                Log.w(DEBUG_TAG, "国内景点未拿到后端精确坐标，保留 AI 景点介绍", exception);
            }
            renderDomesticTravelResult(parsedResult, false);
            return;
        }
        continueCandidateFlow(parsedResult, searchGeneration);
    }

    private void applyGatewayLocationResult(
            ParsedResult parsedResult,
            @NonNull LocationSearchClient.LocationResult locationResult
    ) {
        currentNavigationTarget = new LocationNavigationTarget(
                chooseFirstNonBlank(locationResult.getName(), parsedResult.locationName),
                chooseFirstNonBlank(locationResult.getAddress(), parsedResult.locationName),
                locationResult.getLatitude(),
                locationResult.getLongitude()
        );
        if (!locationResult.hasCoordinates()) {
            Log.w(DEBUG_TAG, "定位网关已命中，但缺少精确坐标: " + parsedResult.locationName);
        }
        String displayName = getPreferredLocationDisplayName(parsedResult.locationName);
        if (!parsedResult.isDomestic) {
            bindLocationMapEntry(displayName, displayName + " \uD83D\uDCCD(点击导航)");
        }
        if (!parsedResult.isDomestic && tvDesc != null) {
            String locationHint = chooseFirstNonBlank(
                    locationResult.getAddress(),
                    "已从双引擎定位网关拿到精确坐标，稍后可直接在地图中导航"
            );
            tvDesc.setVisibility(View.VISIBLE);
            tvDesc.setText("定位网关命中：" + locationHint);
        }
    }

    private void continueCandidateFlow(ParsedResult parsedResult, int searchGeneration) {
        switchResultMode(ResultMode.OVERSEAS);
        if (parsedResult.animeNames == null || parsedResult.animeNames.isEmpty()) {
            renderError("没有识别到明确的动漫作品名，请换一张更清晰的图片重试", null);
            return;
        }
        currentCandidateNames = new ArrayList<>(parsedResult.animeNames);
        currentCandidateIndex = 0;
        currentCandidateLocation = parsedResult.locationName;
        currentCandidateDesc = parsedResult.summary;
        currentTriedSubjectIds = new HashSet<>();
        updateNextOptionButtonState();
        trySearchNextCandidate(searchGeneration);
    }

    private void renderDomesticTravelResult(ParsedResult parsedResult, boolean fromGateway) {
        runSafelyOnUiThread(() -> {
            updateLoadingState(false);
            switchResultMode(ResultMode.DOMESTIC);
            cardResult.setVisibility(View.VISIBLE);
            chipResultState.setText("风景旅行");
            chipConfidence.setText(fromGateway ? "腾讯双引擎" : "AI 景点识别");
            String locationDisplayName = getPreferredLocationDisplayName(parsedResult.locationName);
            tvAnimeTitle.setText(locationDisplayName);
            tvLocationName.setVisibility(View.GONE);
            if (tvDomesticAddress != null) {
                tvDomesticAddress.setText(chooseFirstNonBlank(
                        currentNavigationTarget != null ? currentNavigationTarget.address : null,
                        parsedResult.locationName,
                        "地址待确认"
                ));
            }
            if (tvDomesticIntro != null) {
                tvDomesticIntro.setText(buildDomesticIntroduction(parsedResult, fromGateway));
            }
            tvReferenceLabel.setVisibility(View.GONE);
            ivResultReference.setVisibility(View.GONE);
            tvResultSummary.setText(parsedResult.summary != null ? parsedResult.summary : DEFAULT_RESULT_HINT);
            updateCurrentResultSnapshot(
                    locationDisplayName,
                    chooseFirstNonBlank(
                            currentNavigationTarget != null ? currentNavigationTarget.address : null,
                            locationDisplayName
                    ),
                    buildDomesticRecordDescription(parsedResult, locationDisplayName),
                    null
            );
            updateNextOptionButtonState();
            updateNavigateButtonState();
        });
    }

    private void trySearchNextCandidate(int searchGeneration) {
        if (isStaleSearch(searchGeneration)) {
            return;
        }
        if (currentCandidateNames == null
                || currentCandidateNames.isEmpty()
                || currentCandidateIndex < 0
                || currentCandidateIndex >= currentCandidateNames.size()) {
            requestFallbackImage(
                    lastParsedResult != null ? lastParsedResult.animeTitle : "未知作品",
                    currentCandidateLocation,
                    currentCandidateDesc,
                    searchGeneration,
                    false
            );
            return;
        }
        String currentName = currentCandidateNames.get(currentCandidateIndex);
        anitabiApiClient.searchSubjectIdByName(currentName, new AnitabiApiClient.ApiCallback<Integer>() {
            @Override
            public void onSuccess(Integer subjectId) {
                runSafelyOnUiThread(() -> {
                    if (isStaleSearch(searchGeneration)) {
                        return;
                    }
                    if (currentTriedSubjectIds == null) {
                        currentTriedSubjectIds = new HashSet<>();
                    }
                    if (currentTriedSubjectIds.contains(subjectId)) {
                        if (currentCandidateNames != null && currentCandidateIndex + 1 < currentCandidateNames.size()) {
                            currentCandidateIndex++;
                            updateNextOptionButtonState();
                            trySearchNextCandidate(searchGeneration);
                            return;
                        }
                        renderPartialSuccess(currentName, currentCandidateLocation, currentCandidateDesc);
                        return;
                    }
                    currentTriedSubjectIds.add(subjectId);
                    ParsedResult parsedResult = new ParsedResult(
                            currentCandidateNames,
                            currentName,
                            currentCandidateLocation,
                            currentCandidateDesc,
                            lastParsedResult != null && lastParsedResult.isDomestic
                    );
                    requestBangumiLite(parsedResult, subjectId, searchGeneration);
                });
            }

            @Override
            public void onFailure(Exception e) {
                runSafelyOnUiThread(() -> {
                    if (isStaleSearch(searchGeneration)) {
                        return;
                    }
                    if (currentCandidateNames != null && currentCandidateIndex + 1 < currentCandidateNames.size()) {
                        Log.w(
                                DEBUG_TAG,
                                currentName + "搜索失败，尝试下一个:" + currentCandidateNames.get(currentCandidateIndex + 1),
                                e
                        );
                        currentCandidateIndex++;
                        updateNextOptionButtonState();
                        trySearchNextCandidate(searchGeneration);
                        return;
                    }
                    requestFallbackImage(
                            lastParsedResult != null ? lastParsedResult.animeTitle : currentName,
                            currentCandidateLocation,
                            currentCandidateDesc,
                            searchGeneration,
                            false
                    );
                });
            }
        });
    }

    private void requestBangumiLite(ParsedResult parsedResult, int subjectId, int searchGeneration) {
        anitabiApiClient.getBangumiLite(subjectId, new AnitabiApiClient.ApiCallback<AnitabiApiClient.BangumiLiteResponse>() {
            @Override
            public void onSuccess(AnitabiApiClient.BangumiLiteResponse bangumiLiteResponse) {
                runSafelyOnUiThread(() -> {
                    if (isStaleSearch(searchGeneration)) {
                        return;
                    }
                    requestPointsDetail(parsedResult, bangumiLiteResponse, subjectId, searchGeneration);
                });
            }

            @Override
            public void onFailure(Exception e) {
                runSafelyOnUiThread(() -> {
                    if (isStaleSearch(searchGeneration)) {
                        return;
                    }
                    Log.e(TAG, "Failed to get bangumi lite", e);
                    renderPartialSuccess(parsedResult.animeTitle, parsedResult.locationName, parsedResult.summary);
                });
            }
        });
    }

    private void requestPointsDetail(
            ParsedResult parsedResult,
            AnitabiApiClient.BangumiLiteResponse bangumiLiteResponse,
            int subjectId,
            int searchGeneration
    ) {
        anitabiApiClient.getPointsDetail(subjectId, true, new AnitabiApiClient.ApiCallback<List<AnitabiApiClient.PointDetail>>() {
            @Override
            public void onSuccess(List<AnitabiApiClient.PointDetail> pointDetails) {
                runSafelyOnUiThread(() -> {
                    if (isStaleSearch(searchGeneration)) {
                        return;
                    }
                    updateLoadingState(false);
                    renderResult(parsedResult, bangumiLiteResponse, pointDetails);
                });
            }

            @Override
            public void onFailure(Exception e) {
                runSafelyOnUiThread(() -> {
                    if (isStaleSearch(searchGeneration)) {
                        return;
                    }
                    Log.e(TAG, "Failed to get points detail", e);
                    String fallbackAnimeName = getCurrentCandidateName(parsedResult.animeTitle);
                    String fallbackLocation = chooseFirstNonBlank(currentCandidateLocation, parsedResult.locationName);
                    String fallbackDescription = chooseFirstNonBlank(currentCandidateDesc, parsedResult.summary);
                    requestFallbackImage(
                            fallbackAnimeName,
                            fallbackLocation,
                            fallbackDescription,
                            searchGeneration,
                            false
                    );
                });
            }
        });
    }

    private void requestFallbackImage(
            String animeName,
            String locationName,
            String description,
            int searchGeneration,
            boolean allowAdvanceToNextCandidate
    ) {
        if (isStaleSearch(searchGeneration)) {
            return;
        }
        if (serpApiClient == null) {
            handleCandidateFallbackFailure(
                    animeName,
                    locationName,
                    description,
                    null,
                    searchGeneration,
                    allowAdvanceToNextCandidate
            );
            return;
        }
        serpApiClient.fetchFallbackImage(animeName, locationName, new SerpApiClient.Callback() {
            @Override
            public void onSuccess(String imageUrl) {
                runSafelyOnUiThread(() -> {
                    if (isStaleSearch(searchGeneration)) {
                        return;
                    }
                    updateLoadingState(false);
                    renderWebSearchFallback(animeName, locationName, description, imageUrl);
                });
            }

            @Override
            public void onFailure(Exception e) {
                runSafelyOnUiThread(() -> {
                    if (isStaleSearch(searchGeneration)) {
                        return;
                    }
                    Log.e(DEBUG_TAG, "全网搜图失败，降级为文字地图兜底", e);
                    if (allowAdvanceToNextCandidate && hasNextCandidate()) {
                        handleCandidateFallbackFailure(
                                animeName,
                                locationName,
                                description,
                                e,
                                searchGeneration,
                                true
                        );
                        return;
                    }
                    updateLoadingState(false);
                    renderPartialSuccess(animeName, locationName, description);
                });
            }
        });
    }

    private void handleCandidateFallbackFailure(
            String animeName,
            String locationName,
            String description,
            @Nullable Exception exception,
            int searchGeneration,
            boolean allowAdvanceToNextCandidate
    ) {
        runSafelyOnUiThread(() -> {
            if (isStaleSearch(searchGeneration)) {
                return;
            }
            if (allowAdvanceToNextCandidate
                    && currentCandidateNames != null
                    && currentCandidateIndex + 1 < currentCandidateNames.size()) {
                String currentName = currentCandidateNames.get(currentCandidateIndex);
                String nextName = currentCandidateNames.get(currentCandidateIndex + 1);
                Log.w(DEBUG_TAG, currentName + "下游兜底失败，尝试下一个:" + nextName, exception);
                currentCandidateIndex++;
                updateNextOptionButtonState();
                trySearchNextCandidate(searchGeneration);
                return;
            }
            renderPartialSuccess(animeName, locationName, description);
        });
    }

    private void renderWebSearchFallback(
            String animeName,
            String locationName,
            String description,
            String imageUrl
    ) {
        runSafelyOnUiThread(() -> {
            updateLoadingState(false);
            switchResultMode(ResultMode.OVERSEAS);
            cardResult.setVisibility(View.VISIBLE);
            chipResultState.setText("AI 识别成功");
            chipConfidence.setText("全网智能检索");
            tvAnimeTitle.setText(animeName != null ? animeName : "未知作品");
            String locationDisplayName = getPreferredLocationDisplayName(locationName);
            tvLocationName.setVisibility(View.VISIBLE);
            bindLocationMapEntry(locationDisplayName, locationDisplayName + " \uD83D\uDCCD(点击导航)");
            tvResultSummary.setText(description != null ? description : DEFAULT_RESULT_HINT);
            tvReferenceLabel.setVisibility(View.VISIBLE);
            if (ivResultReference != null) {
                ivResultReference.setVisibility(View.VISIBLE);
                Glide.with(MainActivity.this)
                        .load(imageUrl)
                        .centerCrop()
                        .into(ivResultReference);
            }
            if (tvDesc != null) {
                tvDesc.setVisibility(View.VISIBLE);
                tvDesc.setText(buildNavigationHint("专业巡礼图库暂无截图，当前展示全网智能检索到的参考图"));
            }
            updateNextOptionButtonState();
            updateNavigateButtonState();
            updateCurrentResultSnapshot(animeName, locationDisplayName, description, imageUrl);
        });
    }

    private void renderPartialSuccess(String animeName, String locationName, String description) {
        runSafelyOnUiThread(() -> {
            updateLoadingState(false);
            switchResultMode(ResultMode.OVERSEAS);
            cardResult.setVisibility(View.VISIBLE);
            chipResultState.setText("AI 识别成功");
            chipConfidence.setText("暂无原片截图");
            tvAnimeTitle.setText(animeName != null ? animeName : "未知作品");
            String safeLocationName = getPreferredLocationDisplayName(locationName);
            tvLocationName.setVisibility(View.VISIBLE);
            bindLocationMapEntry(safeLocationName, safeLocationName + " \uD83D\uDCCD(点击导航)");
            tvResultSummary.setText(description != null ? description : DEFAULT_RESULT_HINT);
            tvReferenceLabel.setVisibility(View.GONE);
            if (ivResultReference != null) {
                ivResultReference.setVisibility(View.GONE);
            }
            if (tvDesc != null) {
                tvDesc.setVisibility(View.VISIBLE);
                tvDesc.setText(buildNavigationHint("第三方巡礼数据库暂无截图，当前可直接打开地图前往现场"));
            }
            updateNextOptionButtonState();
            updateNavigateButtonState();
            updateCurrentResultSnapshot(animeName, safeLocationName, description, null);
        });
    }

    private void renderResult(
            ParsedResult parsedResult,
            AnitabiApiClient.BangumiLiteResponse bangumiLiteResponse,
            List<AnitabiApiClient.PointDetail> pointDetails
    ) {
        runSafelyOnUiThread(() -> {
            updateLoadingState(false);
            switchResultMode(ResultMode.OVERSEAS);
            cardResult.setVisibility(View.VISIBLE);

            AnitabiApiClient.PointDetail firstPoint = chooseBestPointDetail(
                    pointDetails,
                    chooseFirstNonBlank(parsedResult.locationName, currentCandidateLocation)
            );
            boolean hasMultiplePoints = pointDetails.size() > 1;
            String animeDisplayName = chooseFirstNonBlank(
                    bangumiLiteResponse.getCn(),
                    bangumiLiteResponse.getTitle(),
                    parsedResult.animeTitle,
                    "AI 识别结果"
            );
            String locationDisplayName = chooseFirstNonBlank(
                    getPreferredLocationDisplayName(null),
                    firstPoint.getName(),
                    parsedResult.locationName,
                    bangumiLiteResponse.getCity(),
                    "巡礼地点待进一步确认"
            );
            String descriptionText = buildResultText(parsedResult, bangumiLiteResponse, firstPoint, hasMultiplePoints);
            String locationDisplayText = locationDisplayName + " \uD83D\uDCCD(点击导航)";
            String pointImageUrl = AnitabiApiClient.getHighResImageUrl(firstPoint.getImage());

            chipResultState.setText("AI 识别成功");
            chipConfidence.setText("原片精准匹配");
            tvAnimeTitle.setText(animeDisplayName);
            tvLocationName.setVisibility(View.VISIBLE);
            bindLocationMapEntry(locationDisplayName, locationDisplayText);
            tvResultSummary.setText(descriptionText);
            if (tvDesc != null) {
                tvDesc.setVisibility(View.VISIBLE);
                tvDesc.setText(buildNavigationHint(
                        buildSupplementText(parsedResult, bangumiLiteResponse, firstPoint, hasMultiplePoints)
                ));
            }

            currentCandidateLocation = locationDisplayName;
            currentCandidateDesc = descriptionText;
            updateCurrentResultSnapshot(
                    animeDisplayName,
                    locationDisplayName,
                    descriptionText,
                    pointImageUrl
            );
            updateNextOptionButtonState();
            updateNavigateButtonState();

            if (!isBlank(pointImageUrl)) {
                tvReferenceLabel.setVisibility(View.VISIBLE);
                ivResultReference.setVisibility(View.VISIBLE);
                Glide.with(this)
                        .load(pointImageUrl)
                        .centerCrop()
                        .into(ivResultReference);
            } else {
                tvReferenceLabel.setVisibility(View.GONE);
                ivResultReference.setVisibility(View.GONE);
            }
        });
    }

    private void renderResultWithLiteFallback(
            ParsedResult parsedResult,
            AnitabiApiClient.BangumiLiteResponse bangumiLiteResponse
    ) {
        runSafelyOnUiThread(() -> {
            updateLoadingState(false);
            clearResultDisplay();
            switchResultMode(ResultMode.OVERSEAS);
            cardResult.setVisibility(View.VISIBLE);

            AnitabiApiClient.LitePoint firstLitePoint = chooseBestLitePoint(
                    bangumiLiteResponse.getLitePoints(),
                    chooseFirstNonBlank(parsedResult.locationName, currentCandidateLocation)
            );
            String animeDisplayName = chooseFirstNonBlank(
                    bangumiLiteResponse.getCn(),
                    bangumiLiteResponse.getTitle(),
                    parsedResult.animeTitle,
                    "AI 识别结果"
            );
            String locationDisplayName = chooseFirstNonBlank(
                    getPreferredLocationDisplayName(null),
                    firstLitePoint.getCn(),
                    firstLitePoint.getName(),
                    parsedResult.locationName,
                    bangumiLiteResponse.getCity(),
                    "巡礼地点待进一步确认"
            );

            chipResultState.setText("部分匹配");
            chipConfidence.setText("Bangumi #" + chooseFirstNonBlank(bangumiLiteResponse.getId(), "?"));
            tvAnimeTitle.setText(animeDisplayName);
            tvLocationName.setVisibility(View.VISIBLE);
            bindLocationMapEntry(locationDisplayName, locationDisplayName + " \uD83D\uDCCD(点击导航)");

            StringBuilder builder = new StringBuilder();
            builder.append("识别作品：").append(animeDisplayName);
            if (!isBlank(bangumiLiteResponse.getTitle())
                    && !bangumiLiteResponse.getTitle().equals(animeDisplayName)) {
                builder.append("\n原名：").append(bangumiLiteResponse.getTitle());
            }
            builder.append("\n匹配巡礼地标：").append(locationDisplayName);
            int liteEpisode = parseIntSafely(firstLitePoint.getEp());
            if (liteEpisode > 0) {
                builder.append("\n对应集数：第").append(liteEpisode).append("集");
            }
            builder.append("\n\nAnitabi 暂未返回详细巡礼点，当前先展示最接近识别地点的一条基础地标信息。");
            if (!isBlank(parsedResult.summary)) {
                builder.append("\n\n场景分析：").append(parsedResult.summary);
            }
            tvResultSummary.setText(builder.toString());
            if (tvDesc != null) {
                tvDesc.setVisibility(View.VISIBLE);
                tvDesc.setText(buildNavigationHint("已匹配到作品，但详细巡礼点尚未返回，当前展示第一条基础地标。"));
            }

            String liteImageUrl = AnitabiApiClient.getHighResImageUrl(firstLitePoint.getImage());
            if (!isBlank(liteImageUrl)) {
                tvReferenceLabel.setVisibility(View.VISIBLE);
                ivResultReference.setVisibility(View.VISIBLE);
                Glide.with(this)
                        .load(liteImageUrl)
                        .centerCrop()
                        .into(ivResultReference);
            } else {
                tvReferenceLabel.setVisibility(View.GONE);
                ivResultReference.setVisibility(View.GONE);
            }

            updateCurrentResultSnapshot(
                    animeDisplayName,
                    locationDisplayName,
                    builder.toString(),
                    liteImageUrl
            );
            updateNextOptionButtonState();
            updateNavigateButtonState();
        });
    }

    private void renderError(String message, Exception exception) {
        renderError(message, exception, true);
    }

    private void renderError(String message, Exception exception, boolean showToast) {
        if (exception != null) {
            Log.e(TAG, message, exception);
        }
        runSafelyOnUiThread(() -> {
            updateLoadingState(false);
            if (showToast) {
                showToast(message);
            }
            cardResult.setVisibility(View.VISIBLE);
            boolean hasModelFallback = lastParsedResult != null
                    && (!isBlank(lastParsedResult.animeTitle) || !isBlank(lastParsedResult.locationName));
            boolean isDomesticResult = hasModelFallback && lastParsedResult != null && lastParsedResult.isDomestic;
            switchResultMode(isDomesticResult ? ResultMode.DOMESTIC : ResultMode.OVERSEAS);
            chipResultState.setText(hasModelFallback ? "仅模型识别" : "识别失败");
            chipConfidence.setText(hasModelFallback
                    ? (isDomesticResult ? "景点可导航" : "地图可用")
                    : "请重试");
            tvAnimeTitle.setText(hasModelFallback
                    ? (isDomesticResult
                    ? getPreferredLocationDisplayName(lastParsedResult.locationName)
                    : lastParsedResult.animeTitle)
                    : "暂未完成匹配");
            String locationText = hasModelFallback
                    ? getPreferredLocationDisplayName(lastParsedResult.locationName)
                    : "请重新选择图片或稍后再试";
            tvLocationName.setVisibility(isDomesticResult ? View.GONE : View.VISIBLE);
            tvLocationName.setText(locationText);
            boolean canOpenResolvedMap = currentNavigationTarget != null
                    && (!isBlank(currentNavigationTarget.displayName) || currentNavigationTarget.hasCoordinates());
            if (!isDomesticResult && hasModelFallback && (!isBlank(lastParsedResult.locationName) || canOpenResolvedMap)) {
                bindLocationMapEntry(locationText, locationText + " \uD83D\uDCCD(点击导航)");
            } else {
                clearLocationMapEntry();
            }
            if (isDomesticResult) {
                if (tvDomesticAddress != null) {
                    tvDomesticAddress.setText(chooseFirstNonBlank(
                            currentNavigationTarget != null ? currentNavigationTarget.address : null,
                            lastParsedResult.locationName,
                            "地址待确认"
                    ));
                }
                if (tvDomesticIntro != null) {
                    tvDomesticIntro.setText(chooseFirstNonBlank(lastParsedResult.summary, message));
                }
            } else {
                tvResultSummary.setText(message);
                if (tvDesc != null) {
                    tvDesc.setVisibility(View.VISIBLE);
                    tvDesc.setText(hasModelFallback
                            ? buildNavigationHint("第三方巡礼数据库未命中，当前保留 AI 识别地点，可点击地点名称打开地图")
                            : DEFAULT_DESC_HINT);
                }
            }
            tvReferenceLabel.setVisibility(View.GONE);
            ivResultReference.setVisibility(View.GONE);
            updateNavigateButtonState();
        });
    }

    private void clearResultDisplay() {
        switchResultMode(ResultMode.NONE);
        cardResult.setVisibility(View.GONE);
        chipResultState.setText("等待识别");
        chipConfidence.setText("等待结果");
        tvAnimeTitle.setText("AI 巡礼匹配结果");
        tvLocationName.setVisibility(View.VISIBLE);
        tvLocationName.setText("等待识别");
        clearLocationMapEntry();
        tvResultSummary.setText(DEFAULT_RESULT_HINT);
        tvReferenceLabel.setVisibility(View.GONE);
        ivResultReference.setVisibility(View.GONE);
        if (tvDesc != null) {
            tvDesc.setText(DEFAULT_DESC_HINT);
            tvDesc.setVisibility(View.GONE);
        }
        if (tvDomesticAddress != null) {
            tvDomesticAddress.setText("");
        }
        if (tvDomesticIntro != null) {
            tvDomesticIntro.setText("");
        }
        updateSaveRecordButtonState();
        updateNavigateButtonState();
    }

    private void showProcessingPlaceholder() {
        switchResultMode(ResultMode.NONE);
        cardResult.setVisibility(View.VISIBLE);
        chipResultState.setText("识别中");
        chipConfidence.setText("请稍候");
        tvAnimeTitle.setText("正在分析图片");
        tvLocationName.setVisibility(View.VISIBLE);
        tvLocationName.setText("正在检索地点信息");
        clearLocationMapEntry();
        tvResultSummary.setText("正在识别图片并匹配地点信息，请稍候...");
        tvReferenceLabel.setVisibility(View.GONE);
        ivResultReference.setVisibility(View.GONE);
        if (tvDesc != null) {
            tvDesc.setVisibility(View.GONE);
            tvDesc.setText("正在识别图片并准备匹配地点信息，请稍候...");
        }
        if (tvDomesticAddress != null) {
            tvDomesticAddress.setText("");
        }
        if (tvDomesticIntro != null) {
            tvDomesticIntro.setText("");
        }
    }

    private void updateLoadingState(boolean loading) {
        btnStartMatch.setEnabled(!loading && selectedImageUri != null);
        btnStartMatch.setText(loading ? "识别中..." : getString(R.string.action_identify));
        if (pbLoading != null) {
            pbLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (loading) {
            startScanlineIndicator();
        } else {
            stopScanlineIndicator();
        }
    }

    private void startScanlineIndicator() {
        if (scanlineView == null) {
            return;
        }
        scanlineView.setVisibility(View.VISIBLE);
        scanlineView.post(() -> {
            if (scanlineView == null || ivScenePreview == null) {
                return;
            }
            float travelDistance = Math.max(0f, ivScenePreview.getHeight() - scanlineView.getHeight());
            if (travelDistance <= 0f) {
                scanlineView.setTranslationY(0f);
                return;
            }
            stopScanlineIndicator();
            scanlineView.setVisibility(View.VISIBLE);
            scanlineAnimator = ObjectAnimator.ofFloat(scanlineView, "translationY", 0f, travelDistance);
            scanlineAnimator.setInterpolator(new LinearInterpolator());
            scanlineAnimator.setDuration(1200L);
            scanlineAnimator.setRepeatCount(ValueAnimator.INFINITE);
            scanlineAnimator.setRepeatMode(ValueAnimator.REVERSE);
            scanlineAnimator.start();
        });
    }

    private void stopScanlineIndicator() {
        if (scanlineAnimator != null) {
            scanlineAnimator.cancel();
            scanlineAnimator = null;
        }
        if (scanlineView != null) {
            scanlineView.setTranslationY(0f);
            scanlineView.setVisibility(View.GONE);
        }
    }

    private void resetUI() {
        beginNewSearchGeneration();
        if (doubaoVisionClient != null) {
            doubaoVisionClient.cancelAll();
        }
        if (tencentLocationHelper != null) {
            tencentLocationHelper.stop();
        }
        lastParsedResult = null;
        clearCurrentResultSnapshot();
        clearCurrentCandidateState();
        clearLocationRoutingState();
        selectedImageUri = null;
        pendingCameraImageUri = null;
        pendingCameraFile = null;
        Glide.with(this).clear(ivScenePreview);
        ivScenePreview.setImageDrawable(null);
        updatePreviewUi(false);
        clearResultDisplay();
        updateLoadingState(false);
        btnStartMatch.setText(getString(R.string.action_identify));
        btnStartMatch.setEnabled(true);
    }

    private void updatePreviewUi(boolean hasLoadedImage) {
        hasLoadedPreviewImage = hasLoadedImage;
        tvPreviewPlaceholderHint.setVisibility(hasLoadedImage ? View.GONE : View.VISIBLE);
        layoutActionButtons.setVisibility(hasLoadedImage ? View.GONE : View.VISIBLE);
        btnOpenCamera.setVisibility(View.VISIBLE);
        btnOpenGallery.setVisibility(View.VISIBLE);
        if (previewScrimView != null) {
            previewScrimView.setVisibility(hasLoadedImage ? View.GONE : View.VISIBLE);
        }
        Log.d(TAG, "Preview UI state updated, hasLoadedImage=" + hasLoadedImage
                + ", actionButtonsVisibility=" + layoutActionButtons.getVisibility()
                + ", scrimVisibility=" + (previewScrimView != null ? previewScrimView.getVisibility() : -1));
    }

    private boolean canOpenImageUri(Uri imageUri) {
        try (InputStream inputStream = getContentResolver().openInputStream(imageUri)) {
            boolean readable = inputStream != null;
            Log.d(TAG, "Image uri readable=" + readable + ", uri=" + imageUri);
            return readable;
        } catch (Exception e) {
            Log.e(TAG, "Unable to open image uri for preview: " + imageUri, e);
            return false;
        }
    }

    private void logUriInfo(String event, Uri uri) {
        if (uri == null) {
            Log.w(TAG, event + ": uri=null");
            return;
        }
        Log.d(TAG, event + ": uri=" + uri
                + ", scheme=" + uri.getScheme()
                + ", authority=" + uri.getAuthority()
                + ", path=" + uri.getPath());
    }

    @Nullable
    private View resolvePreviewScrimView() {
        if (ivScenePreview == null || !(ivScenePreview.getParent() instanceof ViewGroup)) {
            return null;
        }
        ViewGroup previewContainer = (ViewGroup) ivScenePreview.getParent();
        for (int i = 0; i < previewContainer.getChildCount(); i++) {
            View child = previewContainer.getChildAt(i);
            if (child == ivScenePreview
                    || child == tvPreviewPlaceholderHint
                    || child == layoutActionButtons
                    || child == scanlineView) {
                continue;
            }
            if (child.getId() == View.NO_ID) {
                Log.d(TAG, "Preview scrim view resolved at child index=" + i);
                return child;
            }
        }
        Log.w(TAG, "Preview scrim view not found in preview container");
        return null;
    }

    @Nullable
    private double[] getGpsFromUri(Uri uri) {
        if (uri == null) {
            return null;
        }
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                return null;
            }
            ExifInterface exifInterface = new ExifInterface(inputStream);
            float[] latLng = new float[2];
            if (!exifInterface.getLatLong(latLng)) {
                return null;
            }
            double[] gpsLatLng = new double[] { latLng[0], latLng[1] };
            Log.d(DEBUG_TAG, "读取到照片GPS: lat=" + gpsLatLng[0] + ", lng=" + gpsLatLng[1]);
            return gpsLatLng;
        } catch (Exception e) {
            Log.w(DEBUG_TAG, "读取照片GPS失败", e);
            return null;
        }
    }

    private String compressImageToBase64(Uri imageUri) throws IOException {
        Bitmap bitmap = decodeScaledBitmap(imageUri, MAX_IMAGE_EDGE);
        Bitmap rotatedBitmap = applyExifRotation(imageUri, bitmap);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        boolean compressed = rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream);
        if (!compressed) {
            if (rotatedBitmap != bitmap) {
                rotatedBitmap.recycle();
            }
            bitmap.recycle();
            throw new IOException("图片压缩失败");
        }

        byte[] imageBytes = outputStream.toByteArray();
        if (rotatedBitmap != bitmap) {
            rotatedBitmap.recycle();
        }
        bitmap.recycle();
        return Base64.encodeToString(imageBytes, Base64.NO_WRAP);
    }

    private Bitmap decodeScaledBitmap(Uri imageUri, int maxEdge) throws IOException {
        BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
        boundsOptions.inJustDecodeBounds = true;
        decodeBitmapStream(imageUri, boundsOptions);

        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
            throw new IOException("无法读取图片尺寸");
        }

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
        decodeOptions.inSampleSize = calculateInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight, maxEdge);
        Bitmap decodedBitmap = decodeBitmapStream(imageUri, decodeOptions);
        if (decodedBitmap == null) {
            throw new IOException("无法解码图片");
        }

        int longestEdge = Math.max(decodedBitmap.getWidth(), decodedBitmap.getHeight());
        if (longestEdge <= maxEdge) {
            return decodedBitmap;
        }

        float scale = (float) maxEdge / (float) longestEdge;
        int scaledWidth = Math.max(1, Math.round(decodedBitmap.getWidth() * scale));
        int scaledHeight = Math.max(1, Math.round(decodedBitmap.getHeight() * scale));
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(decodedBitmap, scaledWidth, scaledHeight, true);
        if (scaledBitmap != decodedBitmap) {
            decodedBitmap.recycle();
        }
        return scaledBitmap;
    }

    private Bitmap decodeBitmapStream(Uri imageUri, BitmapFactory.Options options) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(imageUri);
        if (inputStream == null) {
            throw new IOException("无法打开图片流");
        }
        try (InputStream stream = inputStream) {
            return BitmapFactory.decodeStream(stream, null, options);
        }
    }

    private int calculateInSampleSize(int width, int height, int maxEdge) {
        int inSampleSize = 1;
        int longestEdge = Math.max(width, height);
        while (longestEdge / inSampleSize > maxEdge * 2) {
            inSampleSize *= 2;
        }
        return Math.max(1, inSampleSize);
    }

    private Bitmap applyExifRotation(Uri imageUri, Bitmap bitmap) {
        try (InputStream inputStream = getContentResolver().openInputStream(imageUri)) {
            if (inputStream == null) {
                return bitmap;
            }
            ExifInterface exifInterface = new ExifInterface(inputStream);
            int orientation = exifInterface.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );
            int rotationDegrees = exifToDegrees(orientation);
            if (rotationDegrees == 0) {
                return bitmap;
            }

            Matrix matrix = new Matrix();
            matrix.postRotate(rotationDegrees);
            return Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    matrix,
                    true
            );
        } catch (Exception ignored) {
            return bitmap;
        }
    }

    private int exifToDegrees(int orientation) {
        if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
            return 90;
        }
        if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
            return 180;
        }
        if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
            return 270;
        }
        return 0;
    }

    private ParsedResult parseAssistantReply(String responseBody) {
        try {
            JsonObject rootObject = JsonParser.parseString(responseBody).getAsJsonObject();
            if (rootObject.has("error") && rootObject.get("error").isJsonObject()) {
                JsonObject errorObject = rootObject.getAsJsonObject("error");
                if (errorObject.has("message") && !errorObject.get("message").isJsonNull()) {
                    throw new IllegalStateException(errorObject.get("message").getAsString());
                }
            }
            JsonObject businessObject = extractBusinessResultObject(rootObject);
            return parseStructuredResult(businessObject);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("模型返回解析失败，请稍后再试", e);
        }
    }

    private JsonObject extractBusinessResultObject(JsonObject rootObject) {
        if (rootObject == null) {
            throw new IllegalStateException("模型返回为空");
        }
        if (looksLikeBusinessResult(rootObject)) {
            return rootObject;
        }
        if (!rootObject.has("choices") || !rootObject.get("choices").isJsonArray()) {
            throw new IllegalStateException("响应中没有 choices");
        }
        JsonArray choices = rootObject.getAsJsonArray("choices");
        if (choices.size() == 0 || !choices.get(0).isJsonObject()) {
            throw new IllegalStateException("响应格式不正确");
        }

        JsonObject firstChoice = choices.get(0).getAsJsonObject();
        if (!firstChoice.has("message") || !firstChoice.get("message").isJsonObject()) {
            throw new IllegalStateException("响应中缺少 message");
        }

        JsonObject messageObject = firstChoice.getAsJsonObject("message");
        if (!messageObject.has("content")) {
            throw new IllegalStateException("模型回复为空");
        }

        String reply = extractReplyText(messageObject.get("content"));
        if (reply.trim().isEmpty()) {
            throw new IllegalStateException("模型回复为空");
        }
        return parseBusinessJson(reply.trim());
    }

    private String extractReplyText(JsonElement content) {
        if (content == null || content.isJsonNull()) {
            return "";
        }
        if (content.isJsonPrimitive()) {
            return content.getAsString();
        }
        if (content.isJsonArray()) {
            StringBuilder builder = new StringBuilder();
            JsonArray contentArray = content.getAsJsonArray();
            for (JsonElement item : contentArray) {
                if (item == null || item.isJsonNull()) {
                    continue;
                }
                if (item.isJsonPrimitive()) {
                    appendReplyPart(builder, item.getAsString());
                    continue;
                }
                if (item.isJsonObject()) {
                    JsonObject itemObject = item.getAsJsonObject();
                    if (itemObject.has("text") && !itemObject.get("text").isJsonNull()) {
                        appendReplyPart(builder, itemObject.get("text").getAsString());
                    }
                    if (itemObject.has("content") && !itemObject.get("content").isJsonNull()) {
                        appendReplyPart(builder, itemObject.get("content").getAsString());
                    }
                }
            }
            return builder.toString();
        }
        return "";
    }

    private void appendReplyPart(StringBuilder builder, String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append(text.trim());
    }

    private ParsedResult parseStructuredResult(JsonObject businessObject) {
        List<String> animeNames = getJsonStringList(businessObject, "anime_names");
        String locationName = getJsonString(businessObject, "location_name");
        String description = getJsonString(businessObject, "description");
        boolean isDomestic = getJsonBoolean(businessObject, "is_domestic");

        String displayAnimeTitle = animeNames.isEmpty()
                ? "AI 待确认作品"
                : chooseFirstNonBlank(animeNames.toArray(new String[0]));
        return new ParsedResult(animeNames, displayAnimeTitle, locationName, description, isDomestic);
    }

    private JsonObject parseBusinessJson(String rawReply) {
        String normalizedReply = stripMarkdownCodeFence(rawReply).trim();
        try {
            JsonObject businessObject = JsonParser.parseString(normalizedReply).getAsJsonObject();
            if (!looksLikeBusinessResult(businessObject)) {
                throw new IllegalStateException("模型返回缺少 anime_names 等关键字段");
            }
            return businessObject;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("模型返回的业务 JSON 解析失败", e);
        }
    }

    private String stripMarkdownCodeFence(String rawReply) {
        String normalized = rawReply.replace("\r\n", "\n").trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?\\s*", "");
            normalized = normalized.replaceFirst("\\s*```$", "");
        }
        return normalized.trim();
    }

    private boolean looksLikeBusinessResult(JsonObject jsonObject) {
        return jsonObject.has("anime_names")
                || jsonObject.has("location_name")
                || jsonObject.has("description")
                || jsonObject.has("is_domestic");
    }

    private String getJsonString(JsonObject jsonObject, String key) {
        if (jsonObject == null || key == null || !jsonObject.has(key) || jsonObject.get(key).isJsonNull()) {
            return "";
        }
        try {
            return jsonObject.get(key).getAsString().trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private List<String> getJsonStringList(JsonObject jsonObject, String key) {
        List<String> results = new ArrayList<>();
        if (jsonObject == null || key == null || !jsonObject.has(key) || jsonObject.get(key).isJsonNull()) {
            return results;
        }
        try {
            JsonArray jsonArray = jsonObject.getAsJsonArray(key);
            for (JsonElement jsonElement : jsonArray) {
                if (jsonElement == null || jsonElement.isJsonNull()) {
                    continue;
                }
                String name = normalizeAnimeKeyword(jsonElement.getAsString());
                if (isBlank(name) || results.contains(name)) {
                    continue;
                }
                results.add(name);
            }
        } catch (Exception ignored) {
            return results;
        }
        return results;
    }

    private boolean getJsonBoolean(JsonObject jsonObject, String key) {
        if (jsonObject == null || key == null || !jsonObject.has(key) || jsonObject.get(key).isJsonNull()) {
            return false;
        }
        try {
            JsonElement jsonElement = jsonObject.get(key);
            if (jsonElement.isJsonPrimitive() && jsonElement.getAsJsonPrimitive().isBoolean()) {
                return jsonElement.getAsBoolean();
            }
            return Boolean.parseBoolean(jsonElement.getAsString().trim());
        } catch (Exception ignored) {
            return false;
        }
    }

    private void bindLocationMapEntry(String locationName) {
        bindLocationMapEntry(locationName, locationName);
    }

    private void bindLocationMapEntry(String locationName, String displayText) {
        if (isBlank(locationName)) {
            clearLocationMapEntry();
            return;
        }
        tvLocationName.setText(chooseFirstNonBlank(displayText, locationName));
        tvLocationName.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
        tvLocationName.setPaintFlags(tvLocationName.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        tvLocationName.setClickable(true);
        tvLocationName.setOnClickListener(view -> openMap(locationName));
    }

    private void clearLocationMapEntry() {
        tvLocationName.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        tvLocationName.setPaintFlags(tvLocationName.getPaintFlags() & ~Paint.UNDERLINE_TEXT_FLAG);
        tvLocationName.setClickable(false);
        tvLocationName.setOnClickListener(null);
    }

    private void clearLocationRoutingState() {
        pendingLocationPermissionSearchGeneration = -1;
        currentDeviceLocation = null;
        currentNavigationTarget = null;
    }

    private void switchResultMode(ResultMode resultMode) {
        currentResultMode = resultMode;
        if (layoutOverseasContent != null) {
            layoutOverseasContent.setVisibility(resultMode == ResultMode.DOMESTIC ? View.GONE : View.VISIBLE);
        }
        if (layoutDomesticContent != null) {
            layoutDomesticContent.setVisibility(resultMode == ResultMode.DOMESTIC ? View.VISIBLE : View.GONE);
        }
        if (tvCommentaryLabel != null) {
            tvCommentaryLabel.setVisibility(resultMode == ResultMode.OVERSEAS ? View.VISIBLE : View.GONE);
        }
        if (tvDesc != null) {
            tvDesc.setVisibility(resultMode == ResultMode.OVERSEAS && !isBlank(tvDesc.getText().toString())
                    ? View.VISIBLE
                    : View.GONE);
        }
        updateNavigateButtonState();
        updateNextOptionButtonState();
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private String getPreferredLocationDisplayName(String fallbackName) {
        if (currentNavigationTarget != null) {
            return chooseFirstNonBlank(
                    currentNavigationTarget.displayName,
                    currentNavigationTarget.address,
                    fallbackName,
                    "地点待确认"
            );
        }
        return chooseFirstNonBlank(fallbackName, "地点待确认");
    }

    private String buildNavigationHint(String baseHint) {
        List<String> lines = new ArrayList<>();
        if (!isBlank(baseHint)) {
            lines.add(baseHint);
        }
        if (currentNavigationTarget != null && !isBlank(currentNavigationTarget.address)) {
            lines.add("定位地址：" + currentNavigationTarget.address);
        }
        if (currentDeviceLocation != null && currentDeviceLocation.hasCoordinates()) {
            lines.add("已获取当前设备位置，可直接规划路线导航。");
        }
        return joinLines(lines);
    }

    private String buildDomesticIntroduction(ParsedResult parsedResult, boolean fromGateway) {
        List<String> lines = new ArrayList<>();
        if (!isBlank(parsedResult.summary)) {
            lines.add(parsedResult.summary);
        } else {
            lines.add("AI 已识别出该景点，但暂未生成更详细的旅行介绍。");
        }
        if (fromGateway && currentNavigationTarget != null && !isBlank(currentNavigationTarget.address)) {
            lines.add("推荐地址：" + currentNavigationTarget.address);
        }
        if (currentDeviceLocation != null && currentDeviceLocation.hasCoordinates()) {
            lines.add("已获取你的当前位置，可直接点击下方按钮进行导航。");
        }
        return joinLines(lines);
    }

    private String buildDomesticRecordDescription(ParsedResult parsedResult, String locationDisplayName) {
        List<String> lines = new ArrayList<>();
        lines.add("景点名称：" + locationDisplayName);
        if (currentNavigationTarget != null && !isBlank(currentNavigationTarget.address)) {
            lines.add("景点地址：" + currentNavigationTarget.address);
        }
        if (!isBlank(parsedResult.summary)) {
            lines.add("");
            lines.add("旅行介绍：" + parsedResult.summary);
        }
        return joinLines(lines);
    }

    private void openMap(String locationName) {
        if (locationName == null || locationName.trim().isEmpty()) {
            return;
        }
        try {
            if (currentNavigationTarget != null && currentNavigationTarget.hasCoordinates()) {
                openPreciseMap(locationName);
                return;
            }
            String query = Uri.encode(locationName);
            Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + query);
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            if (mapIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(mapIntent);
                return;
            }
            Intent webIntent = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/search/?api=1&query=" + query)
            );
            startActivity(webIntent);
        } catch (Exception e) {
            Log.e(DEBUG_TAG, "打开地图失败: " + locationName, e);
            showToast("打开地图失败，请稍后重试");
        }
    }

    private void openPreciseMap(String fallbackLocationName) {
        String locationLabel = Uri.encode(getPreferredLocationDisplayName(fallbackLocationName));
        double latitude = currentNavigationTarget.latitude;
        double longitude = currentNavigationTarget.longitude;
        Uri geoUri = Uri.parse("geo:" + latitude + "," + longitude + "?q=" + latitude + "," + longitude + "(" + locationLabel + ")");
        Intent geoIntent = new Intent(Intent.ACTION_VIEW, geoUri);
        if (geoIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(geoIntent);
            return;
        }

        StringBuilder directionsUrl = new StringBuilder("https://www.google.com/maps/dir/?api=1");
        if (currentDeviceLocation != null && currentDeviceLocation.hasCoordinates()) {
            directionsUrl.append("&origin=")
                    .append(currentDeviceLocation.latitude)
                    .append(",")
                    .append(currentDeviceLocation.longitude);
        }
        directionsUrl.append("&destination=")
                .append(latitude)
                .append(",")
                .append(longitude)
                .append("&travelmode=driving");
        Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(directionsUrl.toString()));
        startActivity(webIntent);
    }

    private void updateCurrentResultSnapshot(
            String animeName,
            String locationName,
            String description,
            String referenceImageUrl
    ) {
        currentAnimeName = animeName;
        currentLocation = locationName;
        currentDesc = description;
        currentLocalUri = selectedImageUri != null ? selectedImageUri.toString() : null;
        currentReferenceUrl = referenceImageUrl;
        hasSavedCurrentRecord = false;
        updateSaveRecordButtonState();
    }

    private void clearCurrentResultSnapshot() {
        currentAnimeName = null;
        currentLocation = null;
        currentDesc = null;
        currentLocalUri = null;
        currentReferenceUrl = null;
        hasSavedCurrentRecord = false;
        updateSaveRecordButtonState();
    }

    private void clearCurrentCandidateState() {
        currentCandidateNames = null;
        currentCandidateIndex = 0;
        currentCandidateLocation = null;
        currentCandidateDesc = null;
        currentTriedSubjectIds = null;
        updateNextOptionButtonState();
    }

    private void updateNextOptionButtonState() {
        if (btnNextOption == null) {
            return;
        }
        boolean hasNextCandidate = currentResultMode == ResultMode.OVERSEAS
                && currentCandidateNames != null
                && currentCandidateIndex >= 0
                && currentCandidateIndex + 1 < currentCandidateNames.size();
        btnNextOption.setVisibility(hasNextCandidate ? View.VISIBLE : View.GONE);
        btnNextOption.setEnabled(hasNextCandidate);
        btnNextOption.setText("🔄 换一个结果");
    }

    private void updateNavigateButtonState() {
        if (btnNavigateSpot == null) {
            return;
        }
        boolean canNavigate = currentResultMode == ResultMode.DOMESTIC
                && (currentNavigationTarget != null
                || (lastParsedResult != null && !isBlank(lastParsedResult.locationName)));
        btnNavigateSpot.setVisibility(canNavigate ? View.VISIBLE : View.GONE);
        btnNavigateSpot.setEnabled(canNavigate);
    }

    private void updateSaveRecordButtonState() {
        if (btnSaveRecord == null) {
            return;
        }
        boolean hasRecordData = !isBlank(currentAnimeName) || !isBlank(currentLocation);
        btnSaveRecord.setVisibility(hasRecordData ? View.VISIBLE : View.GONE);
        btnSaveRecord.setEnabled(hasRecordData && !hasSavedCurrentRecord);
        btnSaveRecord.setText(hasSavedCurrentRecord ? "已记录打卡" : "📌 记录打卡");
    }

    private void saveCurrentRecord() {
        if (isBlank(currentAnimeName) && isBlank(currentLocation)) {
            showToast("当前没有可记录的巡礼结果");
            return;
        }
        if (hasSavedCurrentRecord) {
            showToast("这条巡礼记录已经保存过了");
            return;
        }

        PilgrimRecord record = new PilgrimRecord();
        record.animeName = currentAnimeName;
        record.locationName = currentLocation;
        record.description = currentDesc;
        record.localImageUri = currentLocalUri;
        record.referenceImageUrl = currentReferenceUrl;
        record.timestamp = System.currentTimeMillis();

        backgroundExecutor.execute(() -> {
            try {
                AppDatabase.getInstance(MainActivity.this).pilgrimDao().insert(record);
                runSafelyOnUiThread(() -> {
                    hasSavedCurrentRecord = true;
                    updateSaveRecordButtonState();
                    showToast("打卡成功！已收录至巡礼日记");
                });
            } catch (Exception e) {
                Log.e(DEBUG_TAG, "保存巡礼记录失败", e);
                runSafelyOnUiThread(() -> showToast("打卡保存失败，请稍后重试"));
            }
        });
    }

    private String extractLabeledValue(String text, String... labels) {
        String[] lines = text.split("\\n");
        for (String rawLine : lines) {
            String line = sanitizeLine(rawLine);
            if (line.isEmpty()) {
                continue;
            }
            for (String label : labels) {
                String value = tryExtractAfterLabel(line, label);
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }

        for (String label : labels) {
            Pattern pattern = Pattern.compile(Pattern.quote(label) + "\\s*[：:]\\s*([^\\n]+)");
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String value = cleanExtractedValue(matcher.group(1));
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return "";
    }

    private String tryExtractAfterLabel(String line, String label) {
        String normalizedLine = line
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .trim();
        String[] candidates = new String[] {
                label + "：",
                label + ":",
                "【" + label + "】",
                "[" + label + "]",
                label
        };
        for (String candidate : candidates) {
            if (normalizedLine.startsWith(candidate)) {
                String value = normalizedLine.substring(candidate.length()).trim();
                return cleanExtractedValue(value);
            }
        }
        return "";
    }

    private String sanitizeLine(String line) {
        return line
                .replace('\u3000', ' ')
                .replaceAll("^\\s*[-*+>#\\d.()]+\\s*", "")
                .trim();
    }

    private String cleanExtractedValue(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value
                .replaceAll("^[：:：\\-\\s]+", "")
                .replaceAll("[*_`#]+", "")
                .trim();
        cleaned = cleaned.replaceAll("\\s{2,}", " ");
        return cleaned;
    }

    private String extractAnimeTitleFallback(String text) {
        Matcher matcher = Pattern.compile("《[^》]{1,40}》").matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }

        for (String rawLine : text.split("\\n")) {
            String line = sanitizeLine(rawLine);
            if (line.isEmpty()) {
                continue;
            }
            if (line.length() <= 30 && !line.contains("地点") && !line.contains("剧情")) {
                return line;
            }
        }
        return "";
    }

    private String extractLocationFallback(String text) {
        String[] lines = text.split("\\n");
        for (String rawLine : lines) {
            String line = sanitizeLine(rawLine);
            if (line.isEmpty()) {
                continue;
            }
            if (line.contains("站") || line.contains("桥") || line.contains("神社")
                    || line.contains("街") || line.contains("海岸") || line.contains("学校")
                    || line.contains("坡") || line.contains("路口") || line.contains("公园")
                    || line.contains("车站") || line.contains("码头") || line.contains("寺")
                    || line.contains("湖") || line.contains("岛")) {
                if (!line.contains("剧情") && !line.contains("动漫") && !line.contains("作品")) {
                    return line;
                }
            }
        }
        return "";
    }

    private String buildSummary(String fullReply, String animeTitle, String locationName) {
        List<String> summaryLines = new ArrayList<>();
        for (String rawLine : fullReply.split("\\n")) {
            String sanitized = sanitizeLine(rawLine);
            if (sanitized.isEmpty()) {
                continue;
            }
            if (containsAnyLabel(sanitized,
                    "动漫名称", "动画名称", "作品名称", "番剧名称",
                    "现实地点", "取景地", "巡礼地点", "原型地", "对应地点")) {
                continue;
            }
            if (!animeTitle.isEmpty() && sanitized.equals(animeTitle)) {
                continue;
            }
            if (!locationName.isEmpty() && sanitized.equals(locationName)) {
                continue;
            }
            summaryLines.add(sanitized);
        }

        String summary = String.join("\n\n", summaryLines).trim();
        if (!summary.isEmpty()) {
            return summary;
        }

        String fallback = fullReply;
        if (!animeTitle.isEmpty()) {
            fallback = fallback.replace(animeTitle, "").trim();
        }
        if (!locationName.isEmpty()) {
            fallback = fallback.replace(locationName, "").trim();
        }
        return fallback.trim();
    }

    private boolean containsAnyLabel(String text, String... labels) {
        for (String label : labels) {
            if (text.startsWith(label + "：")
                    || text.startsWith(label + ":")
                    || text.equals(label)
                    || text.startsWith("【" + label + "】")
                    || text.startsWith("[" + label + "]")) {
                return true;
            }
        }
        return false;
    }

    private String normalizeAnimeKeyword(String animeTitle) {
        if (animeTitle == null) {
            return "";
        }
        String keyword = animeTitle.trim();
        if (keyword.startsWith("《") && keyword.endsWith("》") && keyword.length() > 2) {
            keyword = keyword.substring(1, keyword.length() - 1);
        }
        keyword = keyword.replaceAll("^(动漫名称|动画名称|作品名称|番剧名称)\\s*[：:]\\s*", "");
        keyword = keyword.replaceAll("\\s+", " ").trim();
        if ("AI 待确认作品".equals(keyword)) {
            return "";
        }
        return keyword;
    }

    private String chooseFirstNonBlank(String... values) {
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

    private boolean hasNextCandidate() {
        return currentCandidateNames != null
                && currentCandidateIndex >= 0
                && currentCandidateIndex + 1 < currentCandidateNames.size();
    }

    private String getCurrentCandidateName(String fallbackName) {
        if (currentCandidateNames != null
                && currentCandidateIndex >= 0
                && currentCandidateIndex < currentCandidateNames.size()) {
            return currentCandidateNames.get(currentCandidateIndex);
        }
        return fallbackName;
    }

    private int beginNewSearchGeneration() {
        activeSearchGeneration++;
        return activeSearchGeneration;
    }

    private boolean isStaleSearch(int searchGeneration) {
        return searchGeneration != activeSearchGeneration;
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

    private String buildResultText(
            ParsedResult parsedResult,
            AnitabiApiClient.BangumiLiteResponse bangumiLiteResponse,
            AnitabiApiClient.PointDetail firstPoint,
            boolean hasMultiplePoints
    ) {
        String animeDisplayName = chooseFirstNonBlank(
                bangumiLiteResponse.getCn(),
                bangumiLiteResponse.getTitle(),
                parsedResult.animeTitle,
                "AI 识别结果"
        );
        String locationDisplayName = chooseFirstNonBlank(
                firstPoint.getName(),
                parsedResult.locationName,
                bangumiLiteResponse.getCity(),
                "巡礼地点待进一步确认"
        );

        List<String> lines = new ArrayList<>();
        lines.add("识别作品：" + animeDisplayName);
        if (!isBlank(bangumiLiteResponse.getTitle())
                && !bangumiLiteResponse.getTitle().equals(animeDisplayName)) {
            lines.add("原名：" + bangumiLiteResponse.getTitle());
        }
        lines.add("巡礼地点：" + locationDisplayName);
        if (!isBlank(bangumiLiteResponse.getCity())) {
            lines.add("所在城市：" + bangumiLiteResponse.getCity());
        }
        int pointEpisode = parseIntSafely(firstPoint.getEp());
        if (pointEpisode > 0) {
            lines.add("关联集数：第" + pointEpisode + "集");
        }
        if (!isBlank(firstPoint.getOrigin())) {
            lines.add("图片来源：" + firstPoint.getOrigin());
        }
        if (hasMultiplePoints) {
            lines.add("找到多个地点，当前展示最接近识别地点的一条。");
        }
        if (!isBlank(parsedResult.summary)) {
            lines.add("");
            lines.add("场景解读：" + parsedResult.summary);
        }
        return joinLines(lines);
    }

    private String buildSupplementText(
            ParsedResult parsedResult,
            AnitabiApiClient.BangumiLiteResponse bangumiLiteResponse,
            AnitabiApiClient.PointDetail firstPoint,
            boolean hasMultiplePoints
    ) {
        List<String> lines = new ArrayList<>();
        if (!isBlank(parsedResult.locationName) && !parsedResult.locationName.equals(firstPoint.getName())) {
            lines.add("大模型推测场景：" + parsedResult.locationName);
        }
        int screenshotSecond = parseIntSafely(firstPoint.getS());
        if (screenshotSecond > 0) {
            lines.add("截图时间点：" + screenshotSecond + " 秒");
        }
        int pointsLength = parseIntSafely(bangumiLiteResponse.getPointsLength());
        if (pointsLength > 0) {
            lines.add("Anitabi 收录地标数：" + pointsLength);
        }
        if (hasMultiplePoints) {
            lines.add("如需更完整巡礼路线，可继续扩展列表展示逻辑。");
        }
        return joinLines(lines);
    }

    private AnitabiApiClient.PointDetail chooseBestPointDetail(
            List<AnitabiApiClient.PointDetail> pointDetails,
            String expectedLocation
    ) {
        if (pointDetails == null || pointDetails.isEmpty()) {
            throw new IllegalArgumentException("pointDetails 不能为空");
        }
        if (isBlank(expectedLocation)) {
            return pointDetails.get(0);
        }
        AnitabiApiClient.PointDetail bestPoint = pointDetails.get(0);
        int bestScore = scoreLocationMatch(expectedLocation, bestPoint.getName(), bestPoint.getOrigin());
        for (AnitabiApiClient.PointDetail pointDetail : pointDetails) {
            if (pointDetail == null) {
                continue;
            }
            int score = scoreLocationMatch(expectedLocation, pointDetail.getName(), pointDetail.getOrigin());
            if (score > bestScore) {
                bestScore = score;
                bestPoint = pointDetail;
            }
        }
        return bestPoint;
    }

    private AnitabiApiClient.LitePoint chooseBestLitePoint(
            List<AnitabiApiClient.LitePoint> litePoints,
            String expectedLocation
    ) {
        if (litePoints == null || litePoints.isEmpty()) {
            throw new IllegalArgumentException("litePoints 不能为空");
        }
        if (isBlank(expectedLocation)) {
            return litePoints.get(0);
        }
        AnitabiApiClient.LitePoint bestPoint = litePoints.get(0);
        int bestScore = scoreLocationMatch(expectedLocation, bestPoint.getCn(), bestPoint.getName());
        for (AnitabiApiClient.LitePoint litePoint : litePoints) {
            if (litePoint == null) {
                continue;
            }
            int score = scoreLocationMatch(expectedLocation, litePoint.getCn(), litePoint.getName());
            if (score > bestScore) {
                bestScore = score;
                bestPoint = litePoint;
            }
        }
        return bestPoint;
    }

    private int scoreLocationMatch(String expectedLocation, String... candidates) {
        String normalizedExpected = normalizeLocationForMatch(expectedLocation);
        if (isBlank(normalizedExpected)) {
            return 0;
        }
        int bestScore = 0;
        for (String candidate : candidates) {
            String normalizedCandidate = normalizeLocationForMatch(candidate);
            if (isBlank(normalizedCandidate)) {
                continue;
            }
            int score = 0;
            if (normalizedExpected.equals(normalizedCandidate)) {
                score += 100;
            }
            if (normalizedExpected.contains(normalizedCandidate) || normalizedCandidate.contains(normalizedExpected)) {
                score += 50;
            }
            score += countSharedCharacters(normalizedExpected, normalizedCandidate) * 2;
            if (score > bestScore) {
                bestScore = score;
            }
        }
        return bestScore;
    }

    private String normalizeLocationForMatch(String value) {
        if (isBlank(value)) {
            return "";
        }
        return value
                .replaceAll("[\\s\\p{Punct}【】「」『』（）()·・、，。:：-]", "")
                .toLowerCase();
    }

    private int countSharedCharacters(String left, String right) {
        int shared = 0;
        for (int i = 0; i < left.length(); i++) {
            char current = left.charAt(i);
            if (right.indexOf(current) >= 0) {
                shared++;
            }
        }
        return shared;
    }

    private String joinLines(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (isBlank(line)) {
                if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
                    builder.append("\n");
                }
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(line);
        }
        return builder.toString().trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void runSafelyOnUiThread(Runnable action) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        runOnUiThread(action);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String extractFileNameFromUri(Uri uri) {
        if (uri == null || uri.getLastPathSegment() == null) {
            return "";
        }
        String lastPath = uri.getLastPathSegment();
        int separatorIndex = lastPath.lastIndexOf('/');
        if (separatorIndex >= 0 && separatorIndex < lastPath.length() - 1) {
            return lastPath.substring(separatorIndex + 1);
        }
        return lastPath;
    }

    @SuppressWarnings("unchecked")
    private <T extends View> T findOptionalViewByName(String idName) {
        int viewId = getResources().getIdentifier(idName, "id", getPackageName());
        if (viewId == 0) {
            return null;
        }
        return (T) findViewById(viewId);
    }
}
