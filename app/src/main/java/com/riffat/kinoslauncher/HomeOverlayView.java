package com.riffat.kinoslauncher;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

/**
 * Scrim di belakang home UI: dim translusen (bukan hitam pekat) + gradient
 * vertikal halus agar teks drawer tetap terbaca di atas wallpaper ramai.
 *
 * <p>Blur wallpaper asli ditangani lewat window blur (API 31+, lihat
 * HomeActivity.setWallpaperBlur) agar hemat GPU di low-end. Di API 24-30
 * view ini menjadi fallback dim-only.</p>
 */
public class HomeOverlayView extends View {

    private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int scrimColor;
    private int scrimNight;
    // Cache agar tidak alokasi LinearGradient tiap frame (hemat Helio G35).
    private LinearGradient cachedGradient;
    private int cachedH = -1;
    private int cachedBase = 0;

    public HomeOverlayView(Context context) {
        this(context, null);
    }

    public HomeOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(false);
        setClickable(false);
        scrimColor = ContextCompat.getColor(context, R.color.kinos_scrim);
        scrimNight = ContextCompat.getColor(context, R.color.kinos_scrim_night);
        setBackgroundColor(currentScrim());
    }

    private int currentScrim() {
        final int night = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES ? scrimNight : scrimColor;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setBackgroundColor(currentScrim());
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Vignette vertikal sangat halus: atas/bawah 12% lebih gelap,
        // tengah mengikuti scrim. Mengurangi silau tanpa membutakan.
        final int h = getHeight();
        if (h <= 0) return;
        final int base = currentScrim();
        if (cachedGradient == null || cachedH != h || cachedBase != base) {
            final int edge = (base & 0x00FFFFFF) | 0x55000000;
            cachedGradient = new LinearGradient(
                    0, 0, 0, h, edge, base, Shader.TileMode.CLAMP);
            cachedH = h;
            cachedBase = base;
        }
        gradientPaint.setShader(cachedGradient);
        canvas.drawRect(0, 0, getWidth(), h, gradientPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Never consume: let the parent KinOSDialSelectorView handle everything.
        return false;
    }
}
