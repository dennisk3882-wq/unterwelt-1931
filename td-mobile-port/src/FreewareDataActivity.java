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
 * Automatic no-disc game-data installer.
 *
 * English installs the two public C&C Gold freeware ISOs.
 * German first installs/uses that complete baseline, then applies verified
 * German 1.06 community language + video resources as an overlay.
 */
public final class FreewareDataActivity extends Activity {
    public static final String EXTRA_GERMAN_ONLY =
        "org.tiberiandawn.android.GERMAN_ONLY";

    private static final long BASE_EXTRACTION_HEADROOM =
        1300L * 1024L * 1024L;
    private static final long GERMAN_EXTRACTION_HEADROOM =
        1150L * 1024L * 1024L;
    private static final long SAFETY_HEADROOM =
        192L * 1024L * 1024L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean destroyed;

    private Button germanButton;
    private Button englishButton;
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

    private boolean germanOnlyRequested() {
        return getIntent() != null
            && getIntent().getBooleanExtra(EXTRA_GERMAN_ONLY, false)
            && AndroidGameData.isInstalled(this);
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
        title.setText(germanOnlyRequested()
            ? R.string.freeware_german_upgrade_title
            : R.string.freeware_title);
        title.setTextSize(26.0f);
        title.setGravity(Gravity.CENTER);
        content.addView(title, params(0));

        TextView explanation = new TextView(this);
        explanation.setText(germanOnlyRequested()
            ? R.string.freeware_german_upgrade_explanation
            : R.string.freeware_explanation);
        explanation.setTextSize(17.0f);
        explanation.setGravity(Gravity.CENTER);
        content.addView(explanation, params(dp(16)));

        germanButton = new Button(this);
        germanButton.setAllCaps(false);
        germanButton.setText(germanOnlyRequested()
            ? R.string.freeware_german_upgrade_button
            : R.string.freeware_auto_german_button);
        germanButton.setOnClickListener(v -> confirmAutomaticDownload(true));
        content.addView(germanButton, params(dp(20)));

        englishButton = new Button(this);
        englishButton.setAllCaps(false);
        englishButton.setText(R.string.freeware_auto_english_button);
        englishButton.setOnClickListener(v -> confirmAutomaticDownload(false));
        englishButton.setVisibility(germanOnlyRequested() ? View.GONE : View.VISIBLE);
        content.addView(englishButton, params(dp(8)));

        progressStatus = new TextView(this);
        progressStatus.setText(germanOnlyRequested()
            ? R.string.freeware_german_upgrade_ready
            : R.string.freeware_auto_ready);
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
        gdiButton.setVisibility(germanOnlyRequested() ? View.GONE : View.VISIBLE);
        content.addView(gdiButton, params(dp(10)));

        nodButton = new Button(this);
        nodButton.setAllCaps(false);
        nodButton.setText(R.string.freeware_download_nod);
        nodButton.setOnClickListener(v -> openPage(FreewareCatalog.NOD_PAGE));
        nodButton.setVisibility(germanOnlyRequested() ? View.GONE : View.VISIBLE);
        content.addView(nodButton, params(dp(8)));

        if (!germanOnlyRequested()) {
            TextView details = new TextView(this);
            details.setText(getString(R.string.freeware_expected_files,
                FreewareCatalog.GDI_FILENAME, FreewareCatalog.NOD_FILENAME));
            details.setTextSize(14.0f);
            content.addView(details, params(dp(18)));
        }

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

    private void confirmAutomaticDownload(boolean german) {
        boolean baselineInstalled = AndroidGameData.isInstalled(this);
        long downloadBytes = german
            ? (baselineInstalled
                ? FreewareCatalog.GERMAN_EXTRA_DOWNLOAD_SIZE
                : FreewareCatalog.GERMAN_TOTAL_DOWNLOAD_SIZE)
            : (baselineInstalled ? 0L : FreewareCatalog.TOTAL_DOWNLOAD_SIZE);
        long required = requiredFreeBytes(german, baselineInstalled);

        int messageId = german
            ? R.string.freeware_german_confirm
            : R.string.freeware_auto_confirm;
        String message = getString(messageId,
            formatGiB(downloadBytes), formatGiB(required));

        new AlertDialog.Builder(this)
            .setTitle(german
                ? R.string.freeware_german_confirm_title
                : R.string.freeware_auto_confirm_title)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.freeware_auto_start,
                (dialog, which) -> beginAutomaticDownload(german))
            .show();
    }

