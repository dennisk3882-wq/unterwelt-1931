package org.tiberiandawn.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.StatFs;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * No-disc path for Tiberian Dawn.
 *
 * The preferred path downloads the two known public C&C Gold freeware ISOs
 * into app-private cache, verifies them, installs them through the native ISO
 * importer, then removes the temporary ISO files. Manual ModDB links remain
 * available as a fallback if the provider changes its download flow.
 */
public final class FreewareDataActivity extends Activity {
    private static final long EXTRACTION_HEADROOM =
        1300L * 1024L * 1024L;
    private static final long SAFETY_HEADROOM =
        128L * 1024L * 1024L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean destroyed;

    private Button automaticButton;
    private Button gdiButton;
    private Button nodButton;
    private Button backButton;
    private TextView progressStatus;
    private ProgressBar progressBar;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LanguagePreferences.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createView());
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        executor.shutdownNow();
        super.onDestroy();
    }

    private View createView() {
        int padding = dp(24);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText(R.string.freeware_title);
        title.setTextSize(26.0f);
        title.setGravity(Gravity.CENTER);
        content.addView(title, params(0));

        TextView explanation = new TextView(this);
        explanation.setText(R.string.freeware_explanation);
        explanation.setTextSize(17.0f);
        explanation.setGravity(Gravity.CENTER);
        content.addView(explanation, params(dp(16)));

        automaticButton = new Button(this);
        automaticButton.setAllCaps(false);
        automaticButton.setText(R.string.freeware_auto_button);
        automaticButton.setOnClickListener(v -> confirmAutomaticDownload());
        content.addView(automaticButton, params(dp(20)));

        progressStatus = new TextView(this);
        progressStatus.setText(R.string.freeware_auto_ready);
        progressStatus.setTextSize(14.0f);
        progressStatus.setGravity(Gravity.CENTER);
        content.addView(progressStatus, params(dp(12)));

        progressBar = new ProgressBar(
            this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1000);
        progressBar.setProgress(0);
        progressBar.setIndeterminate(false);
        content.addView(progressBar, params(dp(8)));

        TextView fallback = new TextView(this);
        fallback.setText(R.string.freeware_manual_fallback);
        fallback.setTextSize(14.0f);
        fallback.setGravity(Gravity.CENTER);
        content.addView(fallback, params(dp(24)));

        gdiButton = new Button(this);
        gdiButton.setAllCaps(false);
        gdiButton.setText(R.string.freeware_download_gdi);
        gdiButton.setOnClickListener(v -> openPage(FreewareCatalog.GDI_PAGE));
        content.addView(gdiButton, params(dp(10)));

        nodButton = new Button(this);
        nodButton.setAllCaps(false);
        nodButton.setText(R.string.freeware_download_nod);
        nodButton.setOnClickListener(v -> openPage(FreewareCatalog.NOD_PAGE));
        content.addView(nodButton, params(dp(8)));

        TextView details = new TextView(this);
        details.setText(getString(R.string.freeware_expected_files,
            FreewareCatalog.GDI_FILENAME, FreewareCatalog.NOD_FILENAME));
        details.setTextSize(14.0f);
        content.addView(details, params(dp(18)));

        backButton = new Button(this);
        backButton.setAllCaps(false);
        backButton.setText(R.string.freeware_back_to_import);
        backButton.setOnClickListener(v -> finish());
        content.addView(backButton, params(dp(20)));

        scroll.addView(content, new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT,
            ScrollView.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private void confirmAutomaticDownload() {
        long downloadBytes = FreewareCatalog.TOTAL_DOWNLOAD_SIZE;
        new AlertDialog.Builder(this)
            .setTitle(R.string.freeware_auto_confirm_title)
            .setMessage(getString(R.string.freeware_auto_confirm,
                formatGiB(downloadBytes),
                formatGiB(requiredFreeBytes())))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.freeware_auto_start,
                (dialog, which) -> beginAutomaticDownload())
            .show();
    }

    private void beginAutomaticDownload() {
        long available = new StatFs(getFilesDir().getAbsolutePath()).getAvailableBytes();
        long required = requiredFreeBytes();
        if (available < required) {
            showError(getString(R.string.freeware_auto_space_error,
                formatGiB(required), formatGiB(available)));
            return;
        }

        setControlsEnabled(false);
        progressBar.setProgress(0);
        progressBar.setIndeterminate(false);
        progressStatus.setText(R.string.freeware_auto_preparing);

        executor.execute(() -> {
            File root = new File(getCacheDir(), "freeware-auto-download");
            try {
                File gdi = FreewareDownloadClient.downloadKnownIso(
                    FreewareCatalog.GDI_START,
                    FreewareCatalog.GDI_MODDB_FILE_ID,
                    FreewareCatalog.GDI_FILENAME,
                    FreewareCatalog.GDI_SIZE,
                    0L,
                    FreewareCatalog.TOTAL_DOWNLOAD_SIZE,
                    root,
                    this::publishDownloadProgress);
                verifyDownloaded(gdi, FreewareCatalog.GDI_FILENAME);

                File nod = FreewareDownloadClient.downloadKnownIso(
                    FreewareCatalog.NOD_START,
                    FreewareCatalog.NOD_MODDB_FILE_ID,
                    FreewareCatalog.NOD_FILENAME,
                    FreewareCatalog.NOD_SIZE,
                    FreewareCatalog.GDI_SIZE,
                    FreewareCatalog.TOTAL_DOWNLOAD_SIZE,
                    root,
                    this::publishDownloadProgress);
                verifyDownloaded(nod, FreewareCatalog.NOD_FILENAME);

                publishInstalling();
                File product = new File(getFilesDir(), "TiberianDawn");
                String error = GameDataImportActivity.installDownloadedGameData(
                    gdi, nod, product);
                if (error != null && !error.trim().isEmpty()) {
                    throw new IOException(error.trim());
                }
                if (!AndroidGameData.isInstalled(this)) {
                    throw new IOException("Installed game data did not pass validation");
                }

                deleteTree(root);
                postSuccess();
            } catch (IOException | RuntimeException | UnsatisfiedLinkError error) {
                postFailure(error.getMessage());
            }
        });
    }

    private void verifyDownloaded(File file, String displayName) throws IOException {
        publishVerifying(displayName);
        FreewareIsoVerifier.Result result =
            FreewareIsoVerifier.verify(file, displayName);
        if (result == FreewareIsoVerifier.Result.VALID) return;

        file.delete();
        if (result == FreewareIsoVerifier.Result.INVALID_SIZE) {
            throw new IOException(getString(
                R.string.import_error_freeware_size, displayName));
        }
        if (result == FreewareIsoVerifier.Result.INVALID_HASH) {
            throw new IOException(getString(
                R.string.import_error_freeware_hash, displayName));
        }
        throw new IOException(getString(R.string.freeware_auto_verify_error));
    }

    private void publishDownloadProgress(String fileName,
                                         long fileBytes,
                                         long fileTotal,
                                         long overallBytes,
                                         long overallTotal) {
        runOnUiThread(() -> {
            if (!canUpdateUi()) return;
            int progress = overallTotal > 0
                ? (int)Math.min(1000L, overallBytes * 1000L / overallTotal)
                : 0;
            progressBar.setIndeterminate(overallTotal <= 0);
            if (overallTotal > 0) progressBar.setProgress(progress);
            progressStatus.setText(getString(
                R.string.freeware_auto_downloading,
                fileName,
                formatMiB(fileBytes),
                formatMiB(fileTotal)));
        });
    }

    private void publishVerifying(String fileName) {
        runOnUiThread(() -> {
            if (!canUpdateUi()) return;
            progressStatus.setText(getString(
                R.string.freeware_auto_verifying, fileName));
            progressBar.setIndeterminate(true);
        });
    }

    private void publishInstalling() {
        runOnUiThread(() -> {
            if (!canUpdateUi()) return;
            progressStatus.setText(R.string.freeware_auto_installing);
            progressBar.setIndeterminate(true);
        });
    }

    private void postSuccess() {
        runOnUiThread(() -> {
            if (!canUpdateUi()) return;
            progressBar.setIndeterminate(false);
            progressBar.setProgress(1000);
            progressStatus.setText(R.string.freeware_auto_complete);
            new AlertDialog.Builder(this)
                .setTitle(R.string.import_complete_title)
                .setMessage(R.string.freeware_auto_complete_message)
                .setCancelable(false)
                .setPositiveButton(R.string.import_start_game,
                    (dialog, which) -> {
                        startActivity(new Intent(
                            FreewareDataActivity.this,
                            GameDataImportActivity.class));
                        finish();
                    })
                .show();
        });
    }

    private void postFailure(String message) {
        runOnUiThread(() -> {
            if (!canUpdateUi()) return;
            progressBar.setIndeterminate(false);
            progressStatus.setText(R.string.freeware_auto_failed);
            setControlsEnabled(true);
            showError(message == null || message.trim().isEmpty()
                ? getString(R.string.freeware_auto_download_error)
                : message);
        });
    }

    private void setControlsEnabled(boolean enabled) {
        automaticButton.setEnabled(enabled);
        gdiButton.setEnabled(enabled);
        nodButton.setEnabled(enabled);
        backButton.setEnabled(enabled);
    }

    private long requiredFreeBytes() {
        return FreewareCatalog.TOTAL_DOWNLOAD_SIZE
            + EXTRACTION_HEADROOM
            + SAFETY_HEADROOM;
    }

    private void openPage(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try {
            startActivity(intent);
        } catch (RuntimeException error) {
            showError(getString(R.string.freeware_open_error));
        }
    }

    private void showError(String message) {
        if (!canUpdateUi()) return;
        new AlertDialog.Builder(this)
            .setTitle(R.string.import_error_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    private boolean canUpdateUi() {
        return !destroyed && !isFinishing() && !isDestroyed();
    }

    private LinearLayout.LayoutParams params(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String formatGiB(long bytes) {
        return String.format(Locale.getDefault(), "%.2f GB",
            bytes / 1_000_000_000.0);
    }

    private static String formatMiB(long bytes) {
        return String.format(Locale.getDefault(), "%.0f MB",
            bytes / 1_000_000.0);
    }

    private static void deleteTree(File target) {
        if (target == null || !target.exists()) return;
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) deleteTree(child);
            }
        }
        target.delete();
    }
}
