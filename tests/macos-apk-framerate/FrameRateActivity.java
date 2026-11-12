// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.macosframerate;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;

/**
 * Unpaced frame-rate probe: a full-window View that redraws on every frame
 * (postInvalidateOnAnimation from onDraw) and never sleeps, so its rate is the
 * Shiraz ceiling for Canvas View apps. Each frame draws a moving 12x20 grid of
 * filled rects, 12 text lines and a circle. Every 2 s it logs
 * SHIRAZ_FRAMERATE fps=<drawn frames per second> draw_ms=<mean onDraw time>.
 */
public final class FrameRateActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(new Probe(this));
    }

    static final class Probe extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private long windowStart = -1, drawNanos;
        private int frames, total;

        Probe(Context context) {
            super(context);
            text.setColor(Color.WHITE);
            text.setTextSize(28);
        }

        @Override protected void onDraw(Canvas canvas) {
            long started = System.nanoTime();
            int w = getWidth(), h = getHeight(), shift = total % 60;
            canvas.drawColor(Color.rgb(16, 24, 40));
            float cw = w / 12f, ch = h / 20f;
            for (int row = 0; row < 20; row++) {
                for (int col = 0; col < 12; col++) {
                    fill.setColor(Color.HSVToColor(new float[] {((row * 12 + col) * 6 + total * 3) % 360, .6f, .8f}));
                    float x = col * cw + (shift * cw / 60f), y = row * ch;
                    canvas.drawRect(x + 2, y + 2, x + cw - 2, y + ch - 2, fill);
                }
            }
            for (int line = 0; line < 12; line++)
                canvas.drawText("Frame " + total + " line " + line, 24, 60 + line * 40, text);
            fill.setColor(Color.WHITE);
            canvas.drawCircle(w / 2f + (float) Math.sin(total / 20.0) * w / 3, h * .75f, 40, fill);
            total++;
            frames++;
            drawNanos += System.nanoTime() - started;
            long now = SystemClock.uptimeMillis();
            if (windowStart < 0) windowStart = now;
            if (now - windowStart >= 2000) {
                System.out.println("SHIRAZ_FRAMERATE fps=" + (frames * 1000f / (now - windowStart))
                        + " draw_ms=" + (drawNanos / 1e6f / frames) + " frames=" + total);
                windowStart = now;
                frames = 0;
                drawNanos = 0;
            }
            postInvalidateOnAnimation();
        }
    }
}
