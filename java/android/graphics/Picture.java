// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

/** Picture that records into a supersampled bitmap and replays it scaled into the destination. */
public class Picture {
    private static final int SCALE = 2;
    private static final int LIMIT = 4096;
    private Bitmap recorded;
    private int width, height;
    private boolean recording;

    public Picture() { }

    public Picture(Picture src) {
        if (src != null) { width = src.width; height = src.height; recorded = src.recorded; }
    }

    public int getWidth() { return width; }

    public int getHeight() { return height; }

    public Canvas beginRecording(int width, int height) {
        if (width < 0 || height < 0) throw new IllegalArgumentException("Size must not be negative");
        if (recording) throw new IllegalStateException("Picture is already recording");
        this.width = width;
        this.height = height;
        recording = true;
        int w = Math.max(1, Math.min(LIMIT, width * SCALE)), h = Math.max(1, Math.min(LIMIT, height * SCALE));
        recorded = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(recorded);
        canvas.scale(w / (float) Math.max(1, width), h / (float) Math.max(1, height));
        return canvas;
    }

    public void endRecording() { recording = false; }

    public void draw(Canvas canvas) {
        if (canvas == null) throw new NullPointerException("canvas");
        if (recorded == null || width <= 0 || height <= 0) return;
        Paint paint = new Paint();
        paint.setFilterBitmap(true);
        canvas.drawBitmap(recorded, null, new Rect(0, 0, width, height), paint);
    }

    public boolean requiresHardwareAcceleration() { return false; }
}
