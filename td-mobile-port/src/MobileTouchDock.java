package org.tiberiandawn.android;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * Collapsible touch-first controls for conventional Android.
 *
 * The permanent bottom bar is intentionally gone. A small gear button stays in
 * the upper-right corner and expands a compact command palette on demand.
 */
public final class MobileTouchDock {
    public interface Callbacks {
        void onBack();
        void onPanChanged(boolean enabled);
        void onCommands();
        void onMore();
        void onZoomOut();
        void onZoomReset();
        void onZoomIn();
    }

    public static final class Labels {
        public final String back;
        public final String pan;
        public final String panActive;
        public final String commands;
        public final String more;
        public final String menu;
        public final String zoom;

        public Labels(String back, String pan, String panActive,
                      String commands, String more, String menu, String zoom) {
            this.back = back;
            this.pan = pan;
            this.panActive = panActive;
            this.commands = commands;
            this.more = more;
            this.menu = menu;
            this.zoom = zoom;
        }
    }

    private final Activity activity;
    private final Callbacks callbacks;
    private final Button menuButton;
    private final LinearLayout palette;
    private final Button backButton;
    private final Button panButton;
    private final Button commandsButton;
    private final Button moreButton;
    private final Button zoomOutButton;
    private final Button zoomResetButton;
    private final Button zoomInButton;
    private final TextView zoomLabel;
    private Labels labels;
    private boolean panActive;
    private boolean expanded;
    private int zoomPercent = 100;
    private final int baseTopMargin;
    private final int baseRightMargin;

