package org.tiberiandawn.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Guided no-disc path for users who do not already have C&C Gold ISO images. */
public final class FreewareDataActivity extends Activity {
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LanguagePreferences.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createView());
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

        Button gdi = new Button(this);
        gdi.setAllCaps(false);
        gdi.setText(R.string.freeware_download_gdi);
        gdi.setOnClickListener(v -> openPage(FreewareCatalog.GDI_PAGE));
        content.addView(gdi, params(dp(18)));

        Button nod = new Button(this);
        nod.setAllCaps(false);
        nod.setText(R.string.freeware_download_nod);
        nod.setOnClickListener(v -> openPage(FreewareCatalog.NOD_PAGE));
        content.addView(nod, params(dp(8)));

        TextView details = new TextView(this);
        details.setText(getString(R.string.freeware_expected_files,
            FreewareCatalog.GDI_FILENAME, FreewareCatalog.NOD_FILENAME));
        details.setTextSize(14.0f);
        content.addView(details, params(dp(18)));

        Button back = new Button(this);
        back.setAllCaps(false);
        back.setText(R.string.freeware_back_to_import);
        back.setOnClickListener(v -> finish());
        content.addView(back, params(dp(20)));

        scroll.addView(content, new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT,
            ScrollView.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private void openPage(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try {
            startActivity(intent);
        } catch (RuntimeException error) {
            new AlertDialog.Builder(this)
                .setTitle(R.string.freeware_open_error_title)
                .setMessage(R.string.freeware_open_error)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        }
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
}