    private void beginAutomaticDownload(boolean german) {
        boolean baselineInstalled = AndroidGameData.isInstalled(this);
        long available = new StatFs(getFilesDir().getAbsolutePath()).getAvailableBytes();
        long required = requiredFreeBytes(german, baselineInstalled);
        if (available < required) {
            showError(getString(R.string.freeware_auto_space_error,
                formatGiB(required), formatGiB(available)));
            return;
        }

        setControlsEnabled(false);
        progressBar.setProgress(0);
        progressBar.setIndeterminate(false);
        progressStatus.setText(R.string.freeware_auto_preparing);

        executor.execute(() -> performAutomaticInstall(german));
    }

    private void performAutomaticInstall(boolean german) {
        File root = new File(getCacheDir(), "freeware-auto-download");
        boolean baselineInstalled = AndroidGameData.isInstalled(this);
        long overallTotal = german
            ? (baselineInstalled
                ? FreewareCatalog.GERMAN_EXTRA_DOWNLOAD_SIZE
                : FreewareCatalog.GERMAN_TOTAL_DOWNLOAD_SIZE)
            : FreewareCatalog.TOTAL_DOWNLOAD_SIZE;
        long completed = 0L;

        try {
            if (!baselineInstalled) {
                File gdi = FreewareDownloadClient.downloadKnownIso(
                    FreewareCatalog.GDI_START,
                    FreewareCatalog.GDI_MODDB_FILE_ID,
                    FreewareCatalog.GDI_FILENAME,
                    FreewareCatalog.GDI_SIZE,
                    completed,
                    overallTotal,
                    root,
                    this::publishDownloadProgress);
                verifyKnown(gdi, FreewareCatalog.GDI_FILENAME,
                    FreewareCatalog.GDI_SIZE, FreewareCatalog.GDI_MD5);
                completed += FreewareCatalog.GDI_SIZE;

                File nod = FreewareDownloadClient.downloadKnownIso(
                    FreewareCatalog.NOD_START,
                    FreewareCatalog.NOD_MODDB_FILE_ID,
                    FreewareCatalog.NOD_FILENAME,
                    FreewareCatalog.NOD_SIZE,
                    completed,
                    overallTotal,
                    root,
                    this::publishDownloadProgress);
                verifyKnown(nod, FreewareCatalog.NOD_FILENAME,
                    FreewareCatalog.NOD_SIZE, FreewareCatalog.NOD_MD5);
                completed += FreewareCatalog.NOD_SIZE;

                publishInstalling(R.string.freeware_auto_installing);
                File product = new File(getFilesDir(), "TiberianDawn");
                String error = GameDataImportActivity.installDownloadedGameData(
                    gdi, nod, product);
                if (error != null && !error.trim().isEmpty()) {
                    throw new IOException(error.trim());
                }
                if (!AndroidGameData.isInstalled(this)) {
                    throw new IOException(getString(
                        R.string.import_error_incomplete_install));
                }

                // Baseline ISOs are no longer needed before German overlay work.
                gdi.delete();
                nod.delete();
            }

            if (german) {
                File core = FreewareDownloadClient.downloadKnownIso(
                    FreewareCatalog.GERMAN_CORE_START,
                    FreewareCatalog.GERMAN_CORE_MODDB_FILE_ID,
                    FreewareCatalog.GERMAN_CORE_FILENAME,
                    FreewareCatalog.GERMAN_CORE_SIZE,
                    completed,
                    overallTotal,
                    root,
                    this::publishDownloadProgress);
                verifyKnown(core, FreewareCatalog.GERMAN_CORE_FILENAME,
                    FreewareCatalog.GERMAN_CORE_SIZE,
                    FreewareCatalog.GERMAN_CORE_MD5);
                completed += FreewareCatalog.GERMAN_CORE_SIZE;

                File videos = FreewareDownloadClient.downloadKnownIso(
                    FreewareCatalog.GERMAN_VIDEO_START,
                    FreewareCatalog.GERMAN_VIDEO_MODDB_FILE_ID,
                    FreewareCatalog.GERMAN_VIDEO_FILENAME,
                    FreewareCatalog.GERMAN_VIDEO_SIZE,
                    completed,
                    overallTotal,
                    root,
                    this::publishDownloadProgress);
                verifyKnown(videos, FreewareCatalog.GERMAN_VIDEO_FILENAME,
                    FreewareCatalog.GERMAN_VIDEO_SIZE,
                    FreewareCatalog.GERMAN_VIDEO_MD5);

                publishInstalling(R.string.freeware_german_extracting);
                GermanPackageInstaller.install(this, core, videos, stage -> {
                    if ("extract-core".equals(stage) || "extract-video".equals(stage)) {
                        publishInstalling(R.string.freeware_german_extracting);
                    } else {
                        publishInstalling(R.string.freeware_german_applying);
                    }
                });
                if (!GermanPackageInstaller.isInstalled(this)) {
                    throw new IOException(getString(
                        R.string.freeware_german_incomplete));
                }
                LanguagePreferences.set(this, LanguagePreferences.GERMAN);
            }

            deleteTree(root);
            postSuccess(german);
        } catch (IOException | RuntimeException | UnsatisfiedLinkError error) {
            postFailure(error.getMessage());
        }
    }

