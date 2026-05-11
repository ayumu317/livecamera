package com.example.livecamera;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

public class FeatureGuideActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_feature_guide);
        applyWindowInsets();
        bindViews();
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainFeatureGuide), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void bindViews() {
        MaterialButton btnFeatureGuideBack = findViewById(R.id.btnFeatureGuideBack);
        TextView tvFeatureGuideBody = findViewById(R.id.tvFeatureGuideBody);
        btnFeatureGuideBack.setOnClickListener(view -> finish());
        tvFeatureGuideBody.setText(buildGuideText());
    }

    private String buildGuideText() {
        return "一、这是什么\n"
                + "LiveCamera-LBS 是一个实景巡礼匹配助手。你可以上传或拍摄现实场景图片，让 AI 判断它更适合动漫圣地巡礼还是国内旅行识别。动漫巡礼会结合图片内容、作品名、Anitabi/Bangumi 数据和候选地点进行匹配。\n\n"
                + "二、上传图片识别流程\n"
                + "1. 在首页点击相册或拍照按钮，选择一张现实场景图片。\n"
                + "2. 点击“开始识别”。\n"
                + "3. 系统会先让 AI 分析图片中的建筑、街景、车站、桥梁、海边、学校等线索。\n"
                + "4. 如果识别到动漫作品候选，先选择你认为正确的作品。\n"
                + "5. 系统会把“作品名 + 当前图片”再次交给 AI，让它重新匹配巡礼地点线索。\n"
                + "6. 展示主结果后，可以直接“记录打卡”，也可以点击“换一个结果”查看其他候选地点。\n\n"
                + "三、三种识别模式\n"
                + "智能识别：默认入口。系统根据图片自动判断走动漫巡礼还是国内旅行。\n\n"
                + "动漫巡礼：适合上传街景、车站、学校、桥、展馆、海边、神社、商业街等巡礼图。即使模型偶尔判断成国内景点，也不会直接切走国内旅行链路。\n\n"
                + "国内旅行：只做中国境内景点、城市地标、建筑或自然风光识别，不调用动漫作品和巡礼点 API。\n\n"
                + "四、作品不对时怎么办\n"
                + "如果 AI 给出的作品不对，可以在结果底部输入正确作品名，然后点击重新匹配。这不是只按作品名搜索地点，而是让 AI 带着“正确作品名 + 当前图片”再次判断场景线索，再结合 Anitabi/Bangumi/SerpApi 查询候选巡礼地点。\n\n"
                + "五、巡礼日记与导出\n"
                + "点击“记录打卡”会保存当前主结果。巡礼日记里可以查看详情、删除记录，也可以导出 JSON 或 TXT。JSON 适合备份，TXT 适合阅读和分享。\n\n"
                + "六、识别小技巧\n"
                + "尽量上传清晰图片，保留建筑轮廓、道路、桥梁、站牌、海岸线、学校门口、展馆外观等关键线索。如果画面太局部，建议换一张更完整的角度。如果你已经知道作品名，优先使用作品名重新匹配，会比只让 AI 猜作品更稳定。";
    }
}
