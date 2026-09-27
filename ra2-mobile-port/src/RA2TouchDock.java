package com.winlator;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class RA2TouchDock {
    public interface Callbacks {
        void onBack();
        void onRightClick();
        void onZoomOut();
        void onZoomReset();
        void onZoomIn();
    }

    private final Activity activity;
    private final Callbacks callbacks;
    private final Button menuButton;
    private final LinearLayout palette;
    private final TextView zoomLabel;
    private final int margin;
    private int zoomPercent = 100;
    private boolean expanded;

    public RA2TouchDock(Activity activity, FrameLayout parent, boolean german, Callbacks callbacks) {
        this.activity = activity;
        this.callbacks = callbacks;
        this.margin = dp(9);

        menuButton = button("⚙");
        menuButton.setTextSize(22);
        menuButton.setContentDescription(german ? "Spielsteuerung öffnen" : "Open game controls");
        menuButton.setOnClickListener(v -> setExpanded(!expanded));

        FrameLayout.LayoutParams gearParams = new FrameLayout.LayoutParams(dp(50), dp(50), Gravity.TOP | Gravity.END);
        gearParams.setMargins(0, margin, margin, 0);
        parent.addView(menuButton, gearParams);

        palette = new LinearLayout(activity);
        palette.setOrientation(LinearLayout.HORIZONTAL);
        palette.setGravity(Gravity.CENTER_VERTICAL);
        palette.setPadding(dp(6), dp(5), dp(6), dp(5));
        palette.setBackground(panelBackground());
        palette.setVisibility(View.GONE);

        Button back = button(german ? "Zurück" : "Back");
        Button right = button(german ? "Rechts" : "Right");
        Button minus = button("−");
        Button reset = button("100%");
        Button plus = button("+");
        zoomLabel = new TextView(activity);
        zoomLabel.setTextColor(Color.WHITE);
        zoomLabel.setTextSize(12);
        zoomLabel.setPadding(dp(6), 0, dp(6), 0);

        back.setOnClickListener(v -> { collapse(); callbacks.onBack(); });
        right.setOnClickListener(v -> callbacks.onRightClick());
        minus.setOnClickListener(v -> callbacks.onZoomOut());
        reset.setOnClickListener(v -> callbacks.onZoomReset());
        plus.setOnClickListener(v -> callbacks.onZoomIn());

        add(palette, back, dp(76));
        add(palette, right, dp(76));
        palette.addView(zoomLabel, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)));
        add(palette, minus, dp(48));
        add(palette, reset, dp(64));
        add(palette, plus, dp(48));

        FrameLayout.LayoutParams paletteParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        paletteParams.setMargins(0, dp(64), margin, 0);
        parent.addView(palette, paletteParams);
        setZoomPercent(100);
    }

    public void setZoomPercent(int percent) {
        zoomPercent = Math.max(100, Math.min(200, percent));
        zoomLabel.setText("Zoom " + zoomPercent + "%");
    }

    public void applyInsets(WindowInsets insets) {
        if (insets == null) return;
        int top = Math.max(insets.getSystemWindowInsetTop(), insets.getStableInsetTop());
        int right = Math.max(insets.getSystemWindowInsetRight(), insets.getStableInsetRight());

        FrameLayout.LayoutParams gear = (FrameLayout.LayoutParams) menuButton.getLayoutParams();
        gear.topMargin = margin + top;
        gear.rightMargin = margin + right;
        menuButton.setLayoutParams(gear);

        FrameLayout.LayoutParams panel = (FrameLayout.LayoutParams) palette.getLayoutParams();
        panel.topMargin = dp(64) + top;
        panel.rightMargin = margin + right;
        palette.setLayoutParams(panel);
    }

    public void collapse() {
        setExpanded(false);
    }

    private void setExpanded(boolean show) {
        expanded = show;
        palette.setVisibility(show ? View.VISIBLE : View.GONE);
        menuButton.setAlpha(show ? 1f : 0.78f);
    }

    private Button button(String text) {
        Button b = new Button(activity);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12);
        b.setPadding(dp(6), 0, dp(6), 0);
        b.setMinHeight(dp(44));
        b.setStateListAnimator(null);
        b.setBackground(buttonBackground());
        return b;
    }

    private void add(LinearLayout row, View view, int width) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, dp(44));
        p.setMargins(dp(2), 0, dp(2), 0);
        row.addView(view, p);
    }

    private GradientDrawable panelBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(Color.argb(232, 12, 15, 17));
        bg.setStroke(dp(1), Color.rgb(130, 140, 145));
        return bg;
    }

    private GradientDrawable buttonBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(8));
        bg.setColor(Color.argb(235, 45, 49, 52));
        bg.setStroke(dp(1), Color.rgb(115, 123, 128));
        return bg;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
