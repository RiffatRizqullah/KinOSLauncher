package com.riffat.kinoslauncher;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Full-screen scrim behind the home UI. It darkens the wallpaper while the dial
 * is open so app chips stay readable. It never consumes touches: the parent dial
 * view owns all gesture routing, and the ListView on top must receive its own
 * scroll/tap events.
 */
public class HomeOverlayView extends View {

    public HomeOverlayView(Context context) {
        this(context, null);
    }

    public HomeOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(false);
        setClickable(false);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Never consume: let the parent KinOSDialSelectorView handle everything.
        return false;
    }
}
