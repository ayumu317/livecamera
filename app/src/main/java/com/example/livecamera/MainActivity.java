package com.example.livecamera;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
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
import androidx.core.widget.NestedScrollView;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.navigation.NavigationView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
    private static final String PRIVATE_CAPTURE_DIR_NAME = "livecamera_captures";
    private static final String PRIVATE_DIARY_DIR_NAME = "livecamera_diary";
    private static final String CACHE_CAPTURE_DIR_NAME = "images";
    private static final String PREFS_NAME = "livecamera_settings";
    private static final String PREF_DEFAULT_MODE = "default_identify_mode";
    private static final String PREF_PREVIEW_MODE = "preview_mode";
    private static final String PREF_SAVE_ACTION = "save_action";
    private static final String PREF_COLOR_THEME = "color_theme";
    private static final String PREVIEW_FIT = "fit";
    private static final String PREVIEW_FILL = "fill";
    private static final String SAVE_ACTION_STAY = "stay";
    private static final String SAVE_ACTION_OPEN_DIARY = "open_diary";
    private static final String THEME_DEFAULT = "default";
    private static final String THEME_MINT = "mint";
    private static final String THEME_WARM = "warm";
    private static final String THEME_DARK = "dark";
    private static final String DEFAULT_RESULT_HINT = "请选择一张实景照片，然后点击“开始识别”。";
    private static final String DEFAULT_DESC_HINT = "等待识别结果";

    private ShapeableImageView ivScenePreview;
    private ShapeableImageView ivResultReference;
    private TextView tvPreviewPlaceholderHint;
    private LinearLayout layoutActionButtons;
    private MaterialButton btnOpenCamera;
    private MaterialButton btnOpenGallery;
    private MaterialButton btnStartMatch;
    private MaterialButton btnDrawerMenu;
    private MaterialButton btnDiary;
    private MaterialButton btnModeAnime;
    private MaterialButton btnModeDomestic;
    private MaterialButton btnModeAuto;
    private EditText etManualAnimeName;
    private MaterialButton btnSearchManualAnime;
    private MaterialButton btnSaveRecord;
    private MaterialButton btnNextOption;
    private MaterialButton btnNavigateSpot;
    private MaterialCardView cardResult;
    private NestedScrollView scrollContent;
    private LinearLayout layoutOverseasContent;
    private LinearLayout layoutDomesticContent;
    private LinearLayout layoutNextStepHint;
    private LinearLayout layoutAnimeRematch;
    private LinearLayout layoutAnimeCandidateList;
    private LinearLayout layoutSpotCandidateList;
    private TextView tvResultSummary;
    private TextView tvAnimeTitle;
    private TextView tvLocationName;
    private TextView tvReferenceLabel;
    private TextView tvCommentaryLabel;
    private TextView tvDomesticAddress;
    private TextView tvDomesticIntro;
    private TextView tvOverseasBadge;
    private TextView tvDomesticBadge;
    private TextView tvNextStepHint;
    private Chip chipResultState;
    private Chip chipConfidence;
    private ProgressBar pbLoading;
    private TextView tvDesc;
    private View previewScrimView;
    private View scanlineView;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private SharedPreferences appSettings;
    private String previewMode = PREVIEW_FIT;
    private String saveAction = SAVE_ACTION_STAY;
    private String colorTheme = THEME_DEFAULT;
    private boolean refreshSettingsOnResume;

    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private DoubaoVisionClient doubaoVisionClient;
    private AnitabiApiClient anitabiApiClient;
    private SerpApiClient serpApiClient;
    private TencentLocationHelper tencentLocationHelper;
    private LocationSearchClient locationSearchClient;
    private TourInfoApiClient tourInfoApiClient;
    private TourAuthSession tourAuthSession;

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
    private String confirmedAnimeName;
    private String confirmedSpotName;
    private String confirmedLocationName;
    private String confirmedDescription;
    private String confirmedReferenceUrl;
    private String confirmedLocalImageUri;
    private boolean hasSavedCurrentRecord;
    private List<String> currentCandidateNames;
    private int currentCandidateIndex = 0;
    private String currentCandidateLocation;
    private String currentCandidateDesc;
    private List<String> currentVisualKeywords;
    private List<String> currentSpotSearchKeywords;
    private Set<Integer> currentTriedSubjectIds;
    private boolean hasSpotCandidateOptions;
    private boolean spotCandidateListExpanded;
    private boolean allowManualAnimeRematch;
    private int currentSpotCandidateCount;
    private int activeSearchGeneration = 0;
    private int pendingLocationPermissionSearchGeneration = -1;
    private Integer lastManagementRecognitionId;
    private String lastManagementSupplementText;
    private DoubaoVisionClient.UsageStats lastDoubaoUsageStats;
    private DeviceLocationSnapshot currentDeviceLocation;
    private LocationNavigationTarget currentNavigationTarget;
    private ResultMode currentResultMode = ResultMode.NONE;
    private IdentifyMode currentIdentifyMode = IdentifyMode.AUTO;

    private enum IdentifyMode {
        AUTO,
        DOMESTIC,
        ANIME
    }

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
        final List<String> visualKeywords;
        final List<String> spotSearchKeywords;
        final double confidence;
        final String reason;

        ParsedResult(
                List<String> animeNames,
                String animeTitle,
                String locationName,
                String summary,
                boolean isDomestic
        ) {
            this(animeNames, animeTitle, locationName, summary, isDomestic, null, null, -1, "");
        }

        ParsedResult(
                List<String> animeNames,
                String animeTitle,
                String locationName,
                String summary,
                boolean isDomestic,
                List<String> visualKeywords,
                List<String> spotSearchKeywords
        ) {
            this(animeNames, animeTitle, locationName, summary, isDomestic, visualKeywords, spotSearchKeywords, -1, "");
        }

        ParsedResult(
                List<String> animeNames,
                String animeTitle,
                String locationName,
                String summary,
                boolean isDomestic,
                List<String> visualKeywords,
                List<String> spotSearchKeywords,
                double confidence,
                String reason
        ) {
            this.animeNames = animeNames != null ? new ArrayList<>(animeNames) : new ArrayList<>();
            this.animeTitle = animeTitle;
            this.locationName = locationName;
            this.summary = summary;
            this.isDomestic = isDomestic;
            this.visualKeywords = visualKeywords != null ? new ArrayList<>(visualKeywords) : new ArrayList<>();
            this.spotSearchKeywords = spotSearchKeywords != null ? new ArrayList<>(spotSearchKeywords) : new ArrayList<>();
            this.confidence = confidence;
            this.reason = reason;
        }
    }

    private static final class SpotCandidate {
        final AnitabiApiClient.PointDetail pointDetail;
        final int score;
        final String reason;

        SpotCandidate(AnitabiApiClient.PointDetail pointDetail, int score, String reason) {
            this.pointDetail = pointDetail;
            this.score = score;
            this.reason = reason;
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
        loadAppSettings();
        initActivityResultLaunchers();
        doubaoVisionClient = new DoubaoVisionClient();
        anitabiApiClient = new AnitabiApiClient();
        serpApiClient = new SerpApiClient();
        tencentLocationHelper = new TencentLocationHelper(this);
        locationSearchClient = new LocationSearchClient();
        tourInfoApiClient = new TourInfoApiClient();
        tourAuthSession = new TourAuthSession(this);
        initViewState();
        initListeners();
        restoreState(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appSettings != null && refreshSettingsOnResume) {
            refreshSettingsOnResume = false;
            loadAppSettings();
            applyAppSettingsToUi();
        }
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
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopScanlineIndicator();
        backgroundExecutor.shutdownNow();
        if (doubaoVisionClient != null) {
            doubaoVisionClient.cancelAll();
        }
        if (tourInfoApiClient != null) {
            tourInfoApiClient.cancelAll();
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
        drawerLayout = findOptionalViewByName("drawerLayout");
        navigationView = findOptionalViewByName("navigationView");
        scrollContent = findOptionalViewByName("scrollContent");
        ivScenePreview = findViewById(R.id.iv_preview);
        ivResultReference = findViewById(R.id.iv_result_reference);
        tvPreviewPlaceholderHint = findViewById(R.id.tvPreviewPlaceholderHint);
        layoutActionButtons = findViewById(R.id.layoutActionButtons);
        btnOpenCamera = findViewById(R.id.btn_camera);
        btnOpenGallery = findViewById(R.id.btn_gallery);
        btnStartMatch = findViewById(R.id.btn_identify);
        btnDrawerMenu = findOptionalViewByName("btnDrawerMenu");
        btnDiary = findOptionalViewByName("btn_diary");
        btnModeAnime = findViewById(R.id.btnModeAnime);
        btnModeDomestic = findViewById(R.id.btnModeDomestic);
        btnModeAuto = findViewById(R.id.btnModeAuto);
        etManualAnimeName = findViewById(R.id.etManualAnimeName);
        btnSearchManualAnime = findViewById(R.id.btnSearchManualAnime);
        btnSaveRecord = findOptionalViewByName("btnSaveRecord");
        btnNextOption = findOptionalViewByName("btnNextOption");
        btnNavigateSpot = findOptionalViewByName("btnNavigateSpot");
        cardResult = findViewById(R.id.cardResult);
        layoutOverseasContent = findOptionalViewByName("layoutOverseasContent");
        layoutDomesticContent = findOptionalViewByName("layoutDomesticContent");
        layoutNextStepHint = findOptionalViewByName("layoutNextStepHint");
        layoutAnimeRematch = findViewById(R.id.layoutAnimeRematch);
        layoutAnimeCandidateList = findViewById(R.id.layoutAnimeCandidateList);
        layoutSpotCandidateList = findViewById(R.id.layoutSpotCandidateList);
        tvResultSummary = findViewById(R.id.tv_result);
        tvAnimeTitle = findViewById(R.id.tvAnimeTitle);
        tvLocationName = findViewById(R.id.tvLocationName);
        tvReferenceLabel = findViewById(R.id.tvReferenceLabel);
        tvCommentaryLabel = findOptionalViewByName("tvCommentaryLabel");
        tvDomesticAddress = findOptionalViewByName("tvDomesticAddress");
        tvDomesticIntro = findOptionalViewByName("tvDomesticIntro");
        tvOverseasBadge = findOptionalViewByName("tvOverseasBadge");
        tvDomesticBadge = findOptionalViewByName("tvDomesticBadge");
        tvNextStepHint = findOptionalViewByName("tvNextStepHint");
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
        updateIdentifyModeButtons();
        applyAppSettingsToUi();
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
            pendingCameraFile = resolveCapturedImageFile(extractFileNameFromUri(pendingCameraImageUri));
        }
    }

    private void initListeners() {
        btnOpenGallery.setOnClickListener(view -> galleryLauncher.launch("image/*"));
        btnOpenCamera.setOnClickListener(view -> openCamera());
        btnStartMatch.setOnClickListener(view -> startIdentifyFlow());
        if (btnDrawerMenu != null) {
            btnDrawerMenu.setOnClickListener(view -> openDrawerMenu());
        }
        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_mode_auto) {
                    setIdentifyMode(IdentifyMode.AUTO);
                } else if (itemId == R.id.nav_mode_anime) {
                    setIdentifyMode(IdentifyMode.ANIME);
                } else if (itemId == R.id.nav_mode_domestic) {
                    setIdentifyMode(IdentifyMode.DOMESTIC);
                } else if (itemId == R.id.nav_diary) {
                    openPilgrimDiary();
                } else if (itemId == R.id.nav_pick_image) {
                    galleryLauncher.launch("image/*");
                } else if (itemId == R.id.nav_settings) {
                    openSettingsPage();
                } else if (itemId == R.id.nav_account) {
                    openAccountPage();
                } else if (itemId == R.id.nav_about) {
                    showToast("默认智能识别；可从侧边栏切换动漫巡礼或国内旅行。");
                }
                closeDrawerMenu();
                return true;
            });
        }
        btnModeAnime.setOnClickListener(view -> setIdentifyMode(IdentifyMode.ANIME));
        btnModeDomestic.setOnClickListener(view -> setIdentifyMode(IdentifyMode.DOMESTIC));
        btnModeAuto.setOnClickListener(view -> setIdentifyMode(IdentifyMode.AUTO));
        btnSearchManualAnime.setOnClickListener(view -> startManualAnimeRematch());
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
                if (hasSpotCandidateOptions) {
                    setSpotCandidateListExpanded(!spotCandidateListExpanded);
                    allowManualAnimeRematch = false;
                    updateManualAnimeRematchVisibility();
                    if (spotCandidateListExpanded) {
                        scrollToView(layoutSpotCandidateList);
                    } else {
                        scrollToView(cardResult);
                    }
                    return;
                }
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

    private void setIdentifyMode(IdentifyMode identifyMode) {
        IdentifyMode nextIdentifyMode = identifyMode != null ? identifyMode : IdentifyMode.AUTO;
        if (currentIdentifyMode != nextIdentifyMode) {
            resetResultsForModeSwitch();
        }
        currentIdentifyMode = nextIdentifyMode;
        updateIdentifyModeButtons();
        switchResultMode(currentResultMode);
        Log.d(DEBUG_TAG, "currentIdentifyMode=" + currentIdentifyMode);
    }

    private void updateIdentifyModeButtons() {
        applyIdentifyModeButtonState(btnModeAnime, currentIdentifyMode == IdentifyMode.ANIME);
        applyIdentifyModeButtonState(btnModeDomestic, currentIdentifyMode == IdentifyMode.DOMESTIC);
        applyIdentifyModeButtonState(btnModeAuto, currentIdentifyMode == IdentifyMode.AUTO);
        updateNavigationModeState();
    }

    private void applyIdentifyModeButtonState(@Nullable MaterialButton button, boolean selected) {
        if (button == null) {
            return;
        }
        int selectedColor = getThemePrimaryColor();
        int unselectedColor = ContextCompat.getColor(this, R.color.chip_brand_background);
        int selectedTextColor = ContextCompat.getColor(this, android.R.color.white);
        int unselectedTextColor = getThemePrimaryColor();
        int strokeColor = selected ? getThemePrimaryColor() : ContextCompat.getColor(this, R.color.card_stroke);
        button.setSelected(selected);
        button.setTextColor(selected ? selectedTextColor : unselectedTextColor);
        button.setBackgroundTintList(ColorStateList.valueOf(selected ? selectedColor : unselectedColor));
        button.setStrokeColor(ColorStateList.valueOf(strokeColor));
    }

    private void openDrawerMenu() {
        if (drawerLayout != null) {
            drawerLayout.openDrawer(GravityCompat.START);
        }
    }

    private void closeDrawerMenu() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    private void updateNavigationModeState() {
        if (navigationView == null) {
            return;
        }
        int checkedId;
        if (currentIdentifyMode == IdentifyMode.ANIME) {
            checkedId = R.id.nav_mode_anime;
        } else if (currentIdentifyMode == IdentifyMode.DOMESTIC) {
            checkedId = R.id.nav_mode_domestic;
        } else {
            checkedId = R.id.nav_mode_auto;
        }
        navigationView.setCheckedItem(checkedId);
    }

    private void openSettingsPanel() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("设置")
                .setMessage("这里会用于管理默认识别模式、API 配置提示、保存偏好和更多辅助功能。\n\n当前默认首页为智能识别，可从侧边栏切换动漫巡礼或国内旅行。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void openSettingsPage() {
        refreshSettingsOnResume = true;
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void openAccountPage() {
        startActivity(new Intent(this, AccountActivity.class));
    }

    private void openSettingsMenu() {
        String[] items = new String[] {
                "默认识别模式：" + getIdentifyModeLabel(currentIdentifyMode),
                "图片预览方式：" + getPreviewModeLabel(previewMode),
                "保存后动作：" + getSaveActionLabel(saveAction),
                "颜色主题：" + getColorThemeLabel(colorTheme)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle("设置")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        openDefaultModeSetting();
                    } else if (which == 1) {
                        openPreviewModeSetting();
                    } else if (which == 2) {
                        openSaveActionSetting();
                    } else if (which == 3) {
                        openColorThemeSetting();
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void resetResultsForModeSwitch() {
        beginNewSearchGeneration();
        clearLocationRoutingState();
        clearResultDisplay();
        updateLoadingState(false);
        clearCurrentResultSnapshot();
        clearCurrentCandidateState();
        tvResultSummary.setText(DEFAULT_RESULT_HINT);
        if (tvDesc != null) {
            tvDesc.setText(DEFAULT_DESC_HINT);
        }
        updateManualAnimeRematchVisibility();
        updateNextOptionButtonState();
        updateNavigateButtonState();
    }

    private void loadAppSettings() {
        appSettings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentIdentifyMode = readIdentifyModeSetting(
                appSettings.getString(PREF_DEFAULT_MODE, IdentifyMode.AUTO.name())
        );
        previewMode = chooseKnownValue(
                appSettings.getString(PREF_PREVIEW_MODE, PREVIEW_FIT),
                PREVIEW_FIT,
                PREVIEW_FILL
        );
        saveAction = chooseKnownValue(
                appSettings.getString(PREF_SAVE_ACTION, SAVE_ACTION_STAY),
                SAVE_ACTION_STAY,
                SAVE_ACTION_OPEN_DIARY
        );
        colorTheme = chooseKnownValue(
                appSettings.getString(PREF_COLOR_THEME, THEME_DEFAULT),
                THEME_DEFAULT,
                THEME_MINT,
                THEME_WARM,
                THEME_DARK
        );
    }

    private void openDefaultModeSetting() {
        IdentifyMode[] modes = new IdentifyMode[] {
                IdentifyMode.AUTO,
                IdentifyMode.ANIME,
                IdentifyMode.DOMESTIC
        };
        String[] labels = new String[] {"智能识别", "动漫巡礼", "国内旅行"};
        int checked = currentIdentifyMode == IdentifyMode.ANIME ? 1
                : currentIdentifyMode == IdentifyMode.DOMESTIC ? 2 : 0;
        new MaterialAlertDialogBuilder(this)
                .setTitle("默认识别模式")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    IdentifyMode selectedMode = modes[Math.max(0, Math.min(which, modes.length - 1))];
                    appSettings.edit().putString(PREF_DEFAULT_MODE, selectedMode.name()).apply();
                    setIdentifyMode(selectedMode);
                    dialog.dismiss();
                    showToast("默认识别模式已更新");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openPreviewModeSetting() {
        String[] labels = new String[] {"完整显示", "填充裁剪"};
        int checked = PREVIEW_FILL.equals(previewMode) ? 1 : 0;
        new MaterialAlertDialogBuilder(this)
                .setTitle("图片预览方式")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    previewMode = which == 1 ? PREVIEW_FILL : PREVIEW_FIT;
                    appSettings.edit().putString(PREF_PREVIEW_MODE, previewMode).apply();
                    applyPreviewMode();
                    dialog.dismiss();
                    showToast("图片预览方式已更新");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openSaveActionSetting() {
        String[] labels = new String[] {"停留当前页", "保存后打开巡礼日记"};
        int checked = SAVE_ACTION_OPEN_DIARY.equals(saveAction) ? 1 : 0;
        new MaterialAlertDialogBuilder(this)
                .setTitle("保存后动作")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    saveAction = which == 1 ? SAVE_ACTION_OPEN_DIARY : SAVE_ACTION_STAY;
                    appSettings.edit().putString(PREF_SAVE_ACTION, saveAction).apply();
                    dialog.dismiss();
                    showToast("保存后动作已更新");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openColorThemeSetting() {
        String[] labels = new String[] {"默认蓝紫", "清爽青绿", "暖色旅行", "深色预览"};
        String[] values = new String[] {THEME_DEFAULT, THEME_MINT, THEME_WARM, THEME_DARK};
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(colorTheme)) {
                checked = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("颜色主题")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    int safeIndex = Math.max(0, Math.min(which, values.length - 1));
                    colorTheme = values[safeIndex];
                    appSettings.edit().putString(PREF_COLOR_THEME, colorTheme).apply();
                    applyAppSettingsToUi();
                    dialog.dismiss();
                    showToast("颜色主题已更新");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void applyAppSettingsToUi() {
        applyPreviewMode();
        applyColorTheme();
        updateIdentifyModeButtons();
    }

    private void applyPreviewMode() {
        if (ivScenePreview == null) {
            return;
        }
        ivScenePreview.setScaleType(PREVIEW_FILL.equals(previewMode)
                ? ImageView.ScaleType.CENTER_CROP
                : ImageView.ScaleType.FIT_CENTER);
    }

    private void applyColorTheme() {
        int primaryColor = getThemePrimaryColor();
        ColorStateList primaryTint = ColorStateList.valueOf(primaryColor);
        if (btnStartMatch != null) {
            btnStartMatch.setBackgroundTintList(primaryTint);
        }
        if (btnSaveRecord != null) {
            btnSaveRecord.setBackgroundTintList(primaryTint);
        }
        if (btnDrawerMenu != null) {
            btnDrawerMenu.setTextColor(primaryColor);
            btnDrawerMenu.setIconTint(primaryTint);
        }
        if (navigationView != null) {
            navigationView.setItemIconTintList(primaryTint);
            navigationView.setItemTextColor(primaryTint);
        }
        applyResultModeVisualStyle(currentResultMode);
    }

    private IdentifyMode readIdentifyModeSetting(@Nullable String value) {
        if (IdentifyMode.ANIME.name().equals(value)) {
            return IdentifyMode.ANIME;
        }
        if (IdentifyMode.DOMESTIC.name().equals(value)) {
            return IdentifyMode.DOMESTIC;
        }
        return IdentifyMode.AUTO;
    }

    private String chooseKnownValue(String value, String fallback, String... allowed) {
        if (value == null) {
            return fallback;
        }
        if (fallback.equals(value)) {
            return value;
        }
        for (String item : allowed) {
            if (item.equals(value)) {
                return value;
            }
        }
        return fallback;
    }

    private String getIdentifyModeLabel(IdentifyMode identifyMode) {
        if (identifyMode == IdentifyMode.ANIME) {
            return "动漫巡礼";
        }
        if (identifyMode == IdentifyMode.DOMESTIC) {
            return "国内旅行";
        }
        return "智能识别";
    }

    private String getPreviewModeLabel(String value) {
        return PREVIEW_FILL.equals(value) ? "填充裁剪" : "完整显示";
    }

    private String getSaveActionLabel(String value) {
        return SAVE_ACTION_OPEN_DIARY.equals(value) ? "打开巡礼日记" : "停留当前页";
    }

    private String getColorThemeLabel(String value) {
        if (THEME_MINT.equals(value)) {
            return "清爽青绿";
        }
        if (THEME_WARM.equals(value)) {
            return "暖色旅行";
        }
        if (THEME_DARK.equals(value)) {
            return "深色预览";
        }
        return "默认蓝紫";
    }

    private int getThemePrimaryColor() {
        if (THEME_MINT.equals(colorTheme)) {
            return Color.parseColor("#1FAE9A");
        }
        if (THEME_WARM.equals(colorTheme)) {
            return Color.parseColor("#E07A3F");
        }
        if (THEME_DARK.equals(colorTheme)) {
            return Color.parseColor("#3F4A68");
        }
        return ContextCompat.getColor(this, R.color.brand_primary);
    }

    private void startManualAnimeRematch() {
        String animeName = etManualAnimeName != null ? etManualAnimeName.getText().toString().trim() : "";
        if (isBlank(animeName)) {
            showToast("请输入作品名");
            return;
        }
        Log.d(DEBUG_TAG, "manualAnimeName = " + animeName);
        submitManagementCorrection(animeName);
        startAnimeRematchWithWork(animeName, false);
    }

    private void startAnimeRematchWithWork(String animeName, boolean selectedFromAi) {
        if (isBlank(animeName)) {
            showToast("请输入作品名");
            return;
        }
        if (selectedImageUri == null) {
            renderError("请先从相册选择或拍摄一张图片", null);
            return;
        }
        if (selectedFromAi) {
            Log.d(DEBUG_TAG, "selectedAnimeFromAI = " + animeName);
        }
        Log.d(DEBUG_TAG, "start AI rematch with image and work = " + animeName);
        showProcessingPlaceholder();
        updateLoadingState(true);
        lastDoubaoUsageStats = null;
        clearCurrentCandidateState();
        clearLocationRoutingState();
        int searchGeneration = beginNewSearchGeneration();
        Uri imageUri = selectedImageUri;
        backgroundExecutor.execute(() -> {
            final String base64Image;
            final double[] gpsLatLng = getGpsFromUri(imageUri);
            try {
                base64Image = compressImageToBase64(imageUri);
            } catch (Exception e) {
                Log.e(TAG, "Failed to convert image to Base64 for anime rematch", e);
                renderError("图片处理失败，请换一张图片后重试", e);
                return;
            }
            doubaoVisionClient.identifyAnimeWithUserWork(base64Image, gpsLatLng, animeName, new DoubaoVisionClient.Callback() {
                @Override
                public void onSuccess(DoubaoVisionClient.RecognitionResponse response) {
                    cacheDoubaoUsage(response);
                    handleAnimeRematchSuccess(response == null ? "" : response.businessJson, animeName, searchGeneration);
                }

                @Override
                public void onSuccess(String responseBody) {
                    handleAnimeRematchSuccess(responseBody, animeName, searchGeneration);
                }

                @Override
                public void onFailure(Exception e) {
                    if (isStaleSearch(searchGeneration)) {
                        return;
                    }
                    String errorMessage = e != null && e.getMessage() != null ? e.getMessage() : "未知错误";
                    Log.e(DEBUG_TAG, "Anime rematch failed: " + errorMessage, e);
                    renderError("作品重匹配失败: " + errorMessage, e, false);
                }
            });
        });
    }

    private void handleAnimeRematchSuccess(String responseBody, String userAnimeName, int searchGeneration) {
        final ParsedResult parsedResult;
        try {
            ParsedResult rawResult = parseAssistantReply(responseBody);
            parsedResult = normalizeRematchResult(rawResult, userAnimeName);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Failed to parse anime rematch response", e);
            renderError("未能从作品重匹配结果中提取地点线索", e);
            return;
        }
        Log.d(DEBUG_TAG, "rematch location_name = " + parsedResult.locationName);
        Log.d(DEBUG_TAG, "rematch visual_keywords = " + parsedResult.visualKeywords);
        Log.d(DEBUG_TAG, "rematch spot_search_keywords = " + parsedResult.spotSearchKeywords);
        runSafelyOnUiThread(() -> {
            if (isStaleSearch(searchGeneration)) {
                return;
            }
            lastParsedResult = parsedResult;
            prepareAnimeRoute(parsedResult, "ANIME");
            currentCandidateNames = new ArrayList<>(parsedResult.animeNames);
            currentCandidateIndex = 0;
            currentCandidateLocation = parsedResult.locationName;
            currentCandidateDesc = parsedResult.summary;
            currentVisualKeywords = new ArrayList<>(parsedResult.visualKeywords);
            currentSpotSearchKeywords = new ArrayList<>(parsedResult.spotSearchKeywords);
            currentTriedSubjectIds = new HashSet<>();
            updateNextOptionButtonState();
            trySearchNextCandidate(searchGeneration);
        });
    }

    private ParsedResult normalizeRematchResult(ParsedResult parsedResult, String userAnimeName) {
        List<String> animeNames = new ArrayList<>();
        if (!isBlank(userAnimeName)) {
            animeNames.add(userAnimeName.trim());
        }
        if (parsedResult != null && parsedResult.animeNames != null) {
            for (String name : parsedResult.animeNames) {
                if (!isBlank(name) && !animeNames.contains(name)) {
                    animeNames.add(name);
                }
            }
        }
        return new ParsedResult(
                animeNames,
                chooseFirstNonBlank(userAnimeName, parsedResult != null ? parsedResult.animeTitle : ""),
                parsedResult != null ? parsedResult.locationName : "",
                parsedResult != null ? parsedResult.summary : "",
                false,
                parsedResult != null ? parsedResult.visualKeywords : null,
                parsedResult != null ? parsedResult.spotSearchKeywords : null,
                parsedResult != null ? parsedResult.confidence : -1,
                parsedResult != null ? parsedResult.reason : ""
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
        File imageDir = getPrivateCaptureDirectory();
        pendingCameraFile = File.createTempFile("camera_", ".jpg", imageDir);
        return FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                pendingCameraFile
        );
    }

    private File getPrivateCaptureDirectory() throws IOException {
        File externalPicturesDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (externalPicturesDir != null) {
            File privateImageDir = new File(externalPicturesDir, PRIVATE_CAPTURE_DIR_NAME);
            if (ensureDirectoryReady(privateImageDir)) {
                ensureNoMediaFile(privateImageDir);
                return privateImageDir;
            }
            Log.w(TAG, "Unable to prepare app-specific external image directory, falling back to cache");
        }

        File cacheImageDir = new File(getCacheDir(), CACHE_CAPTURE_DIR_NAME);
        if (ensureDirectoryReady(cacheImageDir)) {
            ensureNoMediaFile(cacheImageDir);
            return cacheImageDir;
        }
        throw new IOException("Unable to create private image directory");
    }

    private File getPrivateDiaryImageDirectory() throws IOException {
        File externalPicturesDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (externalPicturesDir != null) {
            File privateDiaryDir = new File(externalPicturesDir, PRIVATE_DIARY_DIR_NAME);
            if (ensureDirectoryReady(privateDiaryDir)) {
                ensureNoMediaFile(privateDiaryDir);
                return privateDiaryDir;
            }
            Log.w(TAG, "Unable to prepare app-specific diary image directory, falling back to internal files");
        }

        File internalDiaryDir = new File(getFilesDir(), PRIVATE_DIARY_DIR_NAME);
        if (ensureDirectoryReady(internalDiaryDir)) {
            ensureNoMediaFile(internalDiaryDir);
            return internalDiaryDir;
        }
        throw new IOException("Unable to create diary image directory");
    }

    @Nullable
    private File resolveCapturedImageFile(String fileName) {
        if (isBlank(fileName)) {
            return null;
        }
        File externalPicturesDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (externalPicturesDir != null) {
            File privateImageFile = new File(new File(externalPicturesDir, PRIVATE_CAPTURE_DIR_NAME), fileName);
            if (privateImageFile.exists()) {
                return privateImageFile;
            }
        }
        File cacheImageFile = new File(new File(getCacheDir(), CACHE_CAPTURE_DIR_NAME), fileName);
        if (cacheImageFile.exists()) {
            return cacheImageFile;
        }
        return cacheImageFile;
    }

    private boolean ensureDirectoryReady(File directory) {
        return directory.exists() ? directory.isDirectory() : directory.mkdirs();
    }

    private void ensureNoMediaFile(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return;
        }
        File noMediaFile = new File(directory, ".nomedia");
        if (noMediaFile.exists()) {
            return;
        }
        try {
            if (!noMediaFile.createNewFile()) {
                Log.w(TAG, "Unable to create .nomedia in " + directory.getAbsolutePath());
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to create .nomedia in " + directory.getAbsolutePath(), e);
        }
    }

    private String createPersistentDiaryImageCopy(String sourceUriString, long timestamp) {
        if (isBlank(sourceUriString)) {
            return "";
        }
        Uri sourceUri;
        try {
            sourceUri = Uri.parse(sourceUriString);
        } catch (Exception e) {
            Log.w(TAG, "Invalid diary image uri, keeping original value");
            return sourceUriString;
        }

        try {
            File diaryDir = getPrivateDiaryImageDirectory();
            File targetFile = new File(
                    diaryDir,
                    "diary_" + timestamp + "_" + Math.abs(sourceUriString.hashCode()) + ".jpg"
            );
            try (InputStream inputStream = getContentResolver().openInputStream(sourceUri);
                 FileOutputStream outputStream = new FileOutputStream(targetFile)) {
                if (inputStream == null) {
                    Log.w(TAG, "Unable to open source image for diary copy: " + sourceUri.getScheme());
                    return sourceUriString;
                }
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
            Log.d(DEBUG_TAG, "diary image copied to private storage");
            return Uri.fromFile(targetFile).toString();
        } catch (Exception e) {
            Log.w(TAG, "Unable to copy diary image, keeping original uri", e);
            return sourceUriString;
        }
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
        clearCurrentResultSnapshot();
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
                .fitCenter()
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
        lastDoubaoUsageStats = null;
        clearCurrentResultSnapshot();
        clearCurrentCandidateState();
        clearLocationRoutingState();
        int searchGeneration = beginNewSearchGeneration();
        IdentifyMode identifyModeForRequest = currentIdentifyMode;
        Log.d(DEBUG_TAG, "currentIdentifyMode=" + identifyModeForRequest);
        Log.d(DEBUG_TAG, "requestDoubaoMode=" + toDoubaoMode(identifyModeForRequest));
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

            doubaoVisionClient.identifyLocation(
                    base64Image,
                    gpsLatLng,
                    toDoubaoMode(identifyModeForRequest),
                    new DoubaoVisionClient.Callback() {
                @Override
                public void onSuccess(DoubaoVisionClient.RecognitionResponse response) {
                    cacheDoubaoUsage(response);
                    handleDoubaoSuccess(response == null ? "" : response.businessJson, searchGeneration, identifyModeForRequest);
                }

                @Override
                public void onSuccess(String responseBody) {
                    handleDoubaoSuccess(responseBody, searchGeneration, identifyModeForRequest);
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

    private void handleDoubaoSuccess(String responseBody, int searchGeneration, IdentifyMode identifyModeForRequest) {
        final ParsedResult parsedResult;
        try {
            parsedResult = parseAssistantReply(responseBody);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Failed to parse Doubao response", e);
            renderError("未能从识别结果中提取有效的作品信息", e);
            return;
        }
        IdentifyMode effectiveMode = identifyModeForRequest != null ? identifyModeForRequest : IdentifyMode.ANIME;
        Log.d(DEBUG_TAG, "currentIdentifyMode=" + effectiveMode);
        Log.d(DEBUG_TAG, "model result isDomestic=" + parsedResult.isDomestic
                + ", locationName=" + parsedResult.locationName
                + ", animeNames=" + parsedResult.animeNames);
        runSafelyOnUiThread(() -> {
            if (isStaleSearch(searchGeneration)) {
                return;
            }
            requestManagementThemeMatch(parsedResult, searchGeneration);
            requestManagementRecognitionAssist(parsedResult, searchGeneration);
            if (effectiveMode == IdentifyMode.DOMESTIC) {
                ParsedResult domesticResult = copyParsedResultWithRoute(parsedResult, true);
                lastParsedResult = domesticResult;
                prepareDomesticRoute(domesticResult, searchGeneration, "DOMESTIC");
                requestLocationGateway(domesticResult, searchGeneration);
                return;
            }
            if (effectiveMode == IdentifyMode.ANIME) {
                ParsedResult animeResult = copyParsedResultWithRoute(parsedResult, false);
                lastParsedResult = animeResult;
                prepareAnimeRoute(animeResult, "ANIME");
                Log.d(DEBUG_TAG, "anime initial candidates = " + animeResult.animeNames);
                if (animeResult.animeNames == null || animeResult.animeNames.isEmpty()) {
                    renderAnimeModeNoCandidate(animeResult, parsedResult.isDomestic);
                    return;
                }
                renderInitialAnimeCandidates(animeResult);
                return;
            }

            lastParsedResult = parsedResult;
            if (parsedResult.isDomestic) {
                prepareDomesticRoute(parsedResult, searchGeneration, "AUTO_DOMESTIC");
                Log.d(DEBUG_TAG, "auto mode started, route=DOMESTIC");
                Log.d(DEBUG_TAG, "finalRoute=AUTO_DOMESTIC");
                requestLocationGateway(parsedResult, searchGeneration);
            } else {
                ParsedResult animeResult = copyParsedResultWithRoute(parsedResult, false);
                lastParsedResult = animeResult;
                prepareAnimeRoute(animeResult, "AUTO_ANIME");
                Log.d(DEBUG_TAG, "auto mode started, route=ANIME");
                Log.d(DEBUG_TAG, "auto anime candidates = " + animeResult.animeNames);
                if (animeResult.animeNames == null || animeResult.animeNames.isEmpty()) {
                    renderAnimeModeNoCandidate(animeResult, false);
                    return;
                }
                renderInitialAnimeCandidates(animeResult);
                return;
            }
        });
    }

    private void prepareDomesticRoute(ParsedResult parsedResult, int searchGeneration, String finalRoute) {
        Log.d(DEBUG_TAG, "finalRoute=" + finalRoute);
        cardResult.setVisibility(View.VISIBLE);
        switchResultMode(ResultMode.DOMESTIC);
        startSingleDeviceLocation(searchGeneration);
        tvAnimeTitle.setText(chooseFirstNonBlank(parsedResult.locationName, "国内景点待确认"));
        tvLocationName.setVisibility(View.GONE);
    }

    private void prepareAnimeRoute(ParsedResult parsedResult, String finalRoute) {
        Log.d(DEBUG_TAG, "finalRoute=" + finalRoute);
        cardResult.setVisibility(View.VISIBLE);
        switchResultMode(ResultMode.OVERSEAS);
        tvAnimeTitle.setText(parsedResult.animeTitle);
        tvLocationName.setVisibility(View.VISIBLE);
        tvLocationName.setText(chooseFirstNonBlank(parsedResult.locationName, "现实地点待进一步确认"));
        bindLocationMapEntry(parsedResult.locationName);
    }

    private ParsedResult copyParsedResultWithRoute(ParsedResult parsedResult, boolean isDomestic) {
        return new ParsedResult(
                parsedResult != null ? parsedResult.animeNames : null,
                parsedResult != null ? parsedResult.animeTitle : "",
                parsedResult != null ? parsedResult.locationName : "",
                parsedResult != null ? parsedResult.summary : "",
                isDomestic,
                parsedResult != null ? parsedResult.visualKeywords : null,
                parsedResult != null ? parsedResult.spotSearchKeywords : null,
                parsedResult != null ? parsedResult.confidence : -1,
                parsedResult != null ? parsedResult.reason : ""
        );
    }

    private String toDoubaoMode(IdentifyMode identifyMode) {
        if (identifyMode == IdentifyMode.DOMESTIC) {
            return "domestic";
        }
        if (identifyMode == IdentifyMode.ANIME) {
            return "anime";
        }
        return "auto";
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

    private void renderAnimeModeNoCandidate(ParsedResult parsedResult, boolean modelSuggestedDomestic) {
        runSafelyOnUiThread(() -> {
            updateLoadingState(false);
            switchResultMode(ResultMode.OVERSEAS);
            clearAnimeCandidateViews();
            allowManualAnimeRematch = true;
            updateManualAnimeRematchVisibility();
            cardResult.setVisibility(View.VISIBLE);
            chipResultState.setText("需要补充作品名");
            chipConfidence.setText(buildResultQualityLabel(parsedResult, "动漫巡礼模式"));
            tvAnimeTitle.setText("未识别到明确作品");
            String locationDisplayName = chooseFirstNonBlank(parsedResult.locationName, "地点待确认");
            tvLocationName.setVisibility(View.VISIBLE);
            tvLocationName.setText(locationDisplayName);
            bindLocationMapEntry(locationDisplayName);
            tvResultSummary.setText("未识别到明确作品，可输入作品名或切换自动判断");
            if (tvDesc != null) {
                tvDesc.setVisibility(View.VISIBLE);
                String routeHint = modelSuggestedDomestic
                        ? "模型倾向国内景点；当前保持动漫巡礼模式，如需景点识别请切换国内旅行模式。"
                        : "当前保留为动漫巡礼线索，不自动切换到国内旅行。";
                tvDesc.setText(joinLines(
                        isBlank(parsedResult.summary) ? routeHint : parsedResult.summary,
                        routeHint,
                        buildResultGuidance(parsedResult, false, false)
                ));
            }
            tvReferenceLabel.setVisibility(View.GONE);
            if (ivResultReference != null) {
                ivResultReference.setVisibility(View.GONE);
            }
            updateCurrentResultSnapshot(
                    "未识别到明确作品",
                    locationDisplayName,
                    chooseFirstNonBlank(parsedResult.summary, "未识别到明确作品，可输入作品名或切换自动判断"),
                    null
            );
            updateNextOptionButtonState();
            updateNavigateButtonState();
        });
    }

    private void renderInitialAnimeCandidates(ParsedResult parsedResult) {
        runSafelyOnUiThread(() -> {
            updateLoadingState(false);
            switchResultMode(ResultMode.OVERSEAS);
            cardResult.setVisibility(View.VISIBLE);
            allowManualAnimeRematch = true;
            updateManualAnimeRematchVisibility();
            chipResultState.setText("作品候选");
            chipConfidence.setText(buildResultQualityLabel(parsedResult, "等待确认"));
            tvAnimeTitle.setText("请选择作品后重新匹配图片");
            tvLocationName.setVisibility(View.VISIBLE);
            tvLocationName.setText(chooseFirstNonBlank(parsedResult.locationName, "地点线索待确认"));
            tvResultSummary.setText(chooseFirstNonBlank(
                    parsedResult.summary,
                    "AI 已给出可能作品，请选择一个作品，系统会带着当前图片再次识别巡礼地点。"
            ));
            if (tvDesc != null) {
                tvDesc.setVisibility(View.VISIBLE);
                tvDesc.setText(joinLines(
                        "选择作品后会重新调用 AI，不会只按作品名直接固定地点。",
                        buildResultGuidance(parsedResult, false, false)
                ));
            }
            tvReferenceLabel.setVisibility(View.GONE);
            if (ivResultReference != null) {
                ivResultReference.setVisibility(View.GONE);
            }
            renderAnimeCandidateList(parsedResult.animeNames);
            clearSpotCandidateViews();
            updateCurrentResultSnapshot(
                    parsedResult.animeTitle,
                    parsedResult.locationName,
                    parsedResult.summary,
                    null
            );
            updateNextOptionButtonState();
            updateNavigateButtonState();
        });
    }

    private void renderAnimeCandidateList(List<String> animeNames) {
        if (layoutAnimeCandidateList == null) {
            return;
        }
        layoutAnimeCandidateList.removeAllViews();
        if (animeNames == null || animeNames.isEmpty()) {
            return;
        }
        TextView titleView = createCandidateText("AI 作品候选", 15, true);
        layoutAnimeCandidateList.addView(titleView);
        for (String animeName : animeNames) {
            if (isBlank(animeName)) {
                continue;
            }
            MaterialCardView cardView = createCandidateCard();
            LinearLayout content = createCandidateCardContent();
            TextView nameView = createCandidateText(animeName, 15, true);
            MaterialButton actionButton = createCandidateActionButton("使用此作品匹配");
            actionButton.setOnClickListener(view -> startAnimeRematchWithWork(animeName, true));
            content.addView(nameView);
            content.addView(actionButton);
            cardView.addView(content);
            layoutAnimeCandidateList.addView(cardView);
        }
    }

    private void clearAnimeCandidateViews() {
        if (layoutAnimeCandidateList != null) {
            layoutAnimeCandidateList.removeAllViews();
        }
    }

    private void clearSpotCandidateViews() {
        if (layoutSpotCandidateList != null) {
            layoutSpotCandidateList.removeAllViews();
            layoutSpotCandidateList.setVisibility(View.GONE);
        }
        hasSpotCandidateOptions = false;
        spotCandidateListExpanded = false;
        currentSpotCandidateCount = 0;
        updateNextStepHint();
    }

    private void setSpotCandidateListExpanded(boolean expanded) {
        spotCandidateListExpanded = expanded && hasSpotCandidateOptions;
        if (layoutSpotCandidateList != null) {
            layoutSpotCandidateList.setVisibility(spotCandidateListExpanded ? View.VISIBLE : View.GONE);
        }
        Log.d(DEBUG_TAG, "spotCandidateListExpanded=" + spotCandidateListExpanded);
        updateNextOptionButtonState();
        updateNextStepHint();
    }

    private void updateManualAnimeRematchVisibility() {
        if (layoutAnimeRematch == null) {
            return;
        }
        boolean showAnimeRematch = currentResultMode == ResultMode.OVERSEAS
                && allowManualAnimeRematch
                && (currentIdentifyMode == IdentifyMode.ANIME
                || (currentIdentifyMode == IdentifyMode.AUTO
                && lastParsedResult != null
                && !lastParsedResult.isDomestic));
        layoutAnimeRematch.setVisibility(showAnimeRematch ? View.VISIBLE : View.GONE);
    }

    private void renderDomesticTravelResult(ParsedResult parsedResult, boolean fromGateway) {
        runSafelyOnUiThread(() -> {
            updateLoadingState(false);
            switchResultMode(ResultMode.DOMESTIC);
            cardResult.setVisibility(View.VISIBLE);
            chipResultState.setText("风景旅行");
            chipConfidence.setText(buildResultQualityLabel(parsedResult, fromGateway ? "腾讯双引擎" : "AI 景点识别"));
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
            setConfirmedPilgrimageSelection(
                    locationDisplayName,
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
                        if (currentIdentifyMode == IdentifyMode.ANIME) {
                            renderNoSpotCandidates(lastParsedResult != null ? lastParsedResult : new ParsedResult(
                                    currentCandidateNames,
                                    currentName,
                                    currentCandidateLocation,
                                    currentCandidateDesc,
                                    false,
                                    currentVisualKeywords,
                                    currentSpotSearchKeywords
                            ));
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
                    if (currentIdentifyMode == IdentifyMode.ANIME) {
                        updateLoadingState(false);
                        renderNoSpotCandidates(lastParsedResult != null ? lastParsedResult : new ParsedResult(
                                currentCandidateNames,
                                currentName,
                                currentCandidateLocation,
                                currentCandidateDesc,
                                false,
                                currentVisualKeywords,
                                currentSpotSearchKeywords
                        ));
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
                    requestBangumiSubjectInfo(parsedResult, bangumiLiteResponse, subjectId, searchGeneration);
                });
            }

            @Override
            public void onFailure(Exception e) {
                runSafelyOnUiThread(() -> {
                    if (isStaleSearch(searchGeneration)) {
                        return;
                    }
                    Log.e(TAG, "Failed to get bangumi lite", e);
                    if (currentIdentifyMode == IdentifyMode.ANIME) {
                        updateLoadingState(false);
                        renderNoSpotCandidates(parsedResult);
                        return;
                    }
                    renderPartialSuccess(parsedResult.animeTitle, parsedResult.locationName, parsedResult.summary);
                });
            }
        });
    }

    private void requestBangumiSubjectInfo(
            ParsedResult parsedResult,
            AnitabiApiClient.BangumiLiteResponse bangumiLiteResponse,
            int subjectId,
            int searchGeneration
    ) {
        anitabiApiClient.getBangumiSubjectInfo(subjectId, new AnitabiApiClient.ApiCallback<AnitabiApiClient.BangumiSubjectInfo>() {
            @Override
            public void onSuccess(AnitabiApiClient.BangumiSubjectInfo subjectInfo) {
                runSafelyOnUiThread(() -> {
                    if (isStaleSearch(searchGeneration)) {
                        return;
                    }
                    bangumiLiteResponse.applySubjectInfo(subjectInfo);
                    Log.d(DEBUG_TAG, "bangumi subject info loaded for subjectId=" + subjectId);
                    requestPointsDetail(parsedResult, bangumiLiteResponse, subjectId, searchGeneration);
                });
            }

            @Override
            public void onFailure(Exception e) {
                runSafelyOnUiThread(() -> {
                    if (isStaleSearch(searchGeneration)) {
                        return;
                    }
                    Log.w(DEBUG_TAG, "Bangumi subject info unavailable, continue with anitabi lite", e);
                    requestPointsDetail(parsedResult, bangumiLiteResponse, subjectId, searchGeneration);
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
                    Log.d(DEBUG_TAG, "anitabi points count = " + pointDetails.size());
                    if (currentIdentifyMode == IdentifyMode.ANIME) {
                        renderSpotCandidates(parsedResult, bangumiLiteResponse, pointDetails);
                    } else {
                        renderResult(parsedResult, bangumiLiteResponse, pointDetails);
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                runSafelyOnUiThread(() -> {
                    if (isStaleSearch(searchGeneration)) {
                        return;
                    }
                    Log.e(TAG, "Failed to get points detail", e);
                    if (currentIdentifyMode == IdentifyMode.ANIME) {
                        updateLoadingState(false);
                        renderNoSpotCandidates(parsedResult);
                        return;
                    }
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
            setConfirmedPilgrimageSelection(
                    animeName,
                    locationDisplayName,
                    locationDisplayName,
                    description,
                    imageUrl
            );
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
            setConfirmedPilgrimageSelection(
                    animeName,
                    safeLocationName,
                    safeLocationName,
                    description,
                    null
            );
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
            if (currentIdentifyMode == IdentifyMode.ANIME) {
                animeDisplayName = chooseFirstNonBlank(getUserSelectedAnimeName(parsedResult), animeDisplayName);
            }
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
            setConfirmedPilgrimageSelection(
                    animeDisplayName,
                    chooseFirstNonBlank(firstPoint.getName(), locationDisplayName),
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

    private void renderSpotCandidates(
            ParsedResult parsedResult,
            AnitabiApiClient.BangumiLiteResponse bangumiLiteResponse,
            List<AnitabiApiClient.PointDetail> pointDetails
    ) {
        List<SpotCandidate> sortedCandidates = buildSortedSpotCandidates(parsedResult, pointDetails);
        Log.d(DEBUG_TAG, "sorted spot candidates count = " + sortedCandidates.size());
        runSafelyOnUiThread(() -> {
            updateLoadingState(false);
            switchResultMode(ResultMode.OVERSEAS);
            cardResult.setVisibility(View.VISIBLE);
            hasSpotCandidateOptions = sortedCandidates != null && !sortedCandidates.isEmpty();
            currentSpotCandidateCount = hasSpotCandidateOptions ? sortedCandidates.size() : 0;
            spotCandidateListExpanded = false;
            allowManualAnimeRematch = !hasSpotCandidateOptions || isLowConfidenceResult(parsedResult);
            updateManualAnimeRematchVisibility();
            chipResultState.setText("圣地巡礼");
            chipConfidence.setText(buildResultQualityLabel(parsedResult, "AI 已匹配"));
            String animeDisplayName = chooseFirstNonBlank(
                    currentIdentifyMode == IdentifyMode.ANIME ? getUserSelectedAnimeName(parsedResult) : null,
                    bangumiLiteResponse.getCn(),
                    bangumiLiteResponse.getTitle(),
                    parsedResult.animeTitle,
                    "AI 识别结果"
            );
            String locationDisplayName = chooseFirstNonBlank(parsedResult.locationName, "地点线索待确认");
            String descriptionText = chooseFirstNonBlank(
                    parsedResult.summary,
                    "AI 已结合指定作品和当前图片重新提取地点线索，请从下方其他可能的巡礼地点中确认。"
            );
            descriptionText = appendWorkIntroToDescription(descriptionText, bangumiLiteResponse);
            tvAnimeTitle.setText(animeDisplayName);
            tvLocationName.setVisibility(View.VISIBLE);
            tvLocationName.setText(locationDisplayName);
            tvResultSummary.setText(descriptionText);
            if (tvDesc != null) {
                tvDesc.setVisibility(View.VISIBLE);
                tvDesc.setText(joinLines(
                        "基于当前图片与作品线索生成",
                        buildRematchKeywordSummary(parsedResult),
                        buildResultGuidance(parsedResult, hasSpotCandidateOptions, false)
                ));
            }
            tvReferenceLabel.setVisibility(View.GONE);
            if (ivResultReference != null) {
                ivResultReference.setVisibility(View.GONE);
            }
            updateCurrentResultSnapshot(animeDisplayName, locationDisplayName, descriptionText, null);
            setConfirmedPilgrimageSelection(
                    animeDisplayName,
                    locationDisplayName,
                    locationDisplayName,
                    descriptionText,
                    null
            );
            if (hasSpotCandidateOptions) {
                renderSpotCandidateList(parsedResult, bangumiLiteResponse, sortedCandidates, pointDetails.size());
                setSpotCandidateListExpanded(false);
            } else {
                clearSpotCandidateViews();
            }
            updateNextOptionButtonState();
            updateNavigateButtonState();
            updateNextStepHint();
        });
    }

    private void renderNoSpotCandidates(ParsedResult parsedResult) {
        runSafelyOnUiThread(() -> {
            updateLoadingState(false);
            switchResultMode(ResultMode.OVERSEAS);
            cardResult.setVisibility(View.VISIBLE);
            allowManualAnimeRematch = true;
            updateManualAnimeRematchVisibility();
            chipResultState.setText("未找到可靠巡礼点");
            chipConfidence.setText(buildResultQualityLabel(parsedResult, "需要补充作品名"));
            tvAnimeTitle.setText(chooseFirstNonBlank(parsedResult.animeTitle, getCurrentCandidateName("作品待确认")));
            tvLocationName.setVisibility(View.VISIBLE);
            tvLocationName.setText(chooseFirstNonBlank(parsedResult.locationName, "地点待确认"));
            tvResultSummary.setText("该作品暂未找到巡礼地点，可尝试更换作品名或补充更具体地点。");
            if (tvDesc != null) {
                tvDesc.setVisibility(View.VISIBLE);
                tvDesc.setText(joinLines(
                        buildRematchKeywordSummary(parsedResult),
                        buildResultGuidance(parsedResult, false, true)
                ));
            }
            clearSpotCandidateViews();
            updateNextOptionButtonState();
            updateNavigateButtonState();
            updateNextStepHint();
        });
    }

    private void renderSpotCandidateList(
            ParsedResult parsedResult,
            AnitabiApiClient.BangumiLiteResponse bangumiLiteResponse,
            List<SpotCandidate> spotCandidates,
            int totalPointCount
    ) {
        if (layoutSpotCandidateList == null) {
            return;
        }
        layoutSpotCandidateList.removeAllViews();
        TextView titleView = createCandidateText("其他可能的巡礼地点", 15, true);
        layoutSpotCandidateList.addView(titleView);
        TextView hintView = createCandidateText("如果当前结果不准确，可以从下面选择更匹配的地点。", 13, false);
        layoutSpotCandidateList.addView(hintView);
        if (spotCandidates == null || spotCandidates.isEmpty()) {
            TextView emptyView = createCandidateText("该作品暂未找到巡礼地点，可尝试更换作品名或补充更具体地点。", 14, false);
            layoutSpotCandidateList.addView(emptyView);
            return;
        }
        int maxCount = Math.min(5, spotCandidates.size());
        for (int i = 0; i < maxCount; i++) {
            SpotCandidate candidate = spotCandidates.get(i);
            AnitabiApiClient.PointDetail point = candidate.pointDetail;
            if (point == null) {
                continue;
            }
            MaterialCardView cardView = createCandidateCard();
            LinearLayout content = createCandidateCardContent();
            content.addView(createCandidateText(buildSpotMatchLabel(candidate.score), 13, true));
            content.addView(createCandidateText(chooseFirstNonBlank(point.getName(), "地点名称待确认"), 15, true));

            List<String> lines = new ArrayList<>();
            int episode = parseIntSafely(point.getEp());
            if (episode > 0) {
                lines.add("集数/场景：第" + episode + "集");
            } else {
                lines.add("集数/场景：待确认");
            }
            lines.add("参考图：" + (!isBlank(point.getImage()) ? "有" : "无"));
            lines.add(candidate.reason);
            content.addView(createCandidateText(joinLines(lines), 13, false));

            MaterialButton actionButton = createCandidateActionButton("选择此地点");
            actionButton.setOnClickListener(view -> {
                Log.d(DEBUG_TAG, "selected spot = " + chooseFirstNonBlank(point.getName(), point.getId()));
                String selectedAnimeName = chooseFirstNonBlank(getUserSelectedAnimeName(parsedResult), parsedResult.animeTitle);
                String selectedSpotName = chooseFirstNonBlank(point.getName(), point.getId());
                setConfirmedPilgrimageSelection(
                        selectedAnimeName,
                        selectedSpotName,
                        selectedSpotName,
                        chooseFirstNonBlank(parsedResult.summary, candidate.reason),
                        AnitabiApiClient.getHighResImageUrl(point.getImage())
                );
                renderSelectedSpotResult(parsedResult, bangumiLiteResponse, point, totalPointCount > 1);
            });
            content.addView(actionButton);
            cardView.addView(content);
            layoutSpotCandidateList.addView(cardView);
        }
    }

    private void renderSelectedSpotResult(
            ParsedResult parsedResult,
            AnitabiApiClient.BangumiLiteResponse bangumiLiteResponse,
            AnitabiApiClient.PointDetail selectedPoint,
            boolean hasMultiplePoints
    ) {
        runSafelyOnUiThread(() -> {
            updateLoadingState(false);
            switchResultMode(ResultMode.OVERSEAS);
            clearAnimeCandidateViews();
            allowManualAnimeRematch = false;
            updateManualAnimeRematchVisibility();
            cardResult.setVisibility(View.VISIBLE);

            String animeDisplayName = chooseFirstNonBlank(
                    bangumiLiteResponse.getCn(),
                    bangumiLiteResponse.getTitle(),
                    parsedResult.animeTitle,
                    "AI 识别结果"
            );
            if (currentIdentifyMode == IdentifyMode.ANIME) {
                animeDisplayName = chooseFirstNonBlank(getUserSelectedAnimeName(parsedResult), animeDisplayName);
            }
            String locationDisplayName = chooseFirstNonBlank(
                    selectedPoint.getName(),
                    parsedResult.locationName,
                    bangumiLiteResponse.getCity(),
                    "巡礼地点待进一步确认"
            );
            String descriptionText = buildResultText(parsedResult, bangumiLiteResponse, selectedPoint, hasMultiplePoints);
            String pointImageUrl = AnitabiApiClient.getHighResImageUrl(selectedPoint.getImage());

            chipResultState.setText("圣地巡礼");
            chipConfidence.setText("已选择地点");
            tvAnimeTitle.setText(animeDisplayName);
            tvLocationName.setVisibility(View.VISIBLE);
            bindLocationMapEntry(locationDisplayName, locationDisplayName + " \uD83D\uDCCD(点击导航)");
            tvResultSummary.setText(descriptionText);
            if (tvDesc != null) {
                tvDesc.setVisibility(View.VISIBLE);
                tvDesc.setText(joinLines(
                        "基于当前图片与作品线索生成",
                        buildNavigationHint(buildSupplementText(parsedResult, bangumiLiteResponse, selectedPoint, hasMultiplePoints))
                ));
            }

            currentCandidateLocation = locationDisplayName;
            currentCandidateDesc = descriptionText;
            updateCurrentResultSnapshot(animeDisplayName, locationDisplayName, descriptionText, pointImageUrl);
            setConfirmedPilgrimageSelection(
                    animeDisplayName,
                    chooseFirstNonBlank(selectedPoint.getName(), selectedPoint.getId()),
                    locationDisplayName,
                    descriptionText,
                    pointImageUrl
            );
            updateNextOptionButtonState();
            updateNavigateButtonState();
            updateNextStepHint();
            scrollToView(cardResult);

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

    private List<SpotCandidate> buildSortedSpotCandidates(
            ParsedResult parsedResult,
            List<AnitabiApiClient.PointDetail> pointDetails
    ) {
        List<SpotCandidate> candidates = new ArrayList<>();
        if (pointDetails == null) {
            return candidates;
        }
        for (AnitabiApiClient.PointDetail pointDetail : pointDetails) {
            if (pointDetail == null) {
                continue;
            }
            int score = scoreSpotCandidate(parsedResult, pointDetail);
            String reason = buildSpotCandidateReason(parsedResult, pointDetail);
            candidates.add(new SpotCandidate(pointDetail, score, reason));
        }
        candidates.sort((left, right) -> Integer.compare(right.score, left.score));
        return candidates;
    }

    private int scoreSpotCandidate(ParsedResult parsedResult, AnitabiApiClient.PointDetail pointDetail) {
        int score = 0;
        List<String> keywords = collectSpotMatchKeywords(parsedResult);
        for (String keyword : keywords) {
            score += scoreLocationMatch(keyword, pointDetail.getName(), pointDetail.getOrigin());
        }
        if (!isBlank(pointDetail.getImage())) {
            score += 3;
        }
        return score;
    }

    private String buildSpotCandidateReason(ParsedResult parsedResult, AnitabiApiClient.PointDetail pointDetail) {
        List<String> matchedLocationTerms = new ArrayList<>();
        List<String> matchedVisualTerms = new ArrayList<>();
        if (parsedResult != null) {
            addMatchedTerms(matchedLocationTerms, pointDetail, parsedResult.locationName);
            addMatchedTerms(matchedLocationTerms, pointDetail, parsedResult.spotSearchKeywords);
            addMatchedTerms(matchedVisualTerms, pointDetail, parsedResult.visualKeywords);
        }
        addMatchedTerms(matchedVisualTerms, pointDetail, currentVisualKeywords);
        addMatchedTerms(matchedLocationTerms, pointDetail, currentSpotSearchKeywords);

        List<String> lines = new ArrayList<>();
        if (!matchedLocationTerms.isEmpty()) {
            lines.add("匹配原因：命中地点线索：" + joinWithSeparator(limitStrings(matchedLocationTerms, 3), " / "));
        }
        if (!matchedVisualTerms.isEmpty()) {
            lines.add("命中视觉关键词：" + joinWithSeparator(limitStrings(matchedVisualTerms, 3), " / "));
        } else if (parsedResult != null && parsedResult.visualKeywords != null && !parsedResult.visualKeywords.isEmpty()) {
            lines.add("参考视觉关键词：" + joinWithSeparator(limitStrings(parsedResult.visualKeywords, 3), " / "));
        }
        lines.add("来源：Anitabi 点位 + AI 图片线索排序");
        if (!isBlank(pointDetail.getImage())) {
            lines.add("参考图：可用于人工核对");
        }
        return joinLines(lines);
    }

    private void addMatchedTerms(List<String> target, AnitabiApiClient.PointDetail pointDetail, String keyword) {
        if (target == null || pointDetail == null || isBlank(keyword)) {
            return;
        }
        if (scoreLocationMatch(keyword, pointDetail.getName(), pointDetail.getOrigin()) > 0) {
            addKeywordIfPresent(target, keyword);
        }
    }

    private void addMatchedTerms(List<String> target, AnitabiApiClient.PointDetail pointDetail, List<String> keywords) {
        if (target == null || pointDetail == null || keywords == null) {
            return;
        }
        for (String keyword : keywords) {
            if (target.size() >= 3) {
                return;
            }
            addMatchedTerms(target, pointDetail, keyword);
        }
    }

    private List<String> limitStrings(List<String> values, int maxCount) {
        List<String> limited = new ArrayList<>();
        if (values == null || maxCount <= 0) {
            return limited;
        }
        for (String value : values) {
            if (limited.size() >= maxCount) {
                break;
            }
            addKeywordIfPresent(limited, value);
        }
        return limited;
    }

    private List<String> collectSpotMatchKeywords(ParsedResult parsedResult) {
        List<String> keywords = new ArrayList<>();
        if (parsedResult != null) {
            addKeywordIfPresent(keywords, parsedResult.locationName);
            addKeywordsIfPresent(keywords, parsedResult.visualKeywords);
            addKeywordsIfPresent(keywords, parsedResult.spotSearchKeywords);
        }
        addKeywordsIfPresent(keywords, currentVisualKeywords);
        addKeywordsIfPresent(keywords, currentSpotSearchKeywords);
        return keywords;
    }

    private void addKeywordsIfPresent(List<String> target, @Nullable List<String> values) {
        if (target == null || values == null) {
            return;
        }
        for (String value : values) {
            addKeywordIfPresent(target, value);
        }
    }

    private void addKeywordIfPresent(List<String> target, String value) {
        if (target == null || isBlank(value)) {
            return;
        }
        String trimmed = value.trim();
        if (!target.contains(trimmed)) {
            target.add(trimmed);
        }
    }

    private String buildSpotMatchLabel(int score) {
        if (score >= 12) {
            return "匹配强";
        }
        if (score >= 4) {
            return "匹配中";
        }
        return "待人工确认";
    }

    private String buildRematchKeywordSummary(ParsedResult parsedResult) {
        List<String> lines = new ArrayList<>();
        if (parsedResult != null && parsedResult.visualKeywords != null && !parsedResult.visualKeywords.isEmpty()) {
            lines.add("视觉关键词：" + joinWithSeparator(parsedResult.visualKeywords, " / "));
        }
        if (parsedResult != null && parsedResult.spotSearchKeywords != null && !parsedResult.spotSearchKeywords.isEmpty()) {
            lines.add("搜索关键词：" + joinWithSeparator(parsedResult.spotSearchKeywords, " / "));
        }
        if (lines.isEmpty()) {
            lines.add("已基于指定作品和当前图片重新提取巡礼地点线索。");
        }
        return joinLines(lines);
    }

    private MaterialCardView createCandidateCard() {
        MaterialCardView cardView = new MaterialCardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(8);
        cardView.setLayoutParams(params);
        if (currentResultMode == ResultMode.OVERSEAS) {
            cardView.setCardBackgroundColor(Color.parseColor("#F8FAFF"));
            cardView.setStrokeColor(Color.parseColor("#D6DEFF"));
        } else if (currentResultMode == ResultMode.DOMESTIC) {
            cardView.setCardBackgroundColor(Color.parseColor("#FFFDF8"));
            cardView.setStrokeColor(Color.parseColor("#E8DDCC"));
        } else {
            cardView.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_primary));
            cardView.setStrokeColor(ContextCompat.getColor(this, R.color.card_stroke));
        }
        cardView.setStrokeWidth(dp(1));
        cardView.setRadius(dp(12));
        cardView.setCardElevation(0f);
        return cardView;
    }

    private LinearLayout createCandidateCardContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(12), dp(12), dp(12));
        return content;
    }

    private TextView createCandidateText(String text, int textSizeSp, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextColor(ContextCompat.getColor(this, bold ? R.color.text_primary : R.color.text_secondary));
        textView.setTextSize(textSizeSp);
        textView.setIncludeFontPadding(false);
        textView.setLineSpacing(dp(2), 1.0f);
        if (bold) {
            textView.setTypeface(textView.getTypeface(), android.graphics.Typeface.BOLD);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = bold ? dp(2) : dp(8);
        textView.setLayoutParams(params);
        return textView;
    }

    private MaterialButton createCandidateActionButton(String text) {
        MaterialButton button = new MaterialButton(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        params.topMargin = dp(10);
        button.setLayoutParams(params);
        button.setText(text);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        button.setBackgroundTintList(ColorStateList.valueOf(getResultModePrimaryColor()));
        button.setCornerRadius(dp(16));
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
            if (currentIdentifyMode == IdentifyMode.ANIME) {
                animeDisplayName = chooseFirstNonBlank(getUserSelectedAnimeName(parsedResult), animeDisplayName);
            }
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
            String workIntroText = buildWorkIntroText(bangumiLiteResponse);
            if (!isBlank(workIntroText)) {
                builder.append("\n\n作品介绍\n").append(workIntroText);
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
            setConfirmedPilgrimageSelection(
                    animeDisplayName,
                    locationDisplayName,
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
            updateNextStepHint();
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
        clearAnimeCandidateViews();
        clearSpotCandidateViews();
        updateSaveRecordButtonState();
        updateNavigateButtonState();
        updateNextStepHint();
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
        clearAnimeCandidateViews();
        clearSpotCandidateViews();
        hideNextStepHint();
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
        List<String> visualKeywords = getJsonStringList(businessObject, "visual_keywords");
        List<String> spotSearchKeywords = getJsonStringList(businessObject, "spot_search_keywords");
        double confidence = getJsonDouble(businessObject, "confidence", -1);
        String reason = getJsonString(businessObject, "reason");
        String displayAnimeTitle = animeNames.isEmpty()
                ? "AI 待确认作品"
                : chooseFirstNonBlank(animeNames.toArray(new String[0]));
        return new ParsedResult(
                animeNames,
                displayAnimeTitle,
                locationName,
                description,
                isDomestic,
                visualKeywords,
                spotSearchKeywords,
                confidence,
                reason
        );
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

    private double getJsonDouble(JsonObject jsonObject, String key, double fallback) {
        if (jsonObject == null || key == null || !jsonObject.has(key) || jsonObject.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return jsonObject.get(key).getAsDouble();
        } catch (Exception ignored) {
            return fallback;
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
        tvLocationName.setTextColor(getResultModePrimaryColor());
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

    private void applyResultModeVisualStyle(ResultMode resultMode) {
        if (resultMode == ResultMode.DOMESTIC) {
            applyDomesticResultVisualStyle();
        } else if (resultMode == ResultMode.OVERSEAS) {
            applyAnimeResultVisualStyle();
        } else {
            applyNeutralResultVisualStyle();
        }
    }

    private void applyAnimeResultVisualStyle() {
        int primary = ContextCompat.getColor(this, R.color.brand_primary);
        int title = ContextCompat.getColor(this, R.color.text_primary);
        int secondary = ContextCompat.getColor(this, R.color.text_secondary);
        if (cardResult != null) {
            cardResult.setCardBackgroundColor(Color.parseColor("#F8FAFF"));
            cardResult.setStrokeColor(Color.parseColor("#CAD5FF"));
            cardResult.setStrokeWidth(dp(1));
        }
        if (chipResultState != null) {
            applyChipStyle(chipResultState, Color.parseColor("#E8F7F3"), Color.parseColor("#2F8F77"));
        }
        if (chipConfidence != null) {
            applyChipStyle(chipConfidence, Color.parseColor("#EDF1FF"), primary);
        }
        if (tvAnimeTitle != null) {
            tvAnimeTitle.setTextColor(title);
            tvAnimeTitle.setTextSize(22);
        }
        if (tvLocationName != null) {
            tvLocationName.setTextColor(primary);
        }
        if (layoutNextStepHint != null) {
            layoutNextStepHint.setBackgroundResource(R.drawable.bg_next_step_anime);
        }
        if (tvNextStepHint != null) {
            tvNextStepHint.setTextColor(Color.parseColor("#34406B"));
        }
        if (tvOverseasBadge != null) {
            tvOverseasBadge.setText("圣地巡礼");
        }
        styleResultActionButtons(primary, Color.parseColor("#EDF1FF"), primary);
    }

    private void applyDomesticResultVisualStyle() {
        int domesticPrimary = Color.parseColor("#5E9D79");
        int domesticTitle = Color.parseColor("#315A42");
        int domesticAccent = Color.parseColor("#E07A3F");
        if (cardResult != null) {
            cardResult.setCardBackgroundColor(Color.parseColor("#FFFDF8"));
            cardResult.setStrokeColor(Color.parseColor("#E8DDCC"));
            cardResult.setStrokeWidth(dp(1));
        }
        if (chipResultState != null) {
            applyChipStyle(chipResultState, Color.parseColor("#EAF6EE"), domesticPrimary);
        }
        if (chipConfidence != null) {
            applyChipStyle(chipConfidence, Color.parseColor("#FFF1E6"), domesticAccent);
        }
        if (tvAnimeTitle != null) {
            tvAnimeTitle.setTextColor(domesticTitle);
            tvAnimeTitle.setTextSize(24);
        }
        if (tvLocationName != null) {
            tvLocationName.setTextColor(domesticPrimary);
        }
        if (layoutNextStepHint != null) {
            layoutNextStepHint.setBackgroundResource(R.drawable.bg_next_step_domestic);
        }
        if (tvNextStepHint != null) {
            tvNextStepHint.setTextColor(Color.parseColor("#6B4A25"));
        }
        if (tvDomesticBadge != null) {
            tvDomesticBadge.setText("国内旅行");
        }
        styleResultActionButtons(domesticPrimary, Color.parseColor("#EAF6EE"), domesticPrimary);
    }

    private void applyNeutralResultVisualStyle() {
        int primary = getThemePrimaryColor();
        if (cardResult != null) {
            cardResult.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_primary));
            cardResult.setStrokeColor(ContextCompat.getColor(this, R.color.card_stroke));
            cardResult.setStrokeWidth(dp(1));
        }
        if (chipResultState != null) {
            applyChipStyle(
                    chipResultState,
                    ContextCompat.getColor(this, R.color.chip_match_background),
                    ContextCompat.getColor(this, R.color.accent_match)
            );
        }
        if (chipConfidence != null) {
            applyChipStyle(
                    chipConfidence,
                    ContextCompat.getColor(this, R.color.chip_brand_background),
                    primary
            );
        }
        if (tvAnimeTitle != null) {
            tvAnimeTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            tvAnimeTitle.setTextSize(22);
        }
        if (tvLocationName != null) {
            tvLocationName.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
        if (layoutNextStepHint != null) {
            layoutNextStepHint.setBackgroundResource(R.drawable.bg_next_step_neutral);
        }
        if (tvNextStepHint != null) {
            tvNextStepHint.setTextColor(ContextCompat.getColor(this, R.color.text_body));
        }
        styleResultActionButtons(
                primary,
                ContextCompat.getColor(this, R.color.chip_brand_background),
                primary
        );
    }

    private void applyChipStyle(Chip chip, int backgroundColor, int textColor) {
        chip.setChipBackgroundColor(ColorStateList.valueOf(backgroundColor));
        chip.setTextColor(textColor);
    }

    private void styleResultActionButtons(int primaryColor, int subtleBackgroundColor, int subtleTextColor) {
        if (btnSaveRecord != null) {
            btnSaveRecord.setBackgroundTintList(ColorStateList.valueOf(primaryColor));
        }
        if (btnNextOption != null) {
            btnNextOption.setTextColor(subtleTextColor);
            btnNextOption.setBackgroundTintList(ColorStateList.valueOf(subtleBackgroundColor));
            btnNextOption.setStrokeColor(ColorStateList.valueOf(Color.argb(80, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor))));
        }
    }

    private int getResultModePrimaryColor() {
        if (currentResultMode == ResultMode.DOMESTIC) {
            return Color.parseColor("#5E9D79");
        }
        if (currentResultMode == ResultMode.OVERSEAS) {
            return ContextCompat.getColor(this, R.color.brand_primary);
        }
        return getThemePrimaryColor();
    }

    private void switchResultMode(ResultMode resultMode) {
        currentResultMode = resultMode;
        applyResultModeVisualStyle(resultMode);
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
        updateManualAnimeRematchVisibility();
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

    private String buildResultQualityLabel(@Nullable ParsedResult parsedResult, String normalLabel) {
        if (parsedResult == null) {
            return normalLabel;
        }
        if (isLowConfidenceResult(parsedResult)) {
            return "低置信度，建议确认";
        }
        if (parsedResult.confidence >= 0) {
            return normalLabel + " " + Math.round(parsedResult.confidence * 100) + "%";
        }
        return normalLabel;
    }

    private String buildResultGuidance(
            @Nullable ParsedResult parsedResult,
            boolean hasCandidateSpots,
            boolean noSpotCandidates
    ) {
        if (parsedResult == null) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        if (!isBlank(parsedResult.reason)) {
            lines.add("判断依据：" + parsedResult.reason);
        }
        if (noSpotCandidates) {
            lines.add("未找到可靠巡礼点：可以更换作品名，或补充更具体的地点线索后重新匹配。");
        } else if (isLowConfidenceResult(parsedResult)) {
            if (currentResultMode == ResultMode.OVERSEAS) {
                lines.add(hasCandidateSpots
                        ? "低置信度：建议先点“换一个结果”核对其他候选；没有合适结果时再输入作品名重新匹配。"
                        : "低置信度：建议输入正确作品名，让 AI 结合当前图片重新匹配巡礼地点。");
            } else if (currentIdentifyMode == IdentifyMode.AUTO) {
                lines.add("低置信度：可从侧边栏切换到动漫巡礼或国内旅行后再识别。");
            } else {
                lines.add("低置信度：建议重新上传更清晰图片，或切换更合适的识别模式。");
            }
        }
        return joinLines(lines);
    }

    private boolean isLowConfidenceResult(@Nullable ParsedResult parsedResult) {
        if (parsedResult == null) {
            return false;
        }
        if (parsedResult.confidence >= 0 && parsedResult.confidence < 0.55) {
            return true;
        }
        return containsUncertainText(parsedResult.locationName)
                || containsUncertainText(parsedResult.summary)
                || containsUncertainText(parsedResult.reason);
    }

    private boolean containsUncertainText(String value) {
        if (isBlank(value)) {
            return false;
        }
        String normalized = value.trim();
        return normalized.contains("待确认")
                || normalized.contains("无法确认")
                || normalized.contains("不确定")
                || normalized.equals("地点待确认")
                || normalized.equals("地点线索待确认");
    }

    private void scrollToView(@Nullable View targetView) {
        if (scrollContent == null || targetView == null) {
            return;
        }
        scrollContent.post(() -> {
            Rect rect = new Rect();
            targetView.getDrawingRect(rect);
            scrollContent.offsetDescendantRectToMyCoords(targetView, rect);
            scrollContent.smoothScrollTo(0, Math.max(0, rect.top - dp(12)));
        });
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
        appendManagementSupplementToDescription(lastManagementSupplementText);
    }

    private void setConfirmedPilgrimageSelection(
            String animeName,
            String spotName,
            String locationName,
            String description,
            String referenceImageUrl
    ) {
        confirmedAnimeName = animeName;
        confirmedSpotName = spotName;
        confirmedLocationName = locationName;
        confirmedDescription = description;
        confirmedReferenceUrl = referenceImageUrl;
        confirmedLocalImageUri = selectedImageUri != null ? selectedImageUri.toString() : null;
        hasSavedCurrentRecord = false;
        Log.d(DEBUG_TAG, "confirmedAnimeName=" + confirmedAnimeName);
        Log.d(DEBUG_TAG, "confirmedSpotName=" + confirmedSpotName);
        updateSaveRecordButtonState();
    }

    private void clearConfirmedPilgrimageSelection() {
        confirmedAnimeName = null;
        confirmedSpotName = null;
        confirmedLocationName = null;
        confirmedDescription = null;
        confirmedReferenceUrl = null;
        confirmedLocalImageUri = null;
        updateSaveRecordButtonState();
    }

    private void clearCurrentResultSnapshot() {
        currentAnimeName = null;
        currentLocation = null;
        currentDesc = null;
        currentLocalUri = null;
        currentReferenceUrl = null;
        lastManagementRecognitionId = null;
        lastManagementSupplementText = null;
        clearConfirmedPilgrimageSelection();
        hasSavedCurrentRecord = false;
        updateSaveRecordButtonState();
    }

    private void clearCurrentCandidateState() {
        currentCandidateNames = null;
        currentCandidateIndex = 0;
        currentCandidateLocation = null;
        currentCandidateDesc = null;
        currentVisualKeywords = null;
        currentSpotSearchKeywords = null;
        currentTriedSubjectIds = null;
        allowManualAnimeRematch = false;
        clearConfirmedPilgrimageSelection();
        clearAnimeCandidateViews();
        clearSpotCandidateViews();
        updateManualAnimeRematchVisibility();
        updateNextOptionButtonState();
    }

    private void updateNextOptionButtonState() {
        if (btnNextOption == null) {
            return;
        }
        boolean canRevealSpotCandidates = currentResultMode == ResultMode.OVERSEAS
                && hasSpotCandidateOptions
                && !spotCandidateListExpanded;
        boolean canHideSpotCandidates = currentResultMode == ResultMode.OVERSEAS
                && hasSpotCandidateOptions
                && spotCandidateListExpanded;
        boolean hasNextCandidate = currentResultMode == ResultMode.OVERSEAS
                && !hasSpotCandidateOptions
                && currentCandidateNames != null
                && currentCandidateIndex >= 0
                && currentCandidateIndex + 1 < currentCandidateNames.size();
        boolean canShowNextAction = canRevealSpotCandidates || canHideSpotCandidates || hasNextCandidate;
        btnNextOption.setVisibility(canShowNextAction ? View.VISIBLE : View.GONE);
        btnNextOption.setEnabled(canShowNextAction);
        if (canHideSpotCandidates) {
            btnNextOption.setText("收起候选");
        } else if (canRevealSpotCandidates) {
            btnNextOption.setText(currentSpotCandidateCount > 0
                    ? "查看其他候选(" + Math.min(currentSpotCandidateCount, 5) + ")"
                    : "查看其他候选");
        } else if (hasNextCandidate) {
            btnNextOption.setText("换一个作品候选");
        } else {
            btnNextOption.setText("换一个结果");
        }
        updateNextStepHint();
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

    private void updateNextStepHint() {
        if (layoutNextStepHint == null || tvNextStepHint == null || cardResult == null
                || cardResult.getVisibility() != View.VISIBLE) {
            return;
        }
        String hint = buildNextStepHint();
        if (isBlank(hint)) {
            hideNextStepHint();
            return;
        }
        tvNextStepHint.setText("下一步：" + hint);
        layoutNextStepHint.setVisibility(View.VISIBLE);
    }

    private void hideNextStepHint() {
        if (layoutNextStepHint != null) {
            layoutNextStepHint.setVisibility(View.GONE);
        }
        if (tvNextStepHint != null) {
            tvNextStepHint.setText("");
        }
    }

    private String buildNextStepHint() {
        if (currentResultMode == ResultMode.NONE) {
            return "";
        }
        ParsedResult parsedResult = lastParsedResult;
        boolean lowConfidence = isLowConfidenceResult(parsedResult);
        if (currentResultMode == ResultMode.DOMESTIC) {
            if (lowConfidence) {
                return currentIdentifyMode == IdentifyMode.AUTO
                        ? "结果不够确定，可从侧边栏切换到国内旅行或动漫巡礼后再识别。"
                        : "结果不够确定，建议重新上传更清晰图片或切换识别模式。";
            }
            return "结果较明确，可以保存打卡；需要路线时可点击导航。";
        }
        if (spotCandidateListExpanded) {
            return "从候选中选择更匹配的地点，或收起候选回到当前主结果。";
        }
        if (hasSpotCandidateOptions) {
            return lowConfidence
                    ? "建议先查看其他候选；没有合适结果时再输入作品名或地点线索重新匹配。"
                    : "结果较明确，可以保存打卡；不确定时可查看其他候选。";
        }
        if (allowManualAnimeRematch || lowConfidence) {
            return "建议输入作品名或地点线索重新匹配。";
        }
        if (currentCandidateNames != null
                && currentCandidateIndex >= 0
                && currentCandidateIndex + 1 < currentCandidateNames.size()) {
            return "可以换一个作品候选继续匹配。";
        }
        if (!isBlank(confirmedAnimeName) || !isBlank(currentAnimeName)) {
            return "结果较明确，可以保存打卡。";
        }
        return "";
    }

    private void updateSaveRecordButtonState() {
        if (btnSaveRecord == null) {
            return;
        }
        boolean hasConfirmedRecord = !isBlank(confirmedAnimeName)
                || !isBlank(confirmedLocationName)
                || !isBlank(confirmedSpotName);
        boolean hasCurrentDomesticRecord = currentResultMode == ResultMode.DOMESTIC
                && (!isBlank(currentAnimeName) || !isBlank(currentLocation));
        boolean hasRecordData = hasConfirmedRecord || hasCurrentDomesticRecord;
        btnSaveRecord.setVisibility(hasRecordData ? View.VISIBLE : View.GONE);
        btnSaveRecord.setEnabled(hasRecordData && !hasSavedCurrentRecord);
        btnSaveRecord.setText(hasSavedCurrentRecord ? "已记录打卡" : "📌 记录打卡");
    }

    private void saveCurrentRecord() {
        String animeNameToSave = chooseFirstNonBlank(confirmedAnimeName, currentAnimeName);
        String locationToSave = chooseFirstNonBlank(confirmedLocationName, confirmedSpotName, currentLocation);
        String descToSave = chooseFirstNonBlank(confirmedDescription, currentDesc);
        String localImageUriToSave = chooseFirstNonBlank(confirmedLocalImageUri, currentLocalUri);
        String referenceImageUrlToSave = chooseFirstNonBlank(confirmedReferenceUrl, currentReferenceUrl);

        Log.d(DEBUG_TAG, "confirmedAnimeName=" + confirmedAnimeName);
        Log.d(DEBUG_TAG, "confirmedSpotName=" + confirmedSpotName);
        Log.d(DEBUG_TAG, "save animeName=" + animeNameToSave);
        Log.d(DEBUG_TAG, "save location=" + locationToSave);

        if (isBlank(animeNameToSave) && isBlank(locationToSave)) {
            showToast("当前没有可记录的巡礼结果");
            return;
        }
        if (hasSavedCurrentRecord) {
            showToast("这条巡礼记录已经保存过了");
            return;
        }

        IdentifyMode recognitionModeToSave = currentIdentifyMode;
        boolean isDomesticRecord = currentResultMode == ResultMode.DOMESTIC;
        long recordTimestamp = System.currentTimeMillis();

        backgroundExecutor.execute(() -> {
            try {
                String persistentLocalImageUri = createPersistentDiaryImageCopy(localImageUriToSave, recordTimestamp);
                PilgrimRecord record = new PilgrimRecord();
                record.animeName = animeNameToSave;
                record.locationName = locationToSave;
                record.description = descToSave;
                record.localImageUri = persistentLocalImageUri;
                record.referenceImageUrl = referenceImageUrlToSave;
                record.timestamp = recordTimestamp;
                AppDatabase.getInstance(MainActivity.this).pilgrimDao().insert(record);
                submitManagementRecognitionRecord(
                        animeNameToSave,
                        locationToSave,
                        descToSave,
                        persistentLocalImageUri,
                        recognitionModeToSave,
                        isDomesticRecord
                );
                runSafelyOnUiThread(() -> {
                    hasSavedCurrentRecord = true;
                    updateSaveRecordButtonState();
                    if (SAVE_ACTION_OPEN_DIARY.equals(saveAction)) {
                        openPilgrimDiary();
                    }
                    showToast("打卡成功！已收录至巡礼日记");
                });
            } catch (Exception e) {
                Log.e(DEBUG_TAG, "保存巡礼记录失败", e);
                runSafelyOnUiThread(() -> showToast("打卡保存失败，请稍后重试"));
            }
        });
    }

    private void requestManagementThemeMatch(ParsedResult parsedResult, int searchGeneration) {
        if (tourInfoApiClient == null || parsedResult == null) {
            return;
        }
        String keyword = chooseFirstNonBlank(
                parsedResult.locationName,
                parsedResult.animeTitle,
                parsedResult.animeNames != null && !parsedResult.animeNames.isEmpty()
                        ? parsedResult.animeNames.get(0)
                        : null,
                currentLocation
        );
        if (isBlank(keyword)) {
            return;
        }
        tourInfoApiClient.matchTheme(keyword, new TourInfoApiClient.ApiCallback<List<TourThemeMatchResult>>() {
            @Override
            public void onSuccess(List<TourThemeMatchResult> data) {
                if (isStaleSearch(searchGeneration) || data == null || data.isEmpty()) {
                    return;
                }
                TourThemeMatchResult theme = data.get(0);
                String supplement = buildManagementThemeSupplement(theme);
                if (isBlank(supplement)) {
                    return;
                }
                runSafelyOnUiThread(() -> {
                    if (isStaleSearch(searchGeneration)) {
                        return;
                    }
                    setManagementSupplement(supplement);
                });
            }

            @Override
            public void onFailure(@NonNull Exception exception) {
                Log.d(DEBUG_TAG, "management theme match skipped: " + exception.getMessage());
            }
        });
    }

    private void requestManagementRecognitionAssist(ParsedResult parsedResult, int searchGeneration) {
        if (tourInfoApiClient == null || parsedResult == null) {
            return;
        }
        String keyword = chooseFirstNonBlank(
                parsedResult.locationName,
                parsedResult.animeTitle,
                parsedResult.animeNames != null && !parsedResult.animeNames.isEmpty()
                        ? parsedResult.animeNames.get(0)
                        : null,
                currentLocation
        );
        if (isBlank(keyword)) {
            return;
        }
        tourInfoApiClient.getRecognitionAssist(keyword, getCurrentManagementAppUserId(), getCurrentManagementAuthToken(), new TourInfoApiClient.ApiCallback<TourRecognitionAssistResponse>() {
            @Override
            public void onSuccess(TourRecognitionAssistResponse data) {
                if (isStaleSearch(searchGeneration) || data == null || data.getItems() == null || data.getItems().isEmpty()) {
                    return;
                }
                TourRecognitionAssistCandidate candidate = data.getItems().get(0);
                String supplement = buildManagementAssistSupplement(candidate);
                if (isBlank(supplement)) {
                    return;
                }
                runSafelyOnUiThread(() -> {
                    if (isStaleSearch(searchGeneration)) {
                        return;
                    }
                    addManagementSupplementSection(supplement);
                });
            }

            @Override
            public void onFailure(@NonNull Exception exception) {
                Log.d(DEBUG_TAG, "management recognition assist skipped: " + exception.getMessage());
            }
        });
    }

    private void submitManagementRecognitionRecord(
            String animeName,
            String locationName,
            String description,
            String imageUri,
            IdentifyMode recognitionMode,
            boolean isDomesticRecord
    ) {
        if (tourInfoApiClient == null) {
            return;
        }
        TourInfoApiClient.RecognitionRecordPayload payload = new TourInfoApiClient.RecognitionRecordPayload()
                .put("app_user_id", getCurrentManagementAppUserId())
                .put("image_uri", imageUri)
                .put("recognition_mode", recognitionMode != null
                        ? recognitionMode.name().toLowerCase(Locale.ROOT)
                        : "auto")
                .put("ai_model", chooseFirstNonBlank(BuildConfig.DOUBAO_MODEL_ID, BuildConfig.DOUBAO_MODEL, "doubao"))
                .put("recognized_theme", chooseFirstNonBlank(
                        animeName,
                        lastParsedResult != null ? lastParsedResult.animeTitle : null
                ))
                .put("recognized_location", chooseFirstNonBlank(
                        locationName,
                        lastParsedResult != null ? lastParsedResult.locationName : null
                ))
                .put("is_domestic", isDomesticRecord)
                .put("confidence", 0)
                .put("description", description)
                .put("user_confirmed", true)
                .put("status", "saved");
        addDoubaoUsagePayload(payload);
        tourInfoApiClient.createRecognitionRecord(payload, getCurrentManagementAuthToken(), new TourInfoApiClient.ApiCallback<TourRecognitionRecordResult>() {
            @Override
            public void onSuccess(TourRecognitionRecordResult data) {
                if (data == null || data.getId() <= 0) {
                    return;
                }
                lastManagementRecognitionId = data.getId();
                requestManagementRecognitionCost(data.getId());
            }

            @Override
            public void onFailure(@NonNull Exception exception) {
                Log.d(DEBUG_TAG, "management recognition record skipped: " + exception.getMessage());
            }
        });
    }

    private void submitManagementCorrection(String correctedAnimeName) {
        if (tourInfoApiClient == null || isBlank(correctedAnimeName)) {
            return;
        }
        TourInfoApiClient.CorrectionPayload payload = new TourInfoApiClient.CorrectionPayload()
                .put("recognition_id", lastManagementRecognitionId)
                .put("app_user_id", getCurrentManagementAppUserId())
                .put("original_theme", chooseFirstNonBlank(
                        confirmedAnimeName,
                        currentAnimeName,
                        lastParsedResult != null ? lastParsedResult.animeTitle : null
                ))
                .put("corrected_theme", correctedAnimeName)
                .put("original_location", chooseFirstNonBlank(
                        confirmedLocationName,
                        confirmedSpotName,
                        currentLocation,
                        lastParsedResult != null ? lastParsedResult.locationName : null
                ))
                .put("corrected_location", currentLocation)
                .put("correction_reason", "用户手动输入作品名后重新匹配");
        tourInfoApiClient.submitCorrection(payload, getCurrentManagementAuthToken(), new TourInfoApiClient.ApiCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                Log.d(DEBUG_TAG, "management correction submitted");
            }

            @Override
            public void onFailure(@NonNull Exception exception) {
                Log.d(DEBUG_TAG, "management correction skipped: " + exception.getMessage());
            }
        });
    }

    private void requestManagementRecognitionCost(int recognitionId) {
        if (tourInfoApiClient == null) {
            return;
        }
        tourInfoApiClient.getRecognitionCost(recognitionId, getCurrentManagementAuthToken(), new TourInfoApiClient.ApiCallback<TourRecognitionCostResult>() {
            @Override
            public void onSuccess(TourRecognitionCostResult data) {
                if (data == null) {
                    return;
                }
                String costText = buildManagementCostSupplement(data);
                runSafelyOnUiThread(() -> addManagementSupplementSection(costText));
            }

            @Override
            public void onFailure(@NonNull Exception exception) {
                Log.d(DEBUG_TAG, "management cost skipped: " + exception.getMessage());
            }
        });
    }

    private String getCurrentManagementAppUserId() {
        if (tourAuthSession == null) {
            return TourAuthSession.LOCAL_APP_USER_ID;
        }
        return chooseFirstNonBlank(tourAuthSession.getCurrentAppUserId(), TourAuthSession.LOCAL_APP_USER_ID);
    }

    private String getCurrentManagementAuthToken() {
        if (tourAuthSession == null || !tourAuthSession.isLoggedIn()) {
            return "";
        }
        return chooseFirstNonBlank(tourAuthSession.getToken(), "");
    }

    private void cacheDoubaoUsage(@Nullable DoubaoVisionClient.RecognitionResponse response) {
        lastDoubaoUsageStats = response == null ? null : response.usageStats;
    }

    private void addDoubaoUsagePayload(TourInfoApiClient.RecognitionRecordPayload payload) {
        if (payload == null || lastDoubaoUsageStats == null || !lastDoubaoUsageStats.hasUsage()) {
            return;
        }
        payload.put("service_provider", "doubao")
                .put("service_type", "vision_recognition")
                .put("endpoint", BuildConfig.DOUBAO_RESPONSES_URL)
                .put("input_tokens", lastDoubaoUsageStats.inputTokens)
                .put("output_tokens", lastDoubaoUsageStats.outputTokens)
                .put("request_count", 1);
    }

    private String buildManagementThemeSupplement(TourThemeMatchResult theme) {
        if (theme == null) {
            return "";
        }
        return joinLines(
                "后台辅助信息",
                "匹配主题：" + chooseFirstNonBlank(theme.getThemeName(), "未命名主题"),
                "主题类型：" + chooseFirstNonBlank(theme.getThemeType(), "未配置"),
                isBlank(theme.getDescription()) ? "" : "作品介绍：" + theme.getDescription()
        );
    }

    private String buildManagementAssistSupplement(TourRecognitionAssistCandidate candidate) {
        if (candidate == null || isBlank(candidate.getLocationName())) {
            return "";
        }
        String sourceText = "learned".equals(candidate.getCandidateSource())
                ? "用户学习候选"
                : "正式地点库";
        return joinLines(
                "识别学习辅助",
                "候选地点：" + candidate.getLocationName(),
                isBlank(candidate.getThemeName()) ? "" : "关联主题：" + candidate.getThemeName(),
                "候选来源：" + sourceText,
                "综合评分：" + formatCostAmount(candidate.getScore()),
                isBlank(candidate.getAddress()) ? "" : "地址：" + candidate.getAddress(),
                isBlank(candidate.getRecommendReason()) ? "" : "推荐依据：" + candidate.getRecommendReason()
        );
    }

    private String buildManagementCostSupplement(TourRecognitionCostResult cost) {
        if (cost == null) {
            return "";
        }
        String currency = chooseFirstNonBlank(cost.getCurrency(), "CNY");
        return joinLines(
                "本次估算成本",
                "AI 模型：" + formatCostAmount(cost.getAiModelCost()) + " " + currency,
                "地图服务：" + formatCostAmount(cost.getMapServiceCost()) + " " + currency,
                "其他接口：" + formatCostAmount(cost.getOtherApiCost()) + " " + currency,
                "合计：" + formatCostAmount(cost.getTotalCost()) + " " + currency
        );
    }

    private String formatCostAmount(double amount) {
        return String.format(Locale.CHINA, "%.4f", amount);
    }

    private void setManagementSupplement(String supplementText) {
        lastManagementSupplementText = supplementText;
        appendManagementSupplementToDescription(lastManagementSupplementText);
    }

    private void addManagementSupplementSection(String sectionText) {
        if (isBlank(sectionText)) {
            return;
        }
        if (isBlank(lastManagementSupplementText)) {
            lastManagementSupplementText = sectionText;
        } else if (!lastManagementSupplementText.contains(sectionText)) {
            lastManagementSupplementText = joinLines(lastManagementSupplementText, "", sectionText);
        }
        appendManagementSupplementToDescription(lastManagementSupplementText);
    }

    private void appendManagementSupplementToDescription(@Nullable String supplementText) {
        if (isBlank(supplementText)) {
            return;
        }
        TextView target = currentResultMode == ResultMode.DOMESTIC && tvDomesticIntro != null
                ? tvDomesticIntro
                : tvDesc;
        if (target == null) {
            return;
        }
        String currentText = target.getText() != null ? target.getText().toString() : "";
        int markerIndex = currentText.indexOf("后台辅助信息");
        if (markerIndex < 0) {
            markerIndex = currentText.indexOf("本次估算成本");
        }
        if (markerIndex < 0) {
            markerIndex = currentText.indexOf("识别学习辅助");
        }
        if (markerIndex < 0) {
            markerIndex = currentText.indexOf("后台辅助信息");
        }
        if (markerIndex < 0) {
            markerIndex = currentText.indexOf("本次估算成本");
        }
        String baseText = markerIndex >= 0
                ? currentText.substring(0, markerIndex).trim()
                : currentText.trim();
        target.setVisibility(View.VISIBLE);
        target.setText(joinLines(baseText, "", supplementText));
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

    private String getUserSelectedAnimeName(@Nullable ParsedResult parsedResult) {
        if (parsedResult != null) {
            String firstCandidate = parsedResult.animeNames != null && !parsedResult.animeNames.isEmpty()
                    ? parsedResult.animeNames.get(0)
                    : null;
            return chooseFirstNonBlank(firstCandidate, parsedResult.animeTitle, getCurrentCandidateName(""));
        }
        return getCurrentCandidateName("");
    }

    private int beginNewSearchGeneration() {
        activeSearchGeneration++;
        clearConfirmedPilgrimageSelection();
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
        if (currentIdentifyMode == IdentifyMode.ANIME) {
            animeDisplayName = chooseFirstNonBlank(getUserSelectedAnimeName(parsedResult), animeDisplayName);
        }
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
        String workIntroText = buildWorkIntroText(bangumiLiteResponse);
        if (!isBlank(workIntroText)) {
            lines.add("");
            lines.add("作品介绍");
            lines.add(workIntroText);
        }
        return joinLines(lines);
    }

    private String appendWorkIntroToDescription(
            String description,
            AnitabiApiClient.BangumiLiteResponse bangumiLiteResponse
    ) {
        String workIntroText = buildWorkIntroText(bangumiLiteResponse);
        if (isBlank(workIntroText)) {
            return description;
        }
        return joinLines(
                chooseFirstNonBlank(description, DEFAULT_RESULT_HINT),
                "",
                "作品介绍",
                workIntroText
        );
    }

    private String buildWorkIntroText(AnitabiApiClient.BangumiLiteResponse bangumiLiteResponse) {
        if (bangumiLiteResponse == null) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        String cnName = chooseFirstNonBlank(
                bangumiLiteResponse.getSubjectNameCn(),
                bangumiLiteResponse.getCn()
        );
        String originalName = chooseFirstNonBlank(
                bangumiLiteResponse.getSubjectName(),
                bangumiLiteResponse.getTitle()
        );
        if (!isBlank(cnName)) {
            lines.add("中文名：" + cnName);
        }
        if (!isBlank(originalName) && !originalName.equals(cnName)) {
            lines.add("原名：" + originalName);
        }
        if (!isBlank(bangumiLiteResponse.getSubjectDate())) {
            lines.add("放送日期：" + bangumiLiteResponse.getSubjectDate());
        }
        Integer eps = bangumiLiteResponse.getSubjectEps();
        if (eps != null && eps > 0) {
            lines.add("集数：" + eps);
        }
        if (!isBlank(bangumiLiteResponse.getSubjectPlatform())) {
            lines.add("类型：" + bangumiLiteResponse.getSubjectPlatform());
        }
        int pointsLength = parseIntSafely(bangumiLiteResponse.getPointsLength());
        if (pointsLength > 0) {
            lines.add("Anitabi 收录巡礼点：" + pointsLength);
        }
        if (!isBlank(bangumiLiteResponse.getSubjectSummary())) {
            lines.add("简介：" + bangumiLiteResponse.getSubjectSummary());
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

    private String joinLines(String... lines) {
        List<String> lineList = new ArrayList<>();
        if (lines != null) {
            for (String line : lines) {
                lineList.add(line);
            }
        }
        return joinLines(lineList);
    }

    private String joinWithSeparator(List<String> values, String separator) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (isBlank(value)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(separator);
            }
            builder.append(value.trim());
        }
        return builder.toString();
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
