package com.example.livecamera;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
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
    private EditText etProfileNickname;
    private EditText etProfilePhone;
    private EditText etProfileEmail;
    private EditText etProfileAvatarUrl;
    private EditText etOldCredential;
    private EditText etNewCredential;
    private EditText etConfirmCredential;
    private LinearLayout layoutProfileEditor;
    private LinearLayout layoutCredentialEditor;
    private MaterialButton btnAccountLogin;
    private MaterialButton btnAccountRegister;
    private MaterialButton btnAccountLogout;
    private MaterialButton btnProfileSave;
    private MaterialButton btnChangeCredential;

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
        etProfileNickname = findViewById(R.id.etProfileNickname);
        etProfilePhone = findViewById(R.id.etProfilePhone);
        etProfileEmail = findViewById(R.id.etProfileEmail);
        etProfileAvatarUrl = findViewById(R.id.etProfileAvatarUrl);
        etOldCredential = findViewById(R.id.etOldCredential);
        etNewCredential = findViewById(R.id.etNewCredential);
        etConfirmCredential = findViewById(R.id.etConfirmCredential);
        layoutProfileEditor = findViewById(R.id.layoutProfileEditor);
        layoutCredentialEditor = findViewById(R.id.layoutCredentialEditor);
        btnAccountLogin = findViewById(R.id.btnAccountLogin);
        btnAccountRegister = findViewById(R.id.btnAccountRegister);
        btnAccountLogout = findViewById(R.id.btnAccountLogout);
        btnProfileSave = findViewById(R.id.btnProfileSave);
        btnChangeCredential = findViewById(R.id.btnChangeCredential);
    }

    private void initListeners() {
        btnAccountLogin.setOnClickListener(view -> submitLogin());
        btnAccountRegister.setOnClickListener(view -> submitRegister());
        btnProfileSave.setOnClickListener(view -> submitProfileUpdate());
        btnChangeCredential.setOnClickListener(view -> submitCredentialChange());
        btnAccountLogout.setOnClickListener(view -> {
            authSession.clear();
            clearProfileFields();
            refreshAccountState();
            showToast("已退出账号，APP 将继续使用本地模式");
        });
    }

    private void verifyExistingSession() {
        if (authSession == null || tourInfoApiClient == null || !authSession.isLoggedIn()) {
            return;
        }
        tourInfoApiClient.getProfile(authSession.getToken(), new TourInfoApiClient.ApiCallback<TourAuthResult>() {
            @Override
            public void onSuccess(TourAuthResult data) {
                if (data != null && data.getUser() != null) {
                    authSession.updateUser(data.getUser());
                }
                runOnUiThread(() -> {
                    populateProfileFields(data != null ? data.getUser() : null);
                    refreshAccountState();
                });
            }

            @Override
            public void onFailure(@NonNull Exception exception) {
                if (clearSessionIfAuthFailed(exception)) {
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
                    populateProfileFields(data != null ? data.getUser() : null);
                    refreshAccountState();
                    showToast("登录成功，后台增强将绑定当前账号");
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
                    populateProfileFields(data != null ? data.getUser() : null);
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

    private void submitProfileUpdate() {
        if (!hasLoggedInSession()) {
            showToast("请先登录");
            return;
        }
        setLoading(true);
        TourInfoApiClient.ProfilePayload payload = new TourInfoApiClient.ProfilePayload()
                .put("nickname", getText(etProfileNickname))
                .put("phone", getText(etProfilePhone))
                .put("email", getText(etProfileEmail))
                .put("avatar_url", getText(etProfileAvatarUrl));
        tourInfoApiClient.updateProfile(payload, authSession.getToken(), new TourInfoApiClient.ApiCallback<TourAuthResult>() {
            @Override
            public void onSuccess(TourAuthResult data) {
                if (data != null && data.getUser() != null) {
                    authSession.updateUser(data.getUser());
                }
                runOnUiThread(() -> {
                    setLoading(false);
                    populateProfileFields(data != null ? data.getUser() : null);
                    refreshAccountState();
                    showToast("资料已更新");
                });
            }

            @Override
            public void onFailure(@NonNull Exception exception) {
                if (clearSessionIfAuthFailed(exception)) {
                    return;
                }
                runOnUiThread(() -> {
                    setLoading(false);
                    showToast("资料保存失败，不影响 APP 原流程");
                });
            }
        });
    }

    private void submitCredentialChange() {
        if (!hasLoggedInSession()) {
            showToast("请先登录");
            return;
        }
        String oldCredential = getText(etOldCredential);
        String newCredential = getText(etNewCredential);
        String confirmCredential = getText(etConfirmCredential);
        if (isBlank(oldCredential) || isBlank(newCredential)) {
            showToast("请输入原密码和新密码");
            return;
        }
        if (!isBlank(confirmCredential) && !newCredential.equals(confirmCredential)) {
            showToast("两次输入的新密码不一致");
            return;
        }
        setLoading(true);
        tourInfoApiClient.changePassword(oldCredential, newCredential, confirmCredential, authSession.getToken(), new TourInfoApiClient.ApiCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                runOnUiThread(() -> {
                    setLoading(false);
                    etOldCredential.setText("");
                    etNewCredential.setText("");
                    etConfirmCredential.setText("");
                    showToast("密码已修改");
                });
            }

            @Override
            public void onFailure(@NonNull Exception exception) {
                if (clearSessionIfAuthFailed(exception)) {
                    return;
                }
                runOnUiThread(() -> {
                    setLoading(false);
                    showToast("密码修改失败，请稍后重试");
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
        etAccountUsername.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        etAccountCredential.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        btnAccountLogin.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        btnAccountRegister.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        layoutProfileEditor.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        layoutCredentialEditor.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        btnAccountLogout.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
    }

    private void setLoading(boolean loading) {
        btnAccountLogin.setEnabled(!loading);
        btnAccountRegister.setEnabled(!loading);
        btnAccountLogout.setEnabled(!loading);
        btnProfileSave.setEnabled(!loading);
        btnChangeCredential.setEnabled(!loading);
    }

    private String getUsernameInput() {
        return etAccountUsername.getText() == null ? "" : etAccountUsername.getText().toString().trim();
    }

    private String getCredentialInput() {
        return etAccountCredential.getText() == null ? "" : etAccountCredential.getText().toString();
    }

    private boolean hasLoggedInSession() {
        return authSession != null && authSession.isLoggedIn();
    }

    private boolean clearSessionIfAuthFailed(@NonNull Exception exception) {
        String message = exception.getMessage();
        if (message == null || (!message.contains("401") && !message.contains("403"))) {
            return false;
        }
        authSession.clear();
        runOnUiThread(() -> {
            setLoading(false);
            clearProfileFields();
            refreshAccountState();
            showToast("登录状态已过期，请重新登录");
        });
        return true;
    }

    private void populateProfileFields(TourAuthUser user) {
        if (user == null) {
            return;
        }
        etProfileNickname.setText(valueOrDefault(user.getNickname(), user.getDisplayName()));
        etProfilePhone.setText(valueOrDefault(user.getPhone(), ""));
        etProfileEmail.setText(valueOrDefault(user.getEmail(), ""));
        etProfileAvatarUrl.setText(valueOrDefault(user.getAvatarUrl(), ""));
    }

    private void clearProfileFields() {
        etProfileNickname.setText("");
        etProfilePhone.setText("");
        etProfileEmail.setText("");
        etProfileAvatarUrl.setText("");
        etOldCredential.setText("");
        etNewCredential.setText("");
        etConfirmCredential.setText("");
    }

    private String getText(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
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
