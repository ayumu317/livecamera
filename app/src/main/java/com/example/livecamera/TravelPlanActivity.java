package com.example.livecamera;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Locale;

public class TravelPlanActivity extends AppCompatActivity {

    public static final String EXTRA_PLACE_NAME = "extra_place_name";
    public static final String EXTRA_PLACE_ADDRESS = "extra_place_address";
    public static final String EXTRA_PLACE_DESCRIPTION = "extra_place_description";
    public static final String EXTRA_PLACE_LATITUDE = "extra_place_latitude";
    public static final String EXTRA_PLACE_LONGITUDE = "extra_place_longitude";

    private TourAuthSession authSession;
    private TourInfoApiClient tourInfoApiClient;
    private TextView tvTravelPlanStatus;
    private TextView tvTravelPlanDetail;
    private LinearLayout layoutTravelPlanList;
    private MaterialButton btnTravelPlanAddPlace;
    private int selectedPlanId;
    private String currentPlaceName;
    private String currentPlaceAddress;
    private String currentPlaceDescription;
    private double currentPlaceLatitude;
    private double currentPlaceLongitude;
    private boolean hasCurrentPlaceLatitude;
    private boolean hasCurrentPlaceLongitude;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_travel_plan);
        authSession = new TourAuthSession(this);
        tourInfoApiClient = TourManagementBackendConfig.newClient(this);
        readCurrentPlaceExtras();
        applyWindowInsets();
        bindViews();
        loadTravelPlans();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tourInfoApiClient != null) {
            tourInfoApiClient.cancelAll();
        }
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainTravelPlans), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void bindViews() {
        MaterialButton btnTravelPlanBack = findViewById(R.id.btnTravelPlanBack);
        btnTravelPlanBack.setOnClickListener(view -> finish());
        tvTravelPlanStatus = findViewById(R.id.tvTravelPlanStatus);
        tvTravelPlanDetail = findViewById(R.id.tvTravelPlanDetail);
        layoutTravelPlanList = findViewById(R.id.layoutTravelPlanList);
        btnTravelPlanAddPlace = findViewById(R.id.btnTravelPlanAddPlace);
        btnTravelPlanAddPlace.setOnClickListener(view -> addCurrentPlaceToSelectedPlan());
        updateAddPlaceButton();
    }

    private void readCurrentPlaceExtras() {
        currentPlaceName = getIntent().getStringExtra(EXTRA_PLACE_NAME);
        currentPlaceAddress = getIntent().getStringExtra(EXTRA_PLACE_ADDRESS);
        currentPlaceDescription = getIntent().getStringExtra(EXTRA_PLACE_DESCRIPTION);
        hasCurrentPlaceLatitude = getIntent().hasExtra(EXTRA_PLACE_LATITUDE);
        hasCurrentPlaceLongitude = getIntent().hasExtra(EXTRA_PLACE_LONGITUDE);
        currentPlaceLatitude = getIntent().getDoubleExtra(EXTRA_PLACE_LATITUDE, 0);
        currentPlaceLongitude = getIntent().getDoubleExtra(EXTRA_PLACE_LONGITUDE, 0);
    }

    private void loadTravelPlans() {
        if (authSession == null || !authSession.isLoggedIn()) {
            showLoggedOutState();
            return;
        }
        tvTravelPlanStatus.setText("正在同步待出行计划...");
        tourInfoApiClient.listTravelPlans(1, 20, authSession.getToken(), new TourInfoApiClient.ApiCallback<TourTravelPlanPageResult>() {
            @Override
            public void onSuccess(TourTravelPlanPageResult data) {
                runOnUiThread(() -> renderPlanList(data));
            }

            @Override
            public void onFailure(@NonNull Exception exception) {
                handleAuthFailureIfNeeded(exception);
                runOnUiThread(() -> {
                    tvTravelPlanStatus.setText("待出行计划暂时无法同步，APP 识别、导航和日记不受影响。");
                    tvTravelPlanDetail.setText("请稍后重试，或在账号中心重新登录。");
                });
            }
        });
    }

    private void renderPlanList(@Nullable TourTravelPlanPageResult pageResult) {
        layoutTravelPlanList.removeAllViews();
        List<TourTravelPlanResult> plans = pageResult == null ? null : pageResult.getItems();
        if (plans == null || plans.isEmpty()) {
            tvTravelPlanStatus.setText("当前账号暂无待出行计划。");
            tvTravelPlanDetail.setText("可继续使用识别、导航和日记；计划创建仍以后台系统为准。");
            selectedPlanId = 0;
            updateAddPlaceButton();
            return;
        }
        tvTravelPlanStatus.setText(String.format(Locale.CHINA, "已同步 %d 个待出行计划", pageResult.getTotal()));
        for (TourTravelPlanResult plan : plans) {
            MaterialButton button = new MaterialButton(this);
            button.setText(formatPlanButtonText(plan));
            button.setAllCaps(false);
            button.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
            button.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.chip_brand_background));
            button.setStrokeColor(ContextCompat.getColorStateList(this, R.color.card_stroke));
            button.setStrokeWidth(dp(1));
            button.setCornerRadius(dp(18));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, dp(10));
            layoutTravelPlanList.addView(button, params);
            button.setOnClickListener(view -> selectPlan(plan.getId()));
        }
        selectPlan(plans.get(0).getId());
    }

    private void selectPlan(int planId) {
        selectedPlanId = planId;
        updateAddPlaceButton();
        tvTravelPlanDetail.setText("正在加载计划详情...");
        tourInfoApiClient.getTravelPlanOverview(planId, authSession.getToken(), new TourInfoApiClient.ApiCallback<TourTravelPlanOverviewResult>() {
            @Override
            public void onSuccess(TourTravelPlanOverviewResult data) {
                runOnUiThread(() -> tvTravelPlanDetail.setText(buildOverviewText(data)));
            }

            @Override
            public void onFailure(@NonNull Exception exception) {
                handleAuthFailureIfNeeded(exception);
                runOnUiThread(() -> tvTravelPlanDetail.setText("计划详情暂时无法同步，请稍后重试。"));
            }
        });
    }

    private void addCurrentPlaceToSelectedPlan() {
        if (selectedPlanId <= 0 || isBlank(currentPlaceName) || authSession == null || !authSession.isLoggedIn()) {
            return;
        }
        btnTravelPlanAddPlace.setEnabled(false);
        TourInfoApiClient.TravelPlacePayload payload = new TourInfoApiClient.TravelPlacePayload()
                .put("title", currentPlaceName)
                .put("address", currentPlaceAddress)
                .put("description", currentPlaceDescription);
        if (hasCurrentPlaceLatitude) {
            payload.put("latitude", currentPlaceLatitude);
        }
        if (hasCurrentPlaceLongitude) {
            payload.put("longitude", currentPlaceLongitude);
        }
        tourInfoApiClient.addPlaceToTravelPlan(selectedPlanId, payload, authSession.getToken(), new TourInfoApiClient.ApiCallback<TourTravelAttractionResult>() {
            @Override
            public void onSuccess(TourTravelAttractionResult data) {
                runOnUiThread(() -> {
                    btnTravelPlanAddPlace.setEnabled(true);
                    showToast("已加入待出行计划");
                    selectPlan(selectedPlanId);
                });
            }

            @Override
            public void onFailure(@NonNull Exception exception) {
                handleAuthFailureIfNeeded(exception);
                runOnUiThread(() -> {
                    btnTravelPlanAddPlace.setEnabled(true);
                    showToast("加入失败，不影响 APP 原流程");
                });
            }
        });
    }

    private void showLoggedOutState() {
        selectedPlanId = 0;
        layoutTravelPlanList.removeAllViews();
        tvTravelPlanStatus.setText("请先在账号中心登录后查看待出行计划。");
        tvTravelPlanDetail.setText("未登录时 APP 仍会继续使用 android-local 完成识别、导航和日记保存。");
        updateAddPlaceButton();
    }

    private void handleAuthFailureIfNeeded(@NonNull Exception exception) {
        String message = exception.getMessage();
        if (message != null && (message.contains("401") || message.contains("403"))) {
            authSession.clear();
            runOnUiThread(this::showLoggedOutState);
        }
    }

    private void updateAddPlaceButton() {
        boolean canAdd = selectedPlanId > 0 && !isBlank(currentPlaceName)
                && authSession != null && authSession.isLoggedIn();
        btnTravelPlanAddPlace.setVisibility(canAdd ? View.VISIBLE : View.GONE);
        btnTravelPlanAddPlace.setEnabled(canAdd);
        if (canAdd) {
            btnTravelPlanAddPlace.setText("加入当前识别地点：" + currentPlaceName);
        }
    }

    private String buildOverviewText(@Nullable TourTravelPlanOverviewResult overview) {
        if (overview == null || overview.getPlan() == null) {
            return "计划详情为空。";
        }
        TourTravelPlanResult plan = overview.getPlan();
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "计划：" + valueOrDefault(plan.getPlanName(), "未命名计划"));
        appendLine(builder, "目的地：" + valueOrDefault(plan.getDestinationName(), "未填写"));
        appendLine(builder, "城市/国家：" + valueOrDefault(plan.getDestinationCity(), "-")
                + " / " + valueOrDefault(plan.getDestinationCountry(), "-"));
        appendLine(builder, "日期：" + valueOrDefault(plan.getStartDate(), "-")
                + " 至 " + valueOrDefault(plan.getEndDate(), "-"));
        appendLine(builder, "状态：" + valueOrDefault(plan.getTravelStatus(), "pending"));
        appendLine(builder, "景点数：" + safeSize(overview.getAttractions())
                + "，路线数：" + safeSize(overview.getRoutes())
                + "，酒店数：" + safeSize(overview.getHotels()));
        if (!isBlank(plan.getDestinationSummary())) {
            appendLine(builder, "");
            appendLine(builder, plan.getDestinationSummary());
        }
        if (overview.getAttractions() != null && !overview.getAttractions().isEmpty()) {
            appendLine(builder, "");
            appendLine(builder, "已加入景点");
            for (TourTravelAttractionResult attraction : overview.getAttractions()) {
                appendLine(builder, "- " + valueOrDefault(attraction.getAttractionName(), "未命名地点")
                        + (isBlank(attraction.getAddress()) ? "" : " · " + attraction.getAddress()));
            }
        }
        return builder.toString().trim();
    }

    private String formatPlanButtonText(TourTravelPlanResult plan) {
        return valueOrDefault(plan.getPlanName(), "未命名计划")
                + " · " + valueOrDefault(plan.getDestinationName(), "目的地未填写");
    }

    private int safeSize(@Nullable List<?> items) {
        return items == null ? 0 : items.size();
    }

    private void appendLine(StringBuilder builder, String value) {
        builder.append(value).append('\n');
    }

    private String valueOrDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
