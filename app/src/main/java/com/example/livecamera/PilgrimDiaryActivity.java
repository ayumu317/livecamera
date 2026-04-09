package com.example.livecamera;

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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PilgrimDiaryActivity extends AppCompatActivity {

    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    private MaterialButton btnDiaryBack;
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

        tvRecordAnimeTitle.setText(chooseFirstNonBlank(record.animeName, "未命名作品"));
        tvRecordLocation.setText(chooseFirstNonBlank(record.locationName, "地点待补充"));
        tvRecordTime.setText(getString(R.string.diary_record_time_prefix) + formatTimestamp(record.timestamp));
        tvRecordDescription.setText(chooseFirstNonBlank(
                record.description,
                getString(R.string.diary_record_description_placeholder)
        ));

        bindRecordImage(ivRecordPhoto, record);
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
        if (loading) {
            cardDiaryEmpty.setVisibility(View.GONE);
            scrollDiaryContent.setVisibility(View.GONE);
        }
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
