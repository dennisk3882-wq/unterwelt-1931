package org.tiberiandawn.android;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * Phone/tablet viewport zoom for the SDL surface.
 *
 * Pinch zoom is deliberately separated from the game's existing two-finger pan:
 * ordinary two-finger drags continue to reach the game. Only after the finger
 * spacing changes enough to be an intentional pinch does this controller cancel
 * the native gesture and take over until all fingers are released.
 */
public final class MobileDisplayController {
    public interface Listener {
        void onZoomChanged(int percent);
    }

    private static final String PREFS = "mobile_display";
    private static final String KEY_ZOOM = "zoom";
    private static final float MIN_ZOOM = 1.0f;
    private static final float MAX_ZOOM = 2.0f;
    private static final float STEP = 0.25f;
    private static final float PINCH_THRESHOLD = 0.06f;

    private final Activity activity;
    private final View surface;
    private final Listener listener;
    private final SharedPreferences preferences;
    private final ScaleGestureDetector scaleDetector;

    private float zoom;
    private float gestureStartZoom;
    private float gestureScale = 1.0f;
    private boolean intercepting;
    private boolean consumeUntilGestureEnds;

    public MobileDisplayController(Activity activity, View surface, Listener listener) {
        this.activity = activity;
        this.surface = surface;
        this.listener = listener;
        this.preferences = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.zoom = clamp(preferences.getFloat(KEY_ZOOM, MIN_ZOOM));

        this.scaleDetector = new ScaleGestureDetector(activity,
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScaleBegin(ScaleGestureDetector detector) {
                    gestureStartZoom = zoom;
                    gestureScale = 1.0f;
                    return true;
                }

                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    float factor = detector.getScaleFactor();
                    if (Float.isNaN(factor) || Float.isInfinite(factor) || factor <= 0.0f) {
                        return false;
                    }
                    gestureScale *= factor;

                    if (!intercepting
                            && Math.abs((float)Math.log(gestureScale)) >= PINCH_THRESHOLD) {
                        intercepting = true;
                        consumeUntilGestureEnds = true;
                    }
                    if (!intercepting) return false;

                    setZoomInternal(gestureStartZoom * gestureScale,
                        detector.getFocusX(), detector.getFocusY(), false);
                    return true;
                }
            });

        surface.post(() -> {
            applyZoom(surface.getWidth() * 0.5f, surface.getHeight() * 0.5f);
            notifyZoom();
        });
    }

    /**
     * Call from Activity.dispatchTouchEvent before dispatching to SDL.
     * Returns true only while an intentional pinch owns the gesture.
     */
    public boolean onDispatchTouchEvent(MotionEvent event) {
        if (event == null) return false;

        boolean wasIntercepting = intercepting;
        scaleDetector.onTouchEvent(event);

        if (!wasIntercepting && intercepting) {
            cancelNativeGesture(event);
        }

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            boolean consume = consumeUntilGestureEnds;
            finishGesture();
            return consume;
        }

        if (action == MotionEvent.ACTION_POINTER_UP && event.getPointerCount() <= 2) {
            boolean consume = consumeUntilGestureEnds;
            if (consume) {
                // Keep swallowing the remaining finger until ACTION_UP so the
                // native game cannot interpret it as a fresh tap after pinch.
                intercepting = false;
            }
            return consume;
        }

        return consumeUntilGestureEnds;
    }

    public void zoomIn() {
        setZoomInternal(roundedStep(zoom + STEP),
            surface.getWidth() * 0.5f, surface.getHeight() * 0.5f, true);
    }

    public void zoomOut() {
        setZoomInternal(roundedStep(zoom - STEP),
            surface.getWidth() * 0.5f, surface.getHeight() * 0.5f, true);
    }

    public void resetZoom() {
        setZoomInternal(MIN_ZOOM,
            surface.getWidth() * 0.5f, surface.getHeight() * 0.5f, true);
    }

    public int getZoomPercent() {
        return Math.round(zoom * 100.0f);
    }

    private void setZoomInternal(float requested, float focusX, float focusY,
                                 boolean persistImmediately) {
        float next = clamp(requested);
        if (Math.abs(next - zoom) < 0.001f) return;
        zoom = next;
        applyZoom(focusX, focusY);
        notifyZoom();
        if (persistImmediately) persist();
    }

    private void applyZoom(float focusX, float focusY) {
        if (surface.getWidth() <= 0 || surface.getHeight() <= 0) return;
        float pivotX = Math.max(0.0f, Math.min(surface.getWidth(), focusX));
        float pivotY = Math.max(0.0f, Math.min(surface.getHeight(), focusY));
        surface.setPivotX(pivotX);
        surface.setPivotY(pivotY);
        surface.setScaleX(zoom);
        surface.setScaleY(zoom);
    }

    private void cancelNativeGesture(MotionEvent source) {
        MotionEvent cancel = MotionEvent.obtain(source);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        surface.dispatchTouchEvent(cancel);
        cancel.recycle();
    }

    private void finishGesture() {
        if (consumeUntilGestureEnds) persist();
        intercepting = false;
        consumeUntilGestureEnds = false;
        gestureScale = 1.0f;
    }

    private void persist() {
        preferences.edit().putFloat(KEY_ZOOM, zoom).apply();
    }

    private void notifyZoom() {
        if (listener != null) listener.onZoomChanged(getZoomPercent());
    }

    private static float roundedStep(float value) {
        return Math.round(value / STEP) * STEP;
    }

    private static float clamp(float value) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, value));
    }
}
