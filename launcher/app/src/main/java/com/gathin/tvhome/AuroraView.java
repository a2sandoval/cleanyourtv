package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * Aurora borealis backdrop for the Clock screensaver: three translucent curtains
 * whose top edges sway on slow sine waves, over a fixed star field. Deliberately
 * cheap -- no bitmaps, no blur, no per-frame allocation: the gradients are built
 * once per size change, the Path is reused, redraws are capped at ~25 fps, and
 * the animation only runs while the dream is on screen.
 */
public class AuroraView extends View {

    private static final long FRAME_MS = 40L;
    /** Curtain colours (green, teal, violet); index 0 is drawn in front. */
    private static final int[] CURTAIN_RGB = {0x2FE08A, 0x00BFA6, 0x7A4FE0};
    private static final float[] CURTAIN_ALPHA = {0.34f, 0.26f, 0.20f};
    private static final int STAR_COUNT = 70;
    private static final int STEPS = 24;

    private final Paint curtainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearGradient[] gradients;
    private float[] starX;
    private float[] starY;
    private float[] starR;
    private float[] starA;
    private long startMs;
    private boolean running;

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            invalidate();
            handler.postDelayed(this, FRAME_MS);
        }
    };

    public AuroraView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void start() {
        if (running) return;
        running = true;
        startMs = SystemClock.uptimeMillis();
        handler.post(frame);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(frame);
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w == 0 || h == 0) return;
        gradients = new LinearGradient[CURTAIN_RGB.length];
        for (int i = 0; i < CURTAIN_RGB.length; i++) {
            int topAlpha = (int) (CURTAIN_ALPHA[i] * 255);
            gradients[i] = new LinearGradient(0, 0, 0, h * 0.85f,
                    new int[]{(topAlpha << 24) | CURTAIN_RGB[i],
                            ((topAlpha / 3) << 24) | CURTAIN_RGB[i],
                            CURTAIN_RGB[i] & 0x00FFFFFF},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        }
        float density = getResources().getDisplayMetrics().density;
        Random random = new Random(20270820L);
        starX = new float[STAR_COUNT];
        starY = new float[STAR_COUNT];
        starR = new float[STAR_COUNT];
        starA = new float[STAR_COUNT];
        for (int i = 0; i < STAR_COUNT; i++) {
            starX[i] = w * random.nextFloat();
            starY[i] = h * 0.7f * random.nextFloat();
            starR[i] = (0.8f + random.nextFloat() * 1.4f) * density * 0.5f;
            starA[i] = 0.25f + random.nextFloat() * 0.55f;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0 || gradients == null) return;
        if (starX != null) {
            for (int i = 0; i < STAR_COUNT; i++) {
                starPaint.setColor(((int) (starA[i] * 255) << 24) | 0x00FFFFFF);
                canvas.drawCircle(starX[i], starY[i], starR[i], starPaint);
            }
        }
        float t = running ? (SystemClock.uptimeMillis() - startMs) / 1000f : 0f;
        for (int i = CURTAIN_RGB.length - 1; i >= 0; i--) {
            drawCurtain(canvas, w, h, i, t);
        }
    }

    private void drawCurtain(Canvas canvas, int w, int h, int i, float t) {
        float baseY = h * (0.04f + 0.10f * i);
        float depth = h * (0.55f + 0.08f * i);
        float amp1 = h * 0.045f;
        float amp2 = h * 0.028f;
        double phase = i * 1.7;
        float k1 = (float) (2 * Math.PI * 2.0 / w);
        float k2 = (float) (2 * Math.PI * 3.7 / w);
        float x0 = -w * 0.05f;
        float x1 = w * 1.05f;
        path.reset();
        for (int s = 0; s <= STEPS; s++) {
            float x = x0 + (x1 - x0) * s / STEPS;
            float y = baseY
                    + (float) Math.sin(x * k1 + t * 0.10 + phase) * amp1
                    + (float) Math.sin(x * k2 - t * 0.063 + phase * 1.3) * amp2;
            if (s == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.lineTo(x1, baseY + depth);
        path.lineTo(x0, baseY + depth);
        path.close();
        curtainPaint.setShader(gradients[i]);
        canvas.drawPath(path, curtainPaint);
        curtainPaint.setShader(null);
    }
}
