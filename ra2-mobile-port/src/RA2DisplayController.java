package com.winlator;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

public final class RA2DisplayController {
    public interface Listener {
        void onZoomChanged(int percent);
    }

    private static final String PREFS = "ra2_mobile";
    private static final String KEY_ZOOM = "touch_zoom";
    private static final float MIN = 1.0f;
    private static final float MAX = 2.0f;
    private static final float STEP = 0.25f;
    private static final float THRESHOLD = 0.055f;

    private final View gameView;
    private final View touchView;
    private final Listener listener;
    private final SharedPreferences preferences;
    private final ScaleGestureDetector detector;

    private float zoom;
    private float startZoom;
    private float gestureScale = 1f;
    private boolean intercepting;
    private boolean consumeUntilUp;

    public RA2DisplayController(Activity activity, View gameView, View touchView, Listener listener) {
        this.gameView = gameView;
        this.touchView = touchView;
        this.listener = listener;
        this.preferences = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.zoom = clamp(preferences.getFloat(KEY_ZOOM, 1f));

        detector = new ScaleGestureDetector(activity, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                startZoom = zoom;
                gestureScale = 1f;
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                if (Float.isNaN(factor) || Float.isInfinite(factor) || factor <= 0f) return false;
                gestureScale *= factor;
                if (!intercepting && Math.abs((float)Math.log(gestureScale)) >= THRESHOLD) {
                    intercepting = true;
                    consumeUntilUp = true;
                    cancelTouch(detector.getFocusX(), detector.getFocusY());
                }
                if (!intercepting) return false;
                setZoom(startZoom * gestureScale, detector.getFocusX(), detector.getFocusY(), false);
                return true;
            }
        });

        gameView.post(() -> {
            apply(gameView.getWidth() * .5f, gameView.getHeight() * .5f);
            notifyListener();
        });
    }

    public boolean onDispatchTouchEvent(MotionEvent event) {
        detector.onTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            boolean consume = consumeUntilUp;
            if (consume) persist();
            intercepting = false;
            consumeUntilUp = false;
            gestureScale = 1f;
            return consume;
        }
        return consumeUntilUp;
    }

    public void zoomIn() {
        setZoom(Math.round((zoom + STEP) / STEP) * STEP, centerX(), centerY(), true);
    }

    public void zoomOut() {
        setZoom(Math.round((zoom - STEP) / STEP) * STEP, centerX(), centerY(), true);
    }

    public void reset() {
        setZoom(1f, centerX(), centerY(), true);
    }

    public int getPercent() {
        return Math.round(zoom * 100f);
    }

    private void setZoom(float value, float focusX, float focusY, boolean persist) {
        float next = clamp(value);
        if (Math.abs(next - zoom) < .001f) return;
        zoom = next;
        apply(focusX, focusY);
        if (persist) persist();
        notifyListener();
    }

    private void apply(float focusX, float focusY) {
        float x = Math.max(0f, Math.min(gameView.getWidth(), focusX));
        float y = Math.max(0f, Math.min(gameView.getHeight(), focusY));
        for (View view : new View[]{gameView, touchView}) {
            view.setPivotX(x);
            view.setPivotY(y);
            view.setScaleX(zoom);
            view.setScaleY(zoom);
        }
    }

    private void cancelTouch(float x, float y) {
        long now = System.currentTimeMillis();
        MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, x, y, 0);
        touchView.dispatchTouchEvent(cancel);
        cancel.recycle();
    }

    private void persist() {
        preferences.edit().putFloat(KEY_ZOOM, zoom).apply();
    }

    private void notifyListener() {
        if (listener != null) listener.onZoomChanged(getPercent());
    }

    private float centerX() { return gameView.getWidth() * .5f; }
    private float centerY() { return gameView.getHeight() * .5f; }

    private static float clamp(float value) {
        return Math.max(MIN, Math.min(MAX, value));
    }
}
