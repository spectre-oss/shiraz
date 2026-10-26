// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;
final class DisplayFixture {
    static void check(Context context) {
        WindowManager manager = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        if (manager != context.getSystemService(WindowManager.class) || manager != context.getApplicationContext().getSystemService(Context.WINDOW_SERVICE)
                || !Context.WINDOW_SERVICE.equals(context.getSystemServiceName(WindowManager.class))) throw new AssertionError("Window service identity");
        Display display = manager.getDefaultDisplay(); DisplayMetrics expected = context.getResources().getDisplayMetrics();
        DisplayMetrics metrics = new DisplayMetrics(); display.getMetrics(metrics);
        if (display != manager.getDefaultDisplay() || display.getDisplayId() != Display.DEFAULT_DISPLAY || !display.isValid()
                || metrics.widthPixels != expected.widthPixels || metrics.heightPixels != expected.heightPixels
                || metrics.densityDpi != expected.densityDpi || metrics.scaledDensity != expected.scaledDensity
                || display.getWidth() != expected.widthPixels || display.getHeight() != expected.heightPixels || display.getRotation() != 0)
            throw new AssertionError("Logical display profile");
        metrics.widthPixels = 12345; display.getMetrics(metrics);
        if (metrics.widthPixels != expected.widthPixels) throw new AssertionError("Mutated display metrics owner");
        Point point = new Point(-1, -1); display.getSize(point);
        if (!point.equals(expected.widthPixels, expected.heightPixels)) throw new AssertionError("Logical display size");
        display.getRealSize(point); if (!point.equals(new Point(expected.widthPixels, expected.heightPixels))) throw new AssertionError("Real display size");
        Configuration initial = context.getResources().getConfiguration();
        Configuration changed = new Configuration(initial); changed.fontScale = 2; changed.densityDpi = 320;
        try {
            context.getResources().updateConfiguration(changed, null); display.getMetrics(metrics);
            if (metrics.densityDpi != 320 || metrics.scaledDensity != 4 || metrics.widthPixels != expected.widthPixels) throw new AssertionError("Display app metrics update");
            display.getRealMetrics(metrics);
            if (metrics.densityDpi != expected.densityDpi || metrics.scaledDensity != expected.scaledDensity) throw new AssertionError("App override changed base display profile");
        } finally { context.getResources().updateConfiguration(initial, null); }
        Point copied = new Point(point); copied.offset(2, 3); copied.negate();
        if (!copied.equals(-point.x - 2, -point.y - 3) || new Point(point).hashCode() != point.hashCode()) throw new AssertionError("Point value operations");
        System.out.println("SHIRAZ_FIXTURE_DISPLAY_OK");
    }
}
