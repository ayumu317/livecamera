package com.example.livecamera;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SettingsActivity extends AppCompatActivity {

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

    private SharedPreferences settings;
    private MaterialCardView rowDefaultMode;
    private MaterialCardView rowPreviewMode;
    private MaterialCardView rowSaveAction;
    private MaterialCardView rowColorTheme;
    private MaterialCardView rowFeatureGuide;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);
        settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        applyWindowInsets();
        bindViews();
        initListeners();
        refreshSettingRows();
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainSettings), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void bindViews() {
        MaterialButton btnSettingsBack = findViewById(R.id.btnSettingsBack);
        btnSettingsBack.setOnClickListener(view -> finish());
        rowDefaultMode = findViewById(R.id.rowDefaultMode);
        rowPreviewMode = findViewById(R.id.rowPreviewMode);
        rowSaveAction = findViewById(R.id.rowSaveAction);
        rowColorTheme = findViewById(R.id.rowColorTheme);
        rowFeatureGuide = findViewById(R.id.rowFeatureGuide);
    }

    private void initListeners() {
        rowDefaultMode.setOnClickListener(view -> chooseDefaultMode());
        rowPreviewMode.setOnClickListener(view -> choosePreviewMode());
        rowSaveAction.setOnClickListener(view -> chooseSaveAction());
        rowColorTheme.setOnClickListener(view -> chooseColorTheme());
        rowFeatureGuide.setOnClickListener(view -> startActivity(new Intent(this, FeatureGuideActivity.class)));
    }

    private void refreshSettingRows() {
        bindSettingRow(
                rowDefaultMode,
                "默认识别模式",
                "控制首页启动后优先使用的识别入口",
                getDefaultModeLabel(getDefaultModeValue())
        );
        bindSettingRow(
                rowPreviewMode,
                "图片预览方式",
                "完整显示适合确认构图，填充裁剪更像封面",
                getPreviewModeLabel(getPreviewModeValue())
        );
        bindSettingRow(
                rowSaveAction,
                "保存后动作",
                "记录打卡成功后是否自动打开巡礼日记",
                getSaveActionLabel(getSaveActionValue())
        );
        bindSettingRow(
                rowColorTheme,
                "颜色主题",
                "先作用于首页关键按钮和导航入口",
                getColorThemeLabel(getColorThemeValue())
        );
        bindSettingRow(
                rowFeatureGuide,
                "功能介绍",
                "查看识别流程、模式说明和日记导出方法",
                "查看"
        );
    }

    private void bindSettingRow(MaterialCardView row, String title, String summary, String value) {
        TextView tvTitle = row.findViewById(R.id.tvSettingTitle);
        TextView tvSummary = row.findViewById(R.id.tvSettingSummary);
        TextView tvValue = row.findViewById(R.id.tvSettingValue);
        tvTitle.setText(title);
        tvSummary.setText(summary);
        tvValue.setText(value);
    }

    private void chooseDefaultMode() {
        String[] labels = new String[] {"智能识别", "动漫巡礼", "国内旅行"};
        String[] values = new String[] {"AUTO", "ANIME", "DOMESTIC"};
        showChoiceDialog("默认识别模式", labels, values, getDefaultModeValue(), PREF_DEFAULT_MODE);
    }

    private void choosePreviewMode() {
        String[] labels = new String[] {"完整显示", "填充裁剪"};
        String[] values = new String[] {PREVIEW_FIT, PREVIEW_FILL};
        showChoiceDialog("图片预览方式", labels, values, getPreviewModeValue(), PREF_PREVIEW_MODE);
    }

    private void chooseSaveAction() {
        String[] labels = new String[] {"停留当前页", "保存后打开巡礼日记"};
        String[] values = new String[] {SAVE_ACTION_STAY, SAVE_ACTION_OPEN_DIARY};
        showChoiceDialog("保存后动作", labels, values, getSaveActionValue(), PREF_SAVE_ACTION);
    }

    private void chooseColorTheme() {
        String[] labels = new String[] {"默认蓝紫", "清爽青绿", "暖色旅行", "深色预览"};
        String[] values = new String[] {THEME_DEFAULT, THEME_MINT, THEME_WARM, THEME_DARK};
        showChoiceDialog("颜色主题", labels, values, getColorThemeValue(), PREF_COLOR_THEME);
    }

    private void showChoiceDialog(String title, String[] labels, String[] values, String currentValue, String prefKey) {
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(currentValue)) {
                checked = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    int safeIndex = Math.max(0, Math.min(which, values.length - 1));
                    settings.edit().putString(prefKey, values[safeIndex]).apply();
                    refreshSettingRows();
                    Toast.makeText(this, "设置已更新", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String getDefaultModeValue() {
        return settings.getString(PREF_DEFAULT_MODE, "AUTO");
    }

    private String getPreviewModeValue() {
        return settings.getString(PREF_PREVIEW_MODE, PREVIEW_FIT);
    }

    private String getSaveActionValue() {
        return settings.getString(PREF_SAVE_ACTION, SAVE_ACTION_STAY);
    }

    private String getColorThemeValue() {
        return settings.getString(PREF_COLOR_THEME, THEME_DEFAULT);
    }

    private String getDefaultModeLabel(String value) {
        if ("ANIME".equals(value)) {
            return "动漫巡礼";
        }
        if ("DOMESTIC".equals(value)) {
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
}
