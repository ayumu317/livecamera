package com.example.livecamera;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

public class AccountActivity extends AppCompatActivity {

    private TourAuthSession authSession;
    private TourInfoApiClient tourInfoApiClient;
    private TextView tvAccountStatus;
    private TextView tvAccountUser;
    private EditText etAccountUsername;
    private EditText etAccountCredential;
    private MaterialButton btnAccountLogin;
    private MaterialButton btnAccountRegister;
    private MaterialButton btnAccountLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_account);
        authSession = new TourAuthSession(this);
        tourInfoApiClient = new TourInfoApiClient();
        applyWindowInsets();
        bindViews();
        initListeners();
        refreshAccountState();
        verifyExistingSession();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tourInfoApiClient != null) {
            tourInfoApiClient.cancelAll();
        }
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainAccount), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void bindViews() {
        MaterialButton btnAccountBack = findViewById(R.id.btnAccountBack);
        btnAccountBack.setOnClickListener(view -> finish());
        tvAccountStatus = findViewById(R.id.tvAccountStatus);
        tvAccountUser = findViewById(R.id.tvAccountUser);
        etAccountUsername = findViewById(R.id.etAccountUsername);
        etAccountCredential = findViewById(R.id.etAccountCredential);
        btnAccountLogin = findViewById(R.id.btnAccountLogin);
        btnAccountRegister = findViewById(R.id.btnAccountRegister);
        btnAccountLogout = findViewById(R.id.btnAccountLogout);
    }

    private void initListeners() {
        btnAccountLogin.setOnClickListener(view -> submitLogin());
        btnAccountRegister.setOnClickListener(view -> submitRegister());
        btnAccountLogout.setOnClickListener(view -> {
            authSession.clear();
            refreshAccountState();
            showToast("已退出账号，APP 将继续使用本地模式");
        });
    }

    private void verifyExistingSession() {
        if (authSession == null || tourInfoApiClient == null || !authSession.isLoggedIn()) {
            return;
        }
        tourInfoApiClient.me(authSession.getToken(), new TourInfoApiClient.ApiCallback<TourAuthResult>() {
            @Override
            public void onSuccess(TourAuthResult data) {
                runOnUiThread(() -> refreshAccountState());
            }

            @Override
            public void onFailure(@NonNull Exception exception) {
                if (exception.getMessage() != null && exception.getMessage().contains("401")) {
                    authSession.clear();
                    runOnUiThread(() -> {
                        refreshAccountState();
                        showToast("登录状态已过期，请重新登录");
                    });
                    return;
                }
                runOnUiThread(() -> showToast("账号状态暂时无法校验，不影响识别功能"));
            }
        });
    }

    private void submitLogin() {
        String username = getUsernameInput();
        String credential = getCredentialInput();
        if (isBlank(username) || isBlank(credential)) {
            showToast("请输入用户名和密码");
            return;
        }
        setLoading(true);
        tourInfoApiClient.login(username, credential, new TourInfoApiClient.ApiCallback<TourAuthResult>() {
            @Override
            public void onSuccess(TourAuthResult data) {
                authSession.save(data);
                runOnUiThread(() -> {
                    setLoading(false);
                    refreshAccountState();
                    showToast("登录成功，后续后台增强将绑定当前账号");
                });
            }

            @Override
            public void onFailure(@NonNull Exception exception) {
                runOnUiThread(() -> {
                    setLoading(false);
                    showToast("登录失败，APP 原功能不受影响");
                });
            }
        });
    }

    private void submitRegister() {
        String username = getUsernameInput();
        String credential = getCredentialInput();
        if (isBlank(username) || isBlank(credential)) {
            showToast("请输入用户名和密码");
            return;
        }
        setLoading(true);
        tourInfoApiClient.register(username, credential, credential, new TourInfoApiClient.ApiCallback<TourAuthResult>() {
            @Override
            public void onSuccess(TourAuthResult data) {
                authSession.save(data);
                runOnUiThread(() -> {
                    setLoading(false);
                    refreshAccountState();
                    showToast("注册成功，已自动登录");
                });
            }

            @Override
            public void onFailure(@NonNull Exception exception) {
                runOnUiThread(() -> {
                    setLoading(false);
                    showToast("注册失败，APP 原功能不受影响");
                });
            }
        });
    }

    private void refreshAccountState() {
        boolean loggedIn = authSession != null && authSession.isLoggedIn();
        if (loggedIn) {
            tvAccountStatus.setText("已登录");
            tvAccountUser.setText("账号：" + authSession.getDisplayName()
                    + "\n用户标识：" + authSession.getCurrentAppUserId()
                    + "\n角色：" + valueOrDefault(authSession.getRole(), "user"));
        } else {
            tvAccountStatus.setText("未登录");
            tvAccountUser.setText("未登录时后台增强会使用 android-local，不影响识别、导航和日记。");
        }
        btnAccountLogout.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
    }

    private void setLoading(boolean loading) {
        btnAccountLogin.setEnabled(!loading);
        btnAccountRegister.setEnabled(!loading);
        btnAccountLogout.setEnabled(!loading);
    }

    private String getUsernameInput() {
        return etAccountUsername.getText() == null ? "" : etAccountUsername.getText().toString().trim();
    }

    private String getCredentialInput() {
        return etAccountCredential.getText() == null ? "" : etAccountCredential.getText().toString();
    }

    private String valueOrDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
