package com.example.livecamera;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PilgrimDiaryActivity extends AppCompatActivity {

    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final List<PilgrimRecord> currentRecords = new ArrayList<>();

    private MaterialButton btnDiaryBack;
    private MaterialButton btnExportDiary;
    private ProgressBar pbDiaryLoading;
    private TextView tvDiaryLoading;
    private MaterialCardView cardDiaryEmpty;
    private TextView tvDiaryEmptyTitle;
    private TextView tvDiaryEmptyMessage;
    private View scrollDiaryContent;
    private LinearLayout layoutDiaryRecords;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_pilgrim_diary);
        applyWindowInsets();
        bindViews();
        initListeners();
        loadRecords();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        backgroundExecutor.shutdownNow();
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainDiary), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void bindViews() {
        btnDiaryBack = findViewById(R.id.btnDiaryBack);
        btnExportDiary = findViewById(R.id.btnExportDiary);
        pbDiaryLoading = findViewById(R.id.pbDiaryLoading);
        tvDiaryLoading = findViewById(R.id.tvDiaryLoading);
        cardDiaryEmpty = findViewById(R.id.cardDiaryEmpty);
        tvDiaryEmptyTitle = findViewById(R.id.tvDiaryEmptyTitle);
        tvDiaryEmptyMessage = findViewById(R.id.tvDiaryEmptyMessage);
        scrollDiaryContent = findViewById(R.id.scrollDiaryContent);
        layoutDiaryRecords = findViewById(R.id.layoutDiaryRecords);
    }

    private void initListeners() {
        btnDiaryBack.setOnClickListener(view -> finish());
        btnExportDiary.setOnClickListener(view -> showExportFormatDialog());
    }

    private void loadRecords() {
        showLoading(true);
        backgroundExecutor.execute(() -> {
            try {
                List<PilgrimRecord> records = AppDatabase.getInstance(PilgrimDiaryActivity.this)
                        .pilgrimDao()
                        .getAllRecordsByNewest();
                runSafelyOnUiThread(() -> renderRecords(records));
            } catch (Exception e) {
                runSafelyOnUiThread(() -> {
                    showLoading(false);
                    renderEmptyState(
                            getString(R.string.diary_load_failed),
                            getString(R.string.diary_empty_message)
                    );
                    Toast.makeText(
                            PilgrimDiaryActivity.this,
                            R.string.diary_load_failed,
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        });
    }

    private void renderRecords(List<PilgrimRecord> records) {
        showLoading(false);
        layoutDiaryRecords.removeAllViews();
        currentRecords.clear();
        if (records != null) {
            currentRecords.addAll(records);
        }
        updateExportButtonState();
        if (records == null || records.isEmpty()) {
            renderEmptyState(
                    getString(R.string.diary_empty_title),
                    getString(R.string.diary_empty_message)
            );
            return;
        }

        cardDiaryEmpty.setVisibility(View.GONE);
        scrollDiaryContent.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (PilgrimRecord record : records) {
            View itemView = inflater.inflate(R.layout.item_pilgrim_record, layoutDiaryRecords, false);
            bindRecordView(itemView, record);
            layoutDiaryRecords.addView(itemView);
        }
    }

    private void bindRecordView(View itemView, PilgrimRecord record) {
        ShapeableImageView ivRecordPhoto = itemView.findViewById(R.id.ivRecordPhoto);
        TextView tvRecordAnimeTitle = itemView.findViewById(R.id.tvRecordAnimeTitle);
        TextView tvRecordLocation = itemView.findViewById(R.id.tvRecordLocation);
        TextView tvRecordTime = itemView.findViewById(R.id.tvRecordTime);
        TextView tvRecordDescription = itemView.findViewById(R.id.tvRecordDescription);
        MaterialButton btnDeleteRecord = itemView.findViewById(R.id.btnDeleteRecord);

        tvRecordAnimeTitle.setText(chooseFirstNonBlank(record.animeName, "未命名作品"));
        tvRecordLocation.setText(chooseFirstNonBlank(record.locationName, "地点待补充"));
        tvRecordTime.setText(getString(R.string.diary_record_time_prefix) + formatTimestamp(record.timestamp));
        tvRecordDescription.setText(chooseFirstNonBlank(
                record.description,
                getString(R.string.diary_record_description_placeholder)
        ));

        bindRecordImage(ivRecordPhoto, record);
        itemView.setOnClickListener(view -> showRecordDetail(record));
        btnDeleteRecord.setOnClickListener(view -> confirmDeleteRecord(record));
    }

    private void showRecordDetail(PilgrimRecord record) {
        View detailView = LayoutInflater.from(this).inflate(R.layout.dialog_pilgrim_record_detail, null, false);
        ShapeableImageView ivDetailPhoto = detailView.findViewById(R.id.ivDetailPhoto);
        TextView tvDetailAnimeTitle = detailView.findViewById(R.id.tvDetailAnimeTitle);
        TextView tvDetailLocation = detailView.findViewById(R.id.tvDetailLocation);
        TextView tvDetailTime = detailView.findViewById(R.id.tvDetailTime);
        TextView tvDetailDescription = detailView.findViewById(R.id.tvDetailDescription);

        tvDetailAnimeTitle.setText(chooseFirstNonBlank(record.animeName, "未命名作品"));
        tvDetailLocation.setText(chooseFirstNonBlank(record.locationName, "地点待补充"));
        tvDetailTime.setText(getString(R.string.diary_record_time_prefix) + formatTimestamp(record.timestamp));
        tvDetailDescription.setText(chooseFirstNonBlank(
                record.description,
                getString(R.string.diary_record_description_placeholder)
        ));
        bindRecordImage(ivDetailPhoto, record);

        new MaterialAlertDialogBuilder(this)
                .setTitle("巡礼详情")
                .setView(detailView)
                .setPositiveButton("关闭", null)
                .setNegativeButton("删除", (dialog, which) -> confirmDeleteRecord(record))
                .show();
    }

    private void confirmDeleteRecord(PilgrimRecord record) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除这条日记？")
                .setMessage("删除后无法从本地日记中恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> deleteRecord(record))
                .show();
    }

    private void deleteRecord(PilgrimRecord record) {
        backgroundExecutor.execute(() -> {
            try {
                AppDatabase.getInstance(PilgrimDiaryActivity.this)
                        .pilgrimDao()
                        .deleteById(record.id);
                runSafelyOnUiThread(() -> {
                    Toast.makeText(PilgrimDiaryActivity.this, "已删除这条日记", Toast.LENGTH_SHORT).show();
                    loadRecords();
                });
            } catch (Exception e) {
                runSafelyOnUiThread(() -> Toast.makeText(
                        PilgrimDiaryActivity.this,
                        "删除失败，请稍后重试",
                        Toast.LENGTH_SHORT
                ).show());
            }
        });
    }

    private void showExportFormatDialog() {
        if (currentRecords.isEmpty()) {
            Toast.makeText(this, "暂无可导出的日记", Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("导出巡礼日记")
                .setItems(new String[] {"JSON 数据文件", "TXT 摘要文件"}, (dialog, which) -> {
                    if (which == 0) {
                        exportRecords(true);
                    } else {
                        exportRecords(false);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void exportRecords(boolean asJson) {
        List<PilgrimRecord> snapshot = new ArrayList<>(currentRecords);
        backgroundExecutor.execute(() -> {
            try {
                File exportDir = new File(getCacheDir(), "exports");
                if (!exportDir.exists() && !exportDir.mkdirs()) {
                    throw new IllegalStateException("Unable to create export directory");
                }
                String extension = asJson ? "json" : "txt";
                File exportFile = new File(exportDir, "pilgrim_diary_" + System.currentTimeMillis() + "." + extension);
                String content = asJson ? buildJsonExport(snapshot) : buildTextExport(snapshot);
                try (OutputStreamWriter writer = new OutputStreamWriter(
                        new FileOutputStream(exportFile),
                        StandardCharsets.UTF_8
                )) {
                    writer.write(content);
                }
                Uri fileUri = FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".fileprovider",
                        exportFile
                );
                runSafelyOnUiThread(() -> shareExportFile(fileUri, asJson));
            } catch (Exception e) {
                runSafelyOnUiThread(() -> Toast.makeText(
                        PilgrimDiaryActivity.this,
                        "导出失败，请稍后重试",
                        Toast.LENGTH_SHORT
                ).show());
            }
        });
    }

    private String buildJsonExport(List<PilgrimRecord> records) {
        JsonArray array = new JsonArray();
        for (PilgrimRecord record : records) {
            JsonObject item = new JsonObject();
            item.addProperty("id", record.id);
            item.addProperty("anime_name", chooseFirstNonBlank(record.animeName, ""));
            item.addProperty("location_name", chooseFirstNonBlank(record.locationName, ""));
            item.addProperty("description", chooseFirstNonBlank(record.description, ""));
            item.addProperty("local_image_uri", chooseFirstNonBlank(record.localImageUri, ""));
            item.addProperty("reference_image_url", chooseFirstNonBlank(record.referenceImageUrl, ""));
            item.addProperty("timestamp", record.timestamp);
            item.addProperty("saved_time", formatTimestamp(record.timestamp));
            array.add(item);
        }
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(array);
    }

    private String buildTextExport(List<PilgrimRecord> records) {
        StringBuilder builder = new StringBuilder();
        builder.append("LiveCamera-LBS 巡礼日记导出\n");
        builder.append("导出时间：").append(formatTimestamp(System.currentTimeMillis())).append("\n");
        builder.append("记录数量：").append(records.size()).append("\n\n");
        for (int i = 0; i < records.size(); i++) {
            PilgrimRecord record = records.get(i);
            builder.append(i + 1).append(". ")
                    .append(chooseFirstNonBlank(record.animeName, "未命名作品"))
                    .append("\n");
            builder.append("地点：").append(chooseFirstNonBlank(record.locationName, "地点待补充")).append("\n");
            builder.append("时间：").append(formatTimestamp(record.timestamp)).append("\n");
            if (!isBlank(record.description)) {
                builder.append("说明：").append(record.description).append("\n");
            }
            if (!isBlank(record.localImageUri)) {
                builder.append("本地图片：").append(record.localImageUri).append("\n");
            }
            if (!isBlank(record.referenceImageUrl)) {
                builder.append("参考图：").append(record.referenceImageUrl).append("\n");
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    private void shareExportFile(Uri fileUri, boolean asJson) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(asJson ? "application/json" : "text/plain");
        intent.putExtra(Intent.EXTRA_STREAM, fileUri);
        intent.putExtra(Intent.EXTRA_SUBJECT, "LiveCamera-LBS 巡礼日记导出");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "分享巡礼日记"));
    }

    private void bindRecordImage(ShapeableImageView imageView, PilgrimRecord record) {
        boolean hasLocalImage = !isBlank(record.localImageUri);
        boolean hasReferenceImage = !isBlank(record.referenceImageUrl);
        if (!hasLocalImage && !hasReferenceImage) {
            imageView.setVisibility(View.GONE);
            return;
        }

        imageView.setVisibility(View.VISIBLE);
        RequestBuilder<Drawable> requestBuilder;
        if (hasLocalImage) {
            requestBuilder = Glide.with(this)
                    .load(Uri.parse(record.localImageUri))
                    .centerCrop();
            if (hasReferenceImage) {
                requestBuilder = requestBuilder.error(
                        Glide.with(this)
                                .load(record.referenceImageUrl)
                                .centerCrop()
                );
            }
        } else {
            requestBuilder = Glide.with(this)
                    .load(record.referenceImageUrl)
                    .centerCrop();
        }
        requestBuilder.into(imageView);
    }

    private void renderEmptyState(String title, String message) {
        scrollDiaryContent.setVisibility(View.GONE);
        cardDiaryEmpty.setVisibility(View.VISIBLE);
        tvDiaryEmptyTitle.setText(title);
        tvDiaryEmptyMessage.setText(message);
    }

    private void showLoading(boolean loading) {
        pbDiaryLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        tvDiaryLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnExportDiary != null) {
            btnExportDiary.setEnabled(!loading && !currentRecords.isEmpty());
        }
        if (loading) {
            cardDiaryEmpty.setVisibility(View.GONE);
            scrollDiaryContent.setVisibility(View.GONE);
        }
    }

    private void updateExportButtonState() {
        if (btnExportDiary == null) {
            return;
        }
        btnExportDiary.setEnabled(!currentRecords.isEmpty());
    }

    private String formatTimestamp(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(timestamp);
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void runSafelyOnUiThread(Runnable action) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        runOnUiThread(action);
    }
}
