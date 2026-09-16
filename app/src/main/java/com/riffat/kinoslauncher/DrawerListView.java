package com.riffat.kinoslauncher;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ListView;

/**
 * Fullscreen app-drawer list with pull-down-to-close: when the content is
 * already scrolled to the very top and the user drags downward past a
 * threshold, the drawer asks to be closed. Normal scrolling is untouched.
 */
public class DrawerListView extends ListView {

    private static final float PULL_CLOSE_DP = 120f;

    private OnPullDownListener pullDownListener;
    private float downY = 0f;
    private boolean tracking = false;
    private boolean fired = false;

    public interface OnPullDownListener {
        void onPullDownToClose();
    }

    public DrawerListView(Context context) {
        super(context);
    }

    public DrawerListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setPullDownListener(OnPullDownListener listener) {
        this.pullDownListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = ev.getY();
                tracking = true;
                fired = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (tracking && !fired) {
                    final float dy = ev.getY() - downY;
                    if (dy > dp(PULL_CLOSE_DP) && isAtTop()) {
                        fired = true;
                        tracking = false;
                        if (pullDownListener != null) {
                            pullDownListener.onPullDownToClose();
                        }
                        return true;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                tracking = false;
                fired = false;
                break;
            default:
                break;
        }
        return super.onTouchEvent(ev);
    }

    /** True when the list cannot scroll up any further. */
    private boolean isAtTop() {
        if (getChildCount() == 0) return true;
        return getFirstVisiblePosition() == 0
                && getChildAt(0).getTop() >= getPaddingTop() - 2;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
