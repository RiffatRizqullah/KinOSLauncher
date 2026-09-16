package com.riffat.kinoslauncher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * KinOS touch selector.
 *
 * <p>The home screen is empty (only a centered clock). The first (primary) touch
 * opens a quick-launch dial centered on that touch point: an opaque black circle
 * with plain app icons (from the user's KinOS Settings favorites) spread over
 * the full 360 degrees.</p>
 *
 * <p>A ring pointer (bg_dial_thumb) follows the primary touch 1:1 while the dial
 * is open. Each
 * slot has three animated states: idle (tucked behind the circle, ~10% peeking
 * out, rotated tangent to the circle), hovered (enlarged, upright, slid outside
 * the circle, title shown at the dial center), and selected (others hide, the
 * circle grows 1.5x, the icon flies to the center at 3x before the app
 * launches). Dragging the ring downward opens
 * the fullscreen A-Z app drawer instead. No drag-velocity, fling or momentum
 * is used anywhere.</p>
 */
public class KinOSDialSelectorView extends FrameLayout {

    // ---- Layout metrics ---------------------------------------------------------------
    private static final float QUICK_ICON_DP = 52f;
    /** Ring pointer (bg_dial_thumb) diameter: enlarged twice by 25% (52 -> 65 -> 81). */
    private static final float BLOB_SIZE_DP = 81f;
    /** Whole-dial scale: circle + orbit shrink to 3/4, proportions preserved. */
    private static final float DIAL_SCALE = 0.75f;
    private static final float QUICK_SELECT_RADIUS_DP = 44f;
    private static final float DRAWER_TRIGGER_DP = 120f;
    private static final float CLAMP_MARGIN_DP = 16f;
    private static final float DRAWER_FADE_DP = 56f;
    private static final float DRAWER_PADDING_START_DP = 16f;
    private static final float DRAWER_PADDING_VERTICAL_DP = 24f;
    private static final float HOVER_GAP_DP = 8f;
    /** Fraction of the icon diameter peeking outside the circle when idle. */
    private static final float IDLE_PEEK_FRACTION = 0.1f;
    private static final float HOVER_SCALE = 1.25f;
    private static final float SELECT_SCALE = 3f;
    /** Ring pointer growth while hovering an app. */
    private static final float BLOB_HOVER_SCALE = 1.1f;
    /** Circle growth factor during the launch animation. */
    private static final float SELECT_CIRCLE_SCALE = 1.5f;
    private static final float DIAL_TITLE_SP = 22f;

    /** Start of the slot circle, degrees (screen coords: 0 = right, 90 = down). */
    private static final float ALLOWED_START_DEG = 270f;
    /** Slots fill the full circle; no bottom exclusion zone. */
    private static final float ALLOWED_SWEEP_DEG = 360f;

    // ---- Opening animation ------------------------------------------------------------
    private static final long DIAL_FADE_IN_MS = 150;
    private static final long DIAL_FADE_OUT_MS = 150;
    private static final long RESET_DELAY_MS = 250L;
    private static final long HOVER_MS = 180L;
    private static final long SELECT_MS = 220L;
    /** Hold-this-long to open the dial; a direct drag opens it immediately. */
    private static final long HOLD_DELAY_MS = 100L;
    private static final long GROW_MS = 250L;

    private static final int INVALID_POINTER = -1;

    // ---- Model ------------------------------------------------------------------------
    private final AppListModel model = new AppListModel();
    private final List<AppItem> favorites = new ArrayList<>();

    // ---- Selection / state ------------------------------------------------------------
    private boolean dialOpen = false;
    private boolean drawerOpen = false;
    private boolean selecting = false;
    private boolean loading = false;
    private boolean appsReady = false;
    private boolean gestureHintFired = false;
    private boolean titleHiding = false;
    private int activePointerId = INVALID_POINTER;
    private float downX = 0f;
    private float downY = 0f;
    private float pointerX = 0f;
    private float pointerY = 0f;
    private float dialCx = 0f;
    private float dialCy = 0f;
    private int highlightedIndex = -1;
    private AppItem selectedApp = null;
    private ValueAnimator fadeAnim;

    // ---- Views ------------------------------------------------------------------------
    private final HomeOverlayView scrim;
    private final FrameLayout quickLayer;
    private final View circleBg;
    private final TextView dialTitle;
    private final View blob;
    private final DrawerListView drawerList;
    private final AppDrawerAdapter drawerAdapter;
    private final List<Slot> slots = new ArrayList<>();

    // ---- Helpers ----------------------------------------------------------------------
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable resetRunnable = new Runnable() {
        @Override
        public void run() {
            resetAll();
        }
    };
    private final Runnable selectRunnable = new Runnable() {
        @Override
        public void run() {
            if (!selecting) return;
            selecting = false;
            final AppItem app = selectedApp;
            selectedApp = null;
            if (app != null && launchListener != null) {
                launchListener.onAppLaunched(app);
            }
            closeAll();
        }
    };
    /** Opens the dial after a hold; cancelled by drag-open, lift or cancel. */
    private final Runnable pendingOpenRunnable = new Runnable() {
        @Override
        public void run() {
            if (activePointerId == INVALID_POINTER
                    || dialOpen || drawerOpen || selecting) {
                return;
            }
            openDialAt(downX, downY);
        }
    };

    // ---- Callbacks --------------------------------------------------------------------
    private LaunchListener launchListener;
    private HomeListener homeListener;
    private HintsShownListener hintsShownListener;
    private DialListener dialListener;
    private DrawerListener drawerListener;

    /** Notifies when a quick-launch or drawer app is chosen. */
    public interface LaunchListener {
        void onAppLaunched(AppItem app);
    }

    /** Notifies about home-level gestures (wallpaper picker etc.). */
    public interface HomeListener {
        void onHomeLongPress();
    }

    /** Notifies when the user has performed a dial gesture at least once. */
    public interface HintsShownListener {
        void onHintsShown();
    }

    /** Notifies when the dial selector is shown / hidden. */
    public interface DialListener {
        void onDialOpened();
        void onDialClosed();
    }

    /** Notifies about the fullscreen drawer and its settings entry. */
    public interface DrawerListener {
        void onDrawerOpened();
        void onDrawerClosed();
        void onSettingsRequested();
    }

    /** One quick-launch slot with its idle/hover geometry. */
    private static final class Slot {
        final ImageView view;
        final float idleX;
        final float idleY;
        final float idleRotation;
        final float outX;
        final float outY;
        /** Bumps on every new animation; stale end-listeners check it before touching Z. */
        long animSeq = 0L;

        Slot(ImageView view, float idleX, float idleY, float idleRotation,
                float outX, float outY) {
            this.view = view;
            this.idleX = idleX;
            this.idleY = idleY;
            this.idleRotation = idleRotation;
            this.outX = outX;
            this.outY = outY;
        }
    }

    public KinOSDialSelectorView(Context context) {
        this(context, null);
    }

    public KinOSDialSelectorView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KinOSDialSelectorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        scrim = new HomeOverlayView(getContext());
        quickLayer = new FrameLayout(getContext());
        circleBg = new View(getContext());
        dialTitle = new TextView(getContext());
        blob = new View(getContext());
        drawerList = new DrawerListView(getContext());
        drawerAdapter = new AppDrawerAdapter(getContext());
        initializeViews();
    }

    private void initializeViews() {
        // Scrim sits behind everything; it must not eat touches (see HomeOverlayView).
        scrim.setAlpha(0f);
        addView(scrim,
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // Quick-launch layer: full-screen, transparent, never consumes touches itself.
        // Holds the black circle, the dial-relative title and the plain icons.
        quickLayer.setClickable(false);
        quickLayer.setFocusable(false);
        quickLayer.setAlpha(0f);
        quickLayer.setVisibility(GONE);
        addView(quickLayer,
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // Opaque black circle behind the dial so icons and title stay readable.
        final GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xFF000000);
        circleBg.setBackground(circle);
        circleBg.setClickable(false);
        circleBg.setFocusable(false);

        // Dial-relative title at the circle center, shown while hovering a slot.
        dialTitle.setGravity(Gravity.CENTER);
        dialTitle.setTextColor(0xFFFFFFFF);
        dialTitle.setTextSize(DIAL_TITLE_SP);
        dialTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        dialTitle.setShadowLayer(6f, 0f, 1f, 0x80000000);
        dialTitle.setSingleLine(true);
        dialTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        dialTitle.setVisibility(GONE);
        dialTitle.setClickable(false);
        dialTitle.setFocusable(false);

        // Ring pointer (bg_dial_thumb): follows the primary touch 1:1, only while
        // the dial is open. It is a direct child (never inside quickLayer) so
        // rebuilding slots can never reset its position. Placed at layout (0, 0);
        // all movement goes through translationX/Y set by moveBlobTo().
        final int blobPx = dp(BLOB_SIZE_DP);
        blob.setBackgroundResource(R.drawable.bg_dial_thumb);
        blob.setVisibility(GONE);
        blob.setClickable(false);
        blob.setFocusable(false);
        // Center pivot so hover scaling grows around the touch point, not a corner.
        blob.setPivotX(blobPx / 2f);
        blob.setPivotY(blobPx / 2f);
        addView(blob, new LayoutParams(blobPx, blobPx, Gravity.TOP | Gravity.START));

        // Fullscreen drawer: hidden until the blob is dragged downward.
        // Transparent background: readability comes from the scrim behind it.
        // Soft top/bottom mask via content fading edges, so it stays correct
        // over the live wallpaper.
        drawerList.setAdapter(drawerAdapter);
        drawerList.setVisibility(GONE);
        drawerList.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        drawerList.setVerticalFadingEdgeEnabled(true);
        drawerList.setFadingEdgeLength(dp(DRAWER_FADE_DP));
        drawerList.setClipToPadding(false);
        drawerList.setPadding(dp(DRAWER_PADDING_START_DP), dp(DRAWER_PADDING_VERTICAL_DP),
                0, dp(DRAWER_PADDING_VERTICAL_DP));
        drawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (drawerAdapter.isSettingsRow(position)) {
                    if (drawerListener != null) {
                        drawerListener.onSettingsRequested();
                    }
                    return;
                }
                final AppItem app = drawerAdapter.getAppItem(position);
                if (app != null && launchListener != null) {
                    launchListener.onAppLaunched(app);
                }
                closeAll();
            }
        });
        drawerList.setPullDownListener(new DrawerListView.OnPullDownListener() {
            @Override
            public void onPullDownToClose() {
                if (drawerOpen && !selecting) {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    closeAll();
                }
            }
        });
        addView(drawerList,
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));
    }

    // ---- Public API -------------------------------------------------------------------

    /** Sets the launch callback. Must be called before the dial is used. */
    public void setLaunchListener(LaunchListener listener) {
        this.launchListener = listener;
    }

    /** Sets the home-level gesture callback. */
    public void setHomeListener(HomeListener listener) {
        this.homeListener = listener;
    }

    /** Sets the callback fired once the user performs a dial gesture. */
    public void setHintsShownListener(HintsShownListener listener) {
        this.hintsShownListener = listener;
    }

    /** Sets the callback fired when the dial selector is shown / hidden. */
    public void setDialListener(DialListener listener) {
        this.dialListener = listener;
    }

    /** Sets the callback fired for the fullscreen drawer. */
    public void setDrawerListener(DrawerListener listener) {
        this.drawerListener = listener;
    }

    /** Kicks off background app loading; cheap and safe to call repeatedly. */
    public void prepare() {
        if (loading || appsReady) return;
        loading = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                model.load(getContext());
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        loading = false;
                        appsReady = true;
                        rebuildContent();
                    }
                });
            }
        }).start();
    }

    /** Reloads the app list in the background (picks up installs/uninstalls). */
    public void refresh() {
        if (loading) return;
        loading = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                model.load(getContext());
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        loading = false;
                        appsReady = true;
                        rebuildContent();
                    }
                });
            }
        }).start();
    }

    /** Re-reads favorites from settings and refreshes visible slots. */
    public void reloadFavorites() {
        favorites.clear();
        favorites.addAll(QuickLaunchStore.resolveFavorites(
                model, QuickLaunchStore.getFavorites(getContext())));
        if (dialOpen && !drawerOpen && !selecting) {
            layoutSlots();
        }
    }

    /** Returns true when the dial is currently interactive. */
    public boolean isDialOpen() {
        return dialOpen;
    }

    /** Returns true when the fullscreen drawer is visible. */
    public boolean isDrawerOpen() {
        return drawerOpen;
    }

    /** Closes the drawer and/or dial without launching anything. */
    public void close() {
        closeAll();
    }

    // =====================================================================================
    // Touch handling: primary-touch position tracking only (no velocity / fling)
    // =====================================================================================

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // While the dial is open (no drawer) we own all touches so the blob can be
        // tracked 1:1. When the drawer is visible it handles its own scrolling/taps.
        // On the empty home we never intercept: onTouchEvent opens the dial.
        if (dialOpen && !drawerOpen) {
            return true;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // A select animation owns the gesture until it fires.
        if (selecting) return true;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (drawerOpen) {
                    return false;
                }
                activePointerId = event.getPointerId(0);
                downX = pointerX = event.getX();
                downY = pointerY = event.getY();
                mainHandler.removeCallbacks(resetRunnable);
                mainHandler.removeCallbacks(pendingOpenRunnable);
                // Preload so the dial is ready when the hold expires.
                prepare();
                // Hold opens after a delay; a direct drag opens immediately below.
                mainHandler.postDelayed(pendingOpenRunnable, HOLD_DELAY_MS);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                final int index = event.findPointerIndex(activePointerId);
                if (index < 0) return true;
                pointerX = event.getX(index);
                pointerY = event.getY(index);
                if (!dialOpen && !drawerOpen) {
                    // Pre-open: dragging past slop opens the dial at once
                    // (responsive); holding still waits for the hold delay.
                    final float dx = pointerX - downX;
                    final float dy = pointerY - downY;
                    final int slop = touchSlopPx();
                    if (dx * dx + dy * dy > slop * slop) {
                        mainHandler.removeCallbacks(pendingOpenRunnable);
                        openDialAt(downX, downY);
                    } else {
                        return true;
                    }
                }
                if (!dialOpen || drawerOpen) return true;
                moveBlobTo(pointerX, pointerY);
                updateHover();
                if (pointerY - downY > drawerTriggerPx()) {
                    openDrawer();
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                final int index = event.findPointerIndex(activePointerId);
                if (index >= 0) {
                    pointerX = event.getX(index);
                    pointerY = event.getY(index);
                }
                activePointerId = INVALID_POINTER;
                mainHandler.removeCallbacks(pendingOpenRunnable);
                if (drawerOpen) {
                    // Gesture ended after the drawer opened: stay in the drawer.
                    return true;
                }
                // Tap or too-short hold: the dial never opened, nothing to do.
                if (!dialOpen) return true;
                moveBlobTo(pointerX, pointerY);
                updateHover();
                if (highlightedIndex >= 0 && highlightedIndex < favorites.size()) {
                    startSelect(highlightedIndex);
                } else {
                    closeAll();
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                // Only the primary pointer matters; extra fingers are ignored unless
                // the primary one lifts, which ends the gesture like ACTION_UP.
                final int liftedId = event.getPointerId(event.getActionIndex());
                if (liftedId != activePointerId) return true;
                activePointerId = INVALID_POINTER;
                mainHandler.removeCallbacks(pendingOpenRunnable);
                if (drawerOpen) return true;
                if (!dialOpen) return true;
                if (highlightedIndex >= 0 && highlightedIndex < favorites.size()) {
                    startSelect(highlightedIndex);
                } else {
                    closeAll();
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                activePointerId = INVALID_POINTER;
                mainHandler.removeCallbacks(pendingOpenRunnable);
                if (drawerOpen) return true;
                if (dialOpen) {
                    closeAll();
                }
                return true;
            }
            default:
                return true;
        }
    }

    // =====================================================================================
    // Open / close
    // =====================================================================================

    private void openDialAt(float x, float y) {
        if (dialOpen) return;
        if (!appsReady) {
            prepare();
        }
        reloadFavorites();
        dialOpen = true;
        drawerOpen = false;
        selecting = false;
        mainHandler.removeCallbacks(selectRunnable);
        selectedApp = null;
        highlightedIndex = -1;
        drawerList.setVisibility(GONE);
        mainHandler.removeCallbacks(resetRunnable);
        if (!gestureHintFired) {
            gestureHintFired = true;
            if (hintsShownListener != null) hintsShownListener.onHintsShown();
        }
        final float[] clamped = clampCenter(x, y);
        dialCx = clamped[0];
        dialCy = clamped[1];
        quickLayer.setVisibility(VISIBLE);
        layoutSlots();
        // Grow out of the finger: pivot at the dial center, 1% -> full,
        // running together with the fade-in below.
        quickLayer.animate().cancel();
        quickLayer.setPivotX(dialCx);
        quickLayer.setPivotY(dialCy);
        quickLayer.setScaleX(0.01f);
        quickLayer.setScaleY(0.01f);
        quickLayer.animate().scaleX(1f).scaleY(1f)
                .setDuration(GROW_MS)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .setListener(null)
                .start();
        moveBlobTo(pointerX, pointerY);
        blob.setVisibility(VISIBLE);
        blob.setScaleX(0.5f);
        blob.setScaleY(0.5f);
        blob.animate().scaleX(1f).scaleY(1f).setDuration(150).setListener(null).start();
        if (dialListener != null) {
            dialListener.onDialOpened();
        }
        fadeDial(true);
    }

    private void openDrawer() {
        if (!dialOpen || drawerOpen) return;
        drawerOpen = true;
        clearHover();
        blob.setVisibility(GONE);
        if (fadeAnim != null) {
            fadeAnim.cancel();
        }
        quickLayer.animate().cancel();
        quickLayer.animate().alpha(0f).setDuration(150)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (drawerOpen) {
                            quickLayer.setVisibility(GONE);
                        }
                    }
                }).start();
        drawerAdapter.setApps(new ArrayList<>(model.apps));
        drawerList.setVisibility(VISIBLE);
        drawerList.setAlpha(0f);
        drawerList.animate().alpha(1f).setDuration(200).setListener(null).start();
        drawerList.bringToFront();
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (drawerListener != null) {
            drawerListener.onDrawerOpened();
        }
    }

    private void closeAll() {
        activePointerId = INVALID_POINTER;
        selecting = false;
        mainHandler.removeCallbacks(selectRunnable);
        mainHandler.removeCallbacks(pendingOpenRunnable);
        selectedApp = null;
        highlightedIndex = -1;
        final boolean wasDrawer = drawerOpen;
        final boolean wasDial = dialOpen;
        drawerOpen = false;
        dialOpen = false;
        blob.setVisibility(GONE);
        drawerList.animate().cancel();
        if (wasDrawer) {
            // Fade the drawer out instead of dropping it instantly.
            drawerList.animate().alpha(0f).setDuration(DIAL_FADE_OUT_MS)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (!drawerOpen) {
                                drawerList.setVisibility(GONE);
                            }
                        }
                    }).start();
        } else {
            drawerList.setVisibility(GONE);
        }
        if (wasDrawer && drawerListener != null) {
            drawerListener.onDrawerClosed();
        }
        if (wasDial && dialListener != null) {
            dialListener.onDialClosed();
        }
        // Fades scrim AND quickLayer together, so a cancelled dial fades out
        // instead of vanishing abruptly.
        fadeDial(false, new Runnable() {
            @Override
            public void run() {
                mainHandler.postDelayed(resetRunnable, RESET_DELAY_MS);
            }
        });
    }

    private void fadeDial(boolean show) {
        fadeDial(show, null);
    }

    private void fadeDial(final boolean show, final Runnable onDone) {
        if (fadeAnim != null) {
            fadeAnim.cancel();
        }
        final float target = show ? 1f : 0f;
        final long duration = show ? DIAL_FADE_IN_MS : DIAL_FADE_OUT_MS;
        fadeAnim = ValueAnimator.ofFloat(scrim.getAlpha(), target).setDuration(duration);
        fadeAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                final float value = (Float) animation.getAnimatedValue();
                scrim.setAlpha(value);
                quickLayer.setAlpha(value);
            }
        });
        fadeAnim.addListener(new AnimatorListenerAdapter() {
            private boolean canceled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (canceled) return;
                if (!show && onDone != null) {
                    onDone.run();
                }
            }
        });
        fadeAnim.start();
    }

    // =====================================================================================
    // Quick-launch slots: idle / hover / select on a 270-degree arc
    // =====================================================================================

    /** Rebuilds model-dependent content after background loads. */
    private void rebuildContent() {
        reloadFavorites();
        if (drawerOpen) {
            drawerAdapter.setApps(new ArrayList<>(model.apps));
        }
    }

    private void layoutSlots() {
        quickLayer.removeAllViews();
        slots.clear();
        final int iconPx = dp(QUICK_ICON_DP);
        final int count = favorites.size();
        final float orbitR = quickRadiusPx(count);
        final float circleR = circleRadiusPx(count);
        final int circlePx = Math.round(circleR * 2f);

        // Opaque black circle covering the idle slots (only ~10% of each icon
        // peeks out). Z order: idle slots (-1) < circle (0) < title (1) <
        // hovered slot (2) < selected slot (3).
        circleBg.setZ(0f);
        quickLayer.addView(circleBg,
                new FrameLayout.LayoutParams(circlePx, circlePx));
        circleBg.setX(dialCx - circleR);
        circleBg.setY(dialCy - circleR);

        // Dial-relative title at the circle center (follows the circle).
        dialTitle.setText("");
        dialTitle.setVisibility(GONE);
        dialTitle.setAlpha(1f);
        titleHiding = false;
        dialTitle.setZ(1f);
        quickLayer.addView(dialTitle,
                new FrameLayout.LayoutParams(circlePx, circlePx));
        dialTitle.setX(dialCx - circleR);
        dialTitle.setY(dialCy - circleR);

        for (int i = 0; i < count; i++) {
            final AppItem app = favorites.get(i);
            final float angleDeg = slotAngleDeg(i, count);
            final double angleRad = Math.toRadians(angleDeg);
            final float cos = (float) Math.cos(angleRad);
            final float sin = (float) Math.sin(angleRad);
            // Idle: tucked BEHIND the circle, ~10% peeking out, rotated tangent.
            final float idleCx = dialCx + orbitR * cos;
            final float idleCy = dialCy + orbitR * sin;
            // Hover: fully outside the circle, upright, slightly enlarged.
            final float outDist = circleR + iconPx / 2f + dp(HOVER_GAP_DP);
            final float outCx = dialCx + outDist * cos;
            final float outCy = dialCy + outDist * sin;
            final ImageView icon = new ImageView(getContext());
            icon.setImageDrawable(app.icon);
            icon.setClickable(false);
            icon.setFocusable(false);
            quickLayer.addView(icon,
                    new FrameLayout.LayoutParams(iconPx, iconPx));
            icon.setX(idleCx - iconPx / 2f);
            icon.setY(idleCy - iconPx / 2f);
            icon.setRotation(angleDeg + 90f);
            icon.setZ(-1f);
            slots.add(new Slot(icon,
                    idleCx - iconPx / 2f, idleCy - iconPx / 2f, angleDeg + 90f,
                    outCx - iconPx / 2f, outCy - iconPx / 2f));
        }
    }

    /**
     * Slot angle in screen degrees (0 = right, 90 = down, clockwise).
     * Slots fill the full circle starting from the top, with no exclusion zone.
     */
    private static float slotAngleDeg(int index, int count) {
        if (count <= 1) {
            return 270f;
        }
        return ALLOWED_START_DEG + index * (ALLOWED_SWEEP_DEG / count);
    }

    private float quickRadiusPx(int count) {
        final float base = 96f + count * 6f;
        return dp(Math.min(190f, base)) * DIAL_SCALE;
    }

    /** Opaque circle radius: idle icons peek ~10% outside its edge. */
    private float circleRadiusPx(int count) {
        return quickRadiusPx(count) + dp(QUICK_ICON_DP) * (0.5f - IDLE_PEEK_FRACTION);
    }

    /** Animates a slot back to idle, tucking it behind the circle on arrival. */
    private void animateToIdle(Slot slot) {
        final long seq = ++slot.animSeq;
        slot.view.animate().cancel();
        slot.view.animate()
                .x(slot.idleX).y(slot.idleY)
                .rotation(slot.idleRotation)
                .scaleX(1f).scaleY(1f)
                .setDuration(HOVER_MS)
                .setInterpolator(new DecelerateInterpolator(1.2f))
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (slot.animSeq == seq) {
                            slot.view.setZ(-1f);
                        }
                    }
                })
                .start();
    }

    /**
     * Animates a slot to its hover pose outside the circle. Z is left alone:
     * only the launch animation may rise above the circle.
     */
    private void animateToHover(Slot slot) {
        slot.animSeq++;
        slot.view.animate().cancel();
        slot.view.animate()
                .x(slot.outX).y(slot.outY)
                .rotation(0f)
                .scaleX(HOVER_SCALE).scaleY(HOVER_SCALE)
                .setDuration(HOVER_MS)
                .setInterpolator(new DecelerateInterpolator(1.2f))
                .setListener(null)
                .start();
    }

    /** Highlights the slot nearest the blob, showing its title at the dial center. */
    private void updateHover() {
        int nearest = -1;
        float nearestDist = Float.MAX_VALUE;
        final float selectPx = dp(QUICK_SELECT_RADIUS_DP);
        final int iconHalf = dp(QUICK_ICON_DP) / 2;
        for (int i = 0; i < slots.size(); i++) {
            final Slot slot = slots.get(i);
            final float cx = slot.idleX + iconHalf;
            final float cy = slot.idleY + iconHalf;
            final float dx = pointerX - cx;
            final float dy = pointerY - cy;
            final float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = i;
            }
        }
        if (nearest < 0 || nearestDist > selectPx) {
            nearest = -1;
        }
        if (nearest == highlightedIndex) return;
        if (highlightedIndex >= 0 && highlightedIndex < slots.size()) {
            animateToIdle(slots.get(highlightedIndex));
        }
        highlightedIndex = nearest;
        if (highlightedIndex >= 0) {
            animateToHover(slots.get(highlightedIndex));
            animateBlobScale(BLOB_HOVER_SCALE);
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            showDialTitle(favorites.get(highlightedIndex).label);
        } else {
            animateBlobScale(1f);
            hideDialTitle();
        }
    }

    /** Returns the hovered slot to idle (used when the drawer takes over). */
    private void clearHover() {
        if (highlightedIndex >= 0 && highlightedIndex < slots.size()) {
            animateToIdle(slots.get(highlightedIndex));
        }
        highlightedIndex = -1;
        animateBlobScale(1f);
        hideDialTitle();
    }

    /** Tweens the ring pointer size (10% growth while hovering an app). */
    private void animateBlobScale(float scale) {
        blob.animate().cancel();
        blob.animate()
                .scaleX(scale).scaleY(scale)
                .setDuration(150L)
                .setInterpolator(new DecelerateInterpolator(1.2f))
                .setListener(null)
                .start();
    }

    /**
     * Plays the select animation (others hide, circle grows 1.5x, icon flies to
     * the center at 3x) and fires the launch ~220ms later so it stays visible.
     */
    private void startSelect(int index) {
        if (index < 0 || index >= slots.size() || index >= favorites.size()) {
            closeAll();
            return;
        }
        selecting = true;
        selectedApp = favorites.get(index);
        highlightedIndex = -1;
        hideDialTitle();
        blob.setVisibility(GONE);
        final int iconPx = dp(QUICK_ICON_DP);
        final Slot slot = slots.get(index);
        slot.animSeq++;
        slot.view.bringToFront();
        slot.view.setZ(3f);
        slot.view.animate().cancel();
        slot.view.animate()
                .x(dialCx - iconPx / 2f).y(dialCy - iconPx / 2f)
                .rotation(0f)
                .scaleX(SELECT_SCALE).scaleY(SELECT_SCALE)
                .setDuration(SELECT_MS)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .setListener(null)
                .start();
        // The black circle grows with the icon, tweened over the same duration.
        final float circleR = circleRadiusPx(favorites.size());
        circleBg.setPivotX(circleR);
        circleBg.setPivotY(circleR);
        circleBg.animate().cancel();
        circleBg.animate()
                .scaleX(SELECT_CIRCLE_SCALE).scaleY(SELECT_CIRCLE_SCALE)
                .setDuration(SELECT_MS)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .setListener(null)
                .start();
        for (int i = 0; i < slots.size(); i++) {
            if (i == index) continue;
            final ImageView other = slots.get(i).view;
            other.animate().cancel();
            other.animate().alpha(0f).setDuration(HOVER_MS).setListener(null).start();
        }
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        mainHandler.postDelayed(selectRunnable, SELECT_MS);
    }

    private void showDialTitle(String label) {
        if (label == null || label.isEmpty()) {
            hideDialTitle();
            return;
        }
        titleHiding = false;
        dialTitle.setText(label);
        if (dialTitle.getVisibility() != VISIBLE) {
            dialTitle.setVisibility(VISIBLE);
            dialTitle.setAlpha(0f);
        }
        dialTitle.animate().cancel();
        dialTitle.animate().alpha(1f).setDuration(150).setListener(null).start();
    }

    private void hideDialTitle() {
        if (dialTitle.getVisibility() != VISIBLE) return;
        titleHiding = true;
        dialTitle.animate().cancel();
        dialTitle.animate().alpha(0f).setDuration(120)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (titleHiding) {
                            dialTitle.setVisibility(GONE);
                        }
                    }
                }).start();
    }

    /**
     * Positions the blob 1:1 on the primary touch. The blob's layout position is
     * always (0, 0), so translation alone defines its center — a single code path
     * that cannot jump between branches.
     */
    private void moveBlobTo(float x, float y) {
        final float half = dp(BLOB_SIZE_DP) / 2f;
        blob.setTranslationX(x - half);
        blob.setTranslationY(y - half);
    }

    private int touchSlopPx() {
        return Math.max(1, ViewConfiguration.get(getContext()).getScaledTouchSlop());
    }

    /**
     * Clamps the dial center so the whole dial (circle + icons + margin) stays on screen.
     */
    private float[] clampCenter(float x, float y) {
        final int count = Math.max(1, favorites.size());
        final float extent = circleRadiusPx(count) + dp(QUICK_ICON_DP) / 2f + dp(CLAMP_MARGIN_DP);
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) {
            return new float[]{x, y};
        }
        final float cx = Math.max(extent, Math.min(w - extent, x));
        final float cy = Math.max(extent, Math.min(h - extent, y));
        return new float[]{cx, cy};
    }

    private float drawerTriggerPx() {
        final float byDp = dp(DRAWER_TRIGGER_DP);
        final int h = getHeight();
        if (h > 0) {
            return Math.max(byDp, h * 0.25f);
        }
        return byDp;
    }

    // =====================================================================================
    // Reset / cleanup
    // =====================================================================================

    private void resetAll() {
        drawerList.setVisibility(GONE);
        drawerList.setAlpha(1f);
        quickLayer.setVisibility(GONE);
        quickLayer.setAlpha(0f);
        quickLayer.setScaleX(1f);
        quickLayer.setScaleY(1f);
        circleBg.setScaleX(1f);
        circleBg.setScaleY(1f);
        blob.setVisibility(GONE);
        scrim.setAlpha(0f);
        dialTitle.setVisibility(GONE);
        dialTitle.setAlpha(1f);
        dialTitle.setText("");
        titleHiding = false;
        slots.clear();
        highlightedIndex = -1;
        selecting = false;
        selectedApp = null;
    }

    // =====================================================================================
    // Utils
    // =====================================================================================

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mainHandler.removeCallbacksAndMessages(null);
        if (fadeAnim != null) {
            fadeAnim.cancel();
            fadeAnim = null;
        }
    }

}
