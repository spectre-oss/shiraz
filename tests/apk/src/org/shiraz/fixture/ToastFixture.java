// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.SystemClock;
import android.widget.Toast;
final class ToastFixture {
    private final String mode = System.getenv("SHIRAZ_FIXTURE_TOAST_MODE");
    private final Toast toast;
    private long shown;
    private boolean canceled, updated;
    ToastFixture(Context context) {
        Toast empty = new Toast(context);
        if (empty.getView() != null) throw new AssertionError("New toast has content");
        try { empty.show(); throw new AssertionError("Empty toast shown"); } catch (RuntimeException expected) { }
        toast = Toast.makeText(context, "Hi", Toast.LENGTH_SHORT);
        if (toast.getDuration() != Toast.LENGTH_SHORT || toast.getView() == null) throw new AssertionError("Text toast state");
        toast.setText("end".equals(mode) ? "End" : "Hi");
        Bitmap bitmap = Bitmap.createBitmap(96, 160, Bitmap.Config.ARGB_8888);
        try {
            toast.getView().layout(0, 0, 96, 160); toast.getView().draw(new Canvas(bitmap));
            int white = 0;
            for (int y = 0; y < 160; ++y) for (int x = 0; x < 96; ++x) if ((bitmap.getPixel(x, y) & 0xffffff) == 0xffffff) ++white;
            if (white == 0) throw new AssertionError("Toast text was not rendered");
        } finally { bitmap.recycle(); }
    }
    void start() {
        if (mode == null) return;
        shown = SystemClock.uptimeMillis(); toast.show();
        if ("queue".equals(mode)) {
            Toast skipped = Toast.makeText(toast.getView().getContext(), "Next", Toast.LENGTH_SHORT);
            skipped.show(); skipped.cancel();
            Toast.makeText(toast.getView().getContext(), "End", Toast.LENGTH_SHORT).show();
        }
        if ("update".equals(mode)) new Handler().postDelayed(new Runnable() { @Override public void run() { toast.setText("End"); updated = true; } }, 100);
        if ("cancel".equals(mode)) new Handler().postDelayed(new Runnable() { @Override public void run() { toast.cancel(); canceled = true; } }, 100);
    }
    void verify() {
        if ("update".equals(mode) && !updated) throw new AssertionError("Live toast text update missing");
        if ("cancel".equals(mode) && !canceled) throw new AssertionError("Toast cancellation callback missing");
        if (("expire".equals(mode) || "queue".equals(mode)) && SystemClock.uptimeMillis() - shown < 2000) throw new AssertionError("Expiry was not tested for its real duration");
        System.out.println("SHIRAZ_FIXTURE_TOAST_OK");
    }
}