    public MobileTouchDock(Activity activity, RelativeLayout parent,
                           Labels labels, Callbacks callbacks) {
        this.activity = activity;
        this.labels = labels;
        this.callbacks = callbacks;
        this.baseTopMargin = dp(8);
        this.baseRightMargin = dp(8);

        menuButton = createButton("⚙");
        menuButton.setTextSize(22.0f);
        menuButton.setMinWidth(dp(48));
        menuButton.setContentDescription(labels.menu);
        menuButton.setTooltipText(labels.menu);
        menuButton.setOnClickListener(v -> setExpanded(!expanded));

        RelativeLayout.LayoutParams menuParams = new RelativeLayout.LayoutParams(
            dp(50), dp(50));
        menuParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        menuParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        menuParams.topMargin = baseTopMargin;
        menuParams.rightMargin = baseRightMargin;
        parent.addView(menuButton, menuParams);

        palette = new LinearLayout(activity);
        palette.setId(View.generateViewId());
        palette.setOrientation(LinearLayout.VERTICAL);
        palette.setGravity(Gravity.CENTER);
        palette.setPadding(dp(6), dp(6), dp(6), dp(6));
        palette.setBackground(containerBackground());
        palette.setElevation(dp(6));
        palette.setVisibility(View.GONE);

        LinearLayout commandRow = row();
        backButton = createButton(labels.back);
        panButton = createButton(labels.pan);
        commandsButton = createButton(labels.commands);
        moreButton = createButton(labels.more);
        backButton.setOnClickListener(v -> {
            collapse();
            callbacks.onBack();
        });
        panButton.setOnClickListener(v -> {
            panActive = !panActive;
            updatePanAppearance();
            callbacks.onPanChanged(panActive);
        });
        commandsButton.setOnClickListener(v -> {
            collapse();
            callbacks.onCommands();
        });
        moreButton.setOnClickListener(v -> {
            collapse();
            callbacks.onMore();
        });
        addCompact(commandRow, backButton);
        addCompact(commandRow, panButton);
        addCompact(commandRow, commandsButton);
        addCompact(commandRow, moreButton);
        palette.addView(commandRow);

        LinearLayout zoomRow = row();
        zoomLabel = new TextView(activity);
        zoomLabel.setTextColor(Color.WHITE);
        zoomLabel.setTextSize(12.0f);
        zoomLabel.setGravity(Gravity.CENTER_VERTICAL);
        zoomLabel.setPadding(dp(8), 0, dp(8), 0);
        zoomRow.addView(zoomLabel, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)));

        zoomOutButton = createButton("−");
        zoomResetButton = createButton("100%");
        zoomInButton = createButton("+");
        zoomOutButton.setContentDescription(labels.zoom + " −");
        zoomResetButton.setContentDescription(labels.zoom + " 100%");
        zoomInButton.setContentDescription(labels.zoom + " +");
        zoomOutButton.setOnClickListener(v -> callbacks.onZoomOut());
        zoomResetButton.setOnClickListener(v -> callbacks.onZoomReset());
        zoomInButton.setOnClickListener(v -> callbacks.onZoomIn());
        addZoom(zoomRow, zoomOutButton, dp(48));
        addZoom(zoomRow, zoomResetButton, dp(66));
        addZoom(zoomRow, zoomInButton, dp(48));
        palette.addView(zoomRow);
        updateZoomAppearance();

        RelativeLayout.LayoutParams paletteParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT);
        paletteParams.addRule(RelativeLayout.BELOW, menuButton.getId());
        paletteParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        paletteParams.topMargin = dp(4);
        paletteParams.rightMargin = baseRightMargin;
        parent.addView(palette, paletteParams);
    }

    public void updateLabels(Labels newLabels) {
        labels = newLabels;
        backButton.setText(labels.back);
        commandsButton.setText(labels.commands);
        moreButton.setText(labels.more);
        menuButton.setContentDescription(labels.menu);
        menuButton.setTooltipText(labels.menu);
        updatePanAppearance();
        updateZoomAppearance();
    }

    public void setPanActive(boolean active) {
        if (panActive == active) return;
        panActive = active;
        updatePanAppearance();
    }

    public void setZoomPercent(int percent) {
        zoomPercent = Math.max(100, Math.min(200, percent));
        updateZoomAppearance();
    }

    public void setVisible(boolean visible) {
        menuButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) {
            expanded = false;
            palette.setVisibility(View.GONE);
        } else if (expanded) {
            palette.setVisibility(View.VISIBLE);
        }
    }

    public void applyInsets(WindowInsets insets) {
        int top = 0;
        int right = 0;
        if (insets != null) {
            top = Math.max(insets.getSystemWindowInsetTop(),
                           insets.getStableInsetTop());
            right = Math.max(insets.getSystemWindowInsetRight(),
                             insets.getStableInsetRight());
        }

        RelativeLayout.LayoutParams menuParams =
            (RelativeLayout.LayoutParams) menuButton.getLayoutParams();
        int wantedTop = baseTopMargin + top;
        int wantedRight = baseRightMargin + right;
        if (menuParams.topMargin != wantedTop || menuParams.rightMargin != wantedRight) {
            menuParams.topMargin = wantedTop;
            menuParams.rightMargin = wantedRight;
            menuButton.setLayoutParams(menuParams);
        }

        RelativeLayout.LayoutParams paletteParams =
            (RelativeLayout.LayoutParams) palette.getLayoutParams();
        if (paletteParams.rightMargin != wantedRight) {
            paletteParams.rightMargin = wantedRight;
            palette.setLayoutParams(paletteParams);
        }
    }

    public void collapse() {
        setExpanded(false);
    }

    private void setExpanded(boolean show) {
        expanded = show;
        palette.setVisibility(show ? View.VISIBLE : View.GONE);
        menuButton.setSelected(show);
        menuButton.setAlpha(show ? 1.0f : 0.78f);
        menuButton.setBackground(buttonBackground(show));
    }

    private void updatePanAppearance() {
        panButton.setText(panActive ? labels.panActive : labels.pan);
        panButton.setBackground(buttonBackground(panActive));
        panButton.setSelected(panActive);
        panButton.setAlpha(panActive ? 1.0f : 0.90f);
    }

    private void updateZoomAppearance() {
        if (zoomLabel == null) return;
        zoomLabel.setText(labels.zoom + " " + zoomPercent + "%");
        zoomResetButton.setText(zoomPercent + "%");
        zoomOutButton.setEnabled(zoomPercent > 100);
        zoomInButton.setEnabled(zoomPercent < 200);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private Button createButton(String label) {
        Button button = new Button(activity);
        button.setId(View.generateViewId());
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12.0f);
        button.setMinHeight(dp(44));
        button.setMinWidth(dp(62));
        button.setPadding(dp(7), 0, dp(7), 0);
        button.setStateListAnimator(null);
        button.setFocusable(false);
        button.setBackground(buttonBackground(false));
        button.setAlpha(0.90f);
        return button;
    }

    private void addCompact(LinearLayout row, Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(44));
        params.setMargins(dp(2), 0, dp(2), 0);
        row.addView(button, params);
    }

    private void addZoom(LinearLayout row, Button button, int width) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, dp(44));
        params.setMargins(dp(2), dp(4), dp(2), 0);
        row.addView(button, params);
    }

    private GradientDrawable containerBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(12));
        background.setColor(Color.argb(222, 18, 21, 23));
        background.setStroke(dp(1), Color.argb(175, 170, 180, 185));
        return background;
    }

    private GradientDrawable buttonBackground(boolean active) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(9));
        background.setColor(active
            ? Color.rgb(30, 118, 61)
            : Color.argb(232, 42, 46, 49));
        background.setStroke(dp(1), active
            ? Color.rgb(135, 235, 158)
            : Color.rgb(128, 136, 142));
        return background;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
