package com.example.livecamera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.tencent.map.geolocation.TencentLocation;
import com.tencent.map.geolocation.TencentLocationListener;
import com.tencent.map.geolocation.TencentLocationManager;
import com.tencent.map.geolocation.TencentLocationRequest;

/**
 * 腾讯单次定位帮助类。
 *
 * 设计目标：
 * 1. 只负责定位，不和相机、相册、识别链路耦合。
 * 2. 定位失败时只回调失败原因，不影响现有海外巡礼主流程。
 * 3. 业务方可在拍照前后“顺手调用一下”，拿到经纬度后自行决定是否上传后端。
 *
 * 调用示例：
 * <pre>
 * TencentLocationHelper helper = new TencentLocationHelper(this);
 * helper.startSingleLocation(new TencentLocationHelper.LocationCallback() {
 *     {@literal @}Override
 *     public void onSuccess(double latitude, double longitude, @Nullable String address) {
 *         Log.d("TOUR_DEBUG", "腾讯定位成功: " + latitude + "," + longitude + ", " + address);
 *     }
 *
 *     {@literal @}Override
 *     public void onFailure(@NonNull String reason) {
 *         Log.w("TOUR_DEBUG", "腾讯定位失败: " + reason);
 *     }
 * });
 * </pre>
 */
public class TencentLocationHelper {

    private static final String TAG = "TencentLocationHelper";
    private static final long SINGLE_LOCATION_TIMEOUT_MS = 12_000L;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final TencentLocationManager locationManager;

    @Nullable
    private TencentLocationListener activeListener;
    @Nullable
    private Runnable timeoutRunnable;

    public interface LocationCallback {
        void onSuccess(double latitude, double longitude, @Nullable String address);

        void onFailure(@NonNull String reason);
    }

    public TencentLocationHelper(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.locationManager = TencentLocationManager.getInstance(appContext);
    }

    /**
     * 发起一次静默单次定位。
     * 如果当前没有定位权限、Key 未配置或腾讯 SDK 初始化失败，会直接回调失败，不影响主链路。
     */
    public void startSingleLocation(@NonNull LocationCallback callback) {
        mainHandler.post(() -> startInternal(callback));
    }

    /**
     * 主动停止当前定位监听，适合在 Activity / Fragment 销毁时调用。
     */
    public void stop() {
        mainHandler.post(this::clearActiveRequest);
    }

    private void startInternal(@NonNull LocationCallback callback) {
        if (isBlank(BuildConfig.TENCENT_MAP_SDK_KEY)) {
            callback.onFailure("腾讯定位 Key 未配置");
            return;
        }
        if (!hasLocationPermission()) {
            callback.onFailure("缺少定位权限，请先授予 ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION");
            return;
        }

        clearActiveRequest();

        TencentLocationRequest request = TencentLocationRequest.create();
        request.setRequestLevel(TencentLocationRequest.REQUEST_LEVEL_NAME);
        request.setAllowGPS(true);
        request.setAllowDirection(false);
        request.setAllowCache(false);
        request.setInterval(0);

        activeListener = new TencentLocationListener() {
            @Override
            public void onLocationChanged(TencentLocation location, int error, String reason) {
                clearActiveRequest();
                if (error == TencentLocation.ERROR_OK && location != null) {
                    callback.onSuccess(
                            location.getLatitude(),
                            location.getLongitude(),
                            emptyToNull(location.getAddress())
                    );
                    return;
                }
                String failReason = "定位失败";
                if (!isBlank(reason)) {
                    failReason = failReason + ": " + reason;
                } else {
                    failReason = failReason + "，错误码=" + error;
                }
                Log.w(TAG, failReason);
                callback.onFailure(failReason);
            }

            @Override
            public void onStatusUpdate(String name, int status, String desc) {
                Log.d(TAG, "定位状态更新: " + name + ", status=" + status + ", desc=" + desc);
            }
        };

        timeoutRunnable = () -> {
            clearActiveRequest();
            callback.onFailure("定位超时，请检查网络或 GPS 状态");
        };
        mainHandler.postDelayed(timeoutRunnable, SINGLE_LOCATION_TIMEOUT_MS);

        int resultCode = locationManager.requestSingleFreshLocation(
                request,
                activeListener,
                Looper.getMainLooper()
        );
        if (resultCode != 0) {
            clearActiveRequest();
            callback.onFailure("腾讯定位发起失败，错误码=" + resultCode);
        }
    }

    private void clearActiveRequest() {
        if (timeoutRunnable != null) {
            mainHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
        if (activeListener != null) {
            locationManager.removeUpdates(activeListener);
            activeListener = null;
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Nullable
    private String emptyToNull(@Nullable String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return text.trim();
    }

    private boolean isBlank(@Nullable String text) {
        return text == null || text.trim().isEmpty();
    }
}
