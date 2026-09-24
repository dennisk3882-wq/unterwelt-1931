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

/** Compact phone/tablet command dock for the conventional Android flavor. */
public final class MobileTouchDock {
    public interface Callbacks {
        void onBack();
        void onPanChanged(boolean enabled);
        void onCommands();
        void onMore();
    }

    public static final class Labels {
        public final String back;
        public final String pan;
        public final String panActive;
        public final String commands;
        public final String more;

        public Labels(String back, String pan, String panActive,
                      String commands, String more) {
            this.back = back;
            this.pan = pan;
            this.panActive = panActive;
            this.commands = commands;
            this.more = more;
        }
    }

    private final Activity activity;
    private final Callbacks callbacks;
    private final LinearLayout dock;
    private final Button backButton;
    private final Button panButton;
    private final Button commandsButton;
    private final Button moreButton;
    private Labels labels;
    private boolean panActive;
    private final int baseBottomMargin;

    public MobileTouchDock(Activity activity, RelativeLayout parent,
                           Labels labels, Callbacks callbacks) {
        this.activity = activity;
        this.labels = labels;
        this.callbacks = callbacks;
        this.baseBottomMargin = dp(10);

        dock = new LinearLayout(activity);
        dock.setId(View.generateViewId());
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(6), dp(4), dp(6), dp(4));
        dock.setBackground(containerBackground());
        dock.setElevation(dp(5));

        backButton = createButton(labels.back);
        panButton = createButton(labels.pan);
        commandsButton = createButton(labels.commands);
        moreButton = createButton(labels.more);

        backButton.setOnClickListener(v -> callbacks.onBack());
        panButton.setOnClickListener(v -> {
            panActive = !panActive;
            updatePanAppearance();
            callbacks.onPanChanged(panActive);
        });
        commandsButton.setOnClickListener(v -> callbacks.onCommands());
        moreButton.setOnClickListener(v -> callbacks.onMore());

        addButton(backButton);
        addButton(panButton);
        addButton(commandsButton);
        addButton(moreButton);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        params.addRule(RelativeLayout.CENTER_HORIZONTAL);
        params.bottomMargin = baseBottomMargin;
        parent.addView(dock, params);
    }

    public void updateLabels(Labels newLabels) {
        labels = newLabels;
        backButton.setText(labels.back);
        commandsButton.setText(labels.commands);
        moreButton.setText(labels.more);
        updatePanAppearance();
    }

    public void setPanActive(boolean active) {
        if (panActive == active) return;
        panActive = active;
        updatePanAppearance();
    }

    public void setVisible(boolean visible) {
        dock.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void applyInsets(WindowInsets insets) {
        int bottom = 0;
        if (insets != null) {
            bottom = Math.max(insets.getSystemWindowInsetBottom(),
                              insets.getStableInsetBottom());
        }
        RelativeLayout.LayoutParams params =
            (RelativeLayout.LayoutParams) dock.getLayoutParams();
        int wanted = baseBottomMargin + bottom;
        if (params.bottomMargin != wanted) {
            params.bottomMargin = wanted;
            dock.setLayoutParams(params);
        }
    }

    private void updatePanAppearance() {
        panButton.setText(panActive ? labels.panActive : labels.pan);
        panButton.setBackground(buttonBackground(panActive));
        panButton.setSelected(panActive);
        panButton.setAlpha(panActive ? 1.0f : 0.90f);
    }

    private Button createButton(String label) {
        Button button = new Button(activity);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13.0f);
        button.setMinHeight(dp(48));
        button.setMinWidth(dp(72));
        button.setPadding(dp(9), 0, dp(9), 0);
        button.setStateListAnimator(null);
        button.setFocusable(false);
        button.setBackground(buttonBackground(false));
        return button;
    }

    private void addButton(Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(48));
        params.setMargins(dp(3), 0, dp(3), 0);
        dock.addView(button, params);
    }

    private GradientDrawable containerBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(12));
        background.setColor(Color.argb(205, 18, 21, 23));
        background.setStroke(dp(1), Color.argb(160, 170, 180, 185));
        return background;
    }

    private GradientDrawable buttonBackground(boolean active) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(8));
        background.setColor(active
            ? Color.rgb(30, 118, 61)
            : Color.argb(235, 42, 46, 49));
        background.setStroke(dp(1), active
            ? Color.rgb(135, 235, 158)
            : Color.rgb(128, 136, 142));
        return background;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