    private void verifyKnown(File file, String name, long size, String md5)
            throws IOException {
        publishVerifying(name);
        FreewareIsoVerifier.Result result =
            FreewareIsoVerifier.verifyKnownFile(file, size, md5);
        if (result == FreewareIsoVerifier.Result.VALID) return;

        file.delete();
        if (result == FreewareIsoVerifier.Result.INVALID_SIZE) {
            throw new IOException(getString(
                R.string.import_error_freeware_size, name));
        }
        throw new IOException(getString(
            R.string.import_error_freeware_hash, name));
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

    private void publishInstalling(int statusString) {
        runOnUiThread(() -> {
            if (!canUpdateUi()) return;
            progressStatus.setText(statusString);
            progressBar.setIndeterminate(true);
        });
    }

    private void postSuccess(boolean german) {
        runOnUiThread(() -> {
            if (!canUpdateUi()) return;
            progressBar.setIndeterminate(false);
            progressBar.setProgress(1000);
            progressStatus.setText(german
                ? R.string.freeware_german_complete
                : R.string.freeware_auto_complete);

            new AlertDialog.Builder(this)
                .setTitle(R.string.import_complete_title)
                .setMessage(german
                    ? R.string.freeware_german_complete_message
                    : R.string.freeware_auto_complete_message)
                .setCancelable(false)
                .setPositiveButton(R.string.import_start_game,
                    (dialog, which) -> restartIntoGame())
                .show();
        });
    }

    private void restartIntoGame() {
        Intent intent = new Intent(this, GameDataImportActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_CLEAR_TASK
            | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
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
        germanButton.setEnabled(enabled);
        englishButton.setEnabled(enabled);
        gdiButton.setEnabled(enabled);
        nodButton.setEnabled(enabled);
        backButton.setEnabled(enabled);
    }

    private long requiredFreeBytes(boolean german, boolean baselineInstalled) {
        long required = SAFETY_HEADROOM;
        if (!baselineInstalled) {
            required += FreewareCatalog.TOTAL_DOWNLOAD_SIZE
                + BASE_EXTRACTION_HEADROOM;
        }
        if (german) {
            required += FreewareCatalog.GERMAN_EXTRA_DOWNLOAD_SIZE
                + GERMAN_EXTRACTION_HEADROOM;
        }
        return required;
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
